package local.phonemanager

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

/** Small OOXML reader/writer for text rosters and non-formula statistics. */
object Xlsx {
    private fun parser(xml: String): XmlPullParser {
        require(!xml.contains("<!DOCTYPE",true) && !xml.contains("<!ENTITY",true)) { "不支持含外部实体的工作簿" }
        return XmlPullParserFactory.newInstance().newPullParser().apply { setInput(xml.reader()) }
    }
    fun read(input: InputStream): List<Student> {
        val files=mutableMapOf<String,String>(); var total=0
        ZipInputStream(input).use { z ->
            var e=z.nextEntry
            while(e!=null) {
                if(e.name.startsWith("xl/") && e.name.endsWith(".xml")) {
                    val out=ByteArrayOutputStream(); val buffer=ByteArray(8192)
                    while(true) { val n=z.read(buffer); if(n<0) break; total+=n; require(total<32_000_000) { "Excel过大，请拆分名单后导入" }; out.write(buffer,0,n) }
                    files[e.name]=out.toString("UTF-8")
                }; e=z.nextEntry
            }
        }
        val strings=mutableListOf<String>()
        files["xl/sharedStrings.xml"]?.let { xml ->
            val p=parser(xml); var current=StringBuilder(); var inside=false
            while(p.eventType!=XmlPullParser.END_DOCUMENT) {
                if(p.eventType==XmlPullParser.START_TAG && p.name=="si") { current=StringBuilder(); inside=true }
                if(p.eventType==XmlPullParser.START_TAG && p.name=="t" && inside) current.append(p.nextText())
                if(p.eventType==XmlPullParser.END_TAG && p.name=="si") { strings.add(current.toString()); inside=false }
                p.next()
            }
        }
        val result=mutableListOf<Student>()
        files.filterKeys { it.matches(Regex("xl/worksheets/sheet[0-9]+\\.xml")) }.toSortedMap().values.forEach { xml ->
            val p=parser(xml); val rows=mutableListOf<List<String>>(); var row=mutableMapOf<Int,String>(); var col=0; var type=""; var value=""
            while(p.eventType!=XmlPullParser.END_DOCUMENT) {
                if(p.eventType==XmlPullParser.START_TAG) when(p.name) {
                    "row" -> row=mutableMapOf()
                    "c" -> { val ref=p.getAttributeValue(null,"r") ?: "A1"; col=0; ref.takeWhile { it.isLetter() }.forEach { col=col*26+(it-'A'+1) }; col--; type=p.getAttributeValue(null,"t") ?: ""; value="" }
                    "v","t" -> value+=p.nextText()
                }
                if(p.eventType==XmlPullParser.END_TAG) when(p.name) {
                    "c" -> row[col]=if(type=="s") strings.getOrElse(value.toIntOrNull() ?: -1) { "" } else value
                    "row" -> rows.add(List((row.keys.maxOrNull() ?: -1)+1) { row[it] ?: "" })
                }; p.next()
            }
            val header=rows.firstOrNull()?.map { it.trim() } ?: emptyList()
            val sidCol=header.indexOfFirst { it=="学号/工号" || it=="学号" }
            if(sidCol<0 || !header.containsAll(listOf("姓名","班级"))) return@forEach
            rows.drop(1).filter { r -> r.any { it.isNotBlank() } }.forEach { r ->
                fun get(i: Int) = r.getOrElse(i) { "" }.trim()
                val sid=get(sidCol); val cls=get(header.indexOf("班级")); val name=get(header.indexOf("姓名"))
                val raw=if("袋号" in header) get(header.indexOf("袋号")) else sid.takeLast(2)
                val pocket=if(raw.isBlank() || raw=="待分配") null else raw.toIntOrNull()?.let { "%02d".format(it) } ?: error("袋号无效：$raw")
                result.add(Student(cls,sid,name,pocket))
            }
        }
        RosterRules.validate(result); return result
    }
    private fun escape(s: String) = s.filter { it >= ' ' || it in "\n\r\t" }.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    private fun col(n: Int): String = if(n<26) ('A'+n).toString() else col(n/26-1)+('A'+n%26)
    fun write(sheets: LinkedHashMap<String,List<List<String>>>): ByteArray {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, content: String) { z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray()); z.closeEntry() }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>${sheets.keys.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }}</Types>""")
            put("_rels/.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${sheets.keys.mapIndexed { i,name -> "<sheet name=\"${escape(name)}\" sheetId=\"${i+1}\" r:id=\"rId${i+1}\"/>" }.joinToString("")}</sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${sheets.keys.indices.joinToString("") { "<Relationship Id=\"rId${it+1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it+1}.xml\"/>" }}</Relationships>""")
            sheets.values.forEachIndexed { index, rows ->
                put("xl/worksheets/sheet${index+1}.xml", """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" state="frozen"/></sheetView></sheetViews><cols><col min="1" max="20" width="20" customWidth="1"/></cols><sheetData>${rows.mapIndexed { r, cells -> "<row r=\"${r+1}\">"+cells.mapIndexed { c,v -> if(r>0 && rows.firstOrNull()?.getOrNull(c)?.endsWith("次数")==true) "<c r=\"${col(c)}${r+1}\"><v>${v.toIntOrNull() ?: 0}</v></c>" else "<c r=\"${col(c)}${r+1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(v)}</t></is></c>" }.joinToString("")+"</row>" }.joinToString("")}</sheetData></worksheet>""")
            }
        }; return out.toByteArray()
    }
}
