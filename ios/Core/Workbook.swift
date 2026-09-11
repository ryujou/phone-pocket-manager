import Foundation
struct Workbook {
    static func students(_ data: Data) throws -> [Student] {
        let files = try ZipContainer.read(data)
        let shared = SheetXML()
        if let bytes = files["xl/sharedStrings.xml"] { try shared.parse(bytes) }
        let book = SheetXML(), rels = SheetXML()
        guard let b = files["xl/workbook.xml"], let r = files["xl/_rels/workbook.xml.rels"] else { throw PocketError.message("缺少 Excel 工作簿") }
        try book.parse(b); try rels.parse(r)
        var result: [Student] = []
        for id in book.sheetIDs {
            guard let target = rels.links[id] else { continue }
            let path = target.hasPrefix("/") ? String(target.dropFirst()) : "xl/" + target
            guard let bytes = files[path] else { continue }
            let sheet = SheetXML(); sheet.strings = shared.strings; try sheet.parse(bytes)
            guard let headerIndex = sheet.rows.firstIndex(where:{ $0.contains("姓名") && ($0.contains("学号/工号") || $0.contains("学号")) && $0.contains("班级") }) else { continue }
            let header = sheet.rows[headerIndex]
            for row in sheet.rows.dropFirst(headerIndex+1) where row.contains(where:{ !$0.isEmpty }) {
                func value(_ keys: [String]) -> String { for key in keys { if let i = header.firstIndex(of:key), i < row.count { return row[i].trimmingCharacters(in:.whitespacesAndNewlines) } }; return "" }
                let sid = value(["学号/工号","学号"]), cls = value(["班级"])
                let raw = header.contains("袋号") ? value(["袋号"]) : String(sid.suffix(2))
                let pocket = raw.isEmpty ? "" : (Int(raw).map { String(format:"%02d",$0) } ?? raw)
                result.append(Student(sid:sid,name:value(["姓名"]),className:cls,pocket:pocket,sourceClass:cls))
            }
        }
        var check = Archive(); try check.importStudents(result)
        return check.students
    }
    static func export(students: [Student], lessons: [Lesson]) -> Data {
        var people = Dictionary(uniqueKeysWithValues: students.map { ($0.id,$0) })
        for l in lessons { for r in l.records { people[l.className+"|"+r.sid] = Student(sid:r.sid,name:r.name,className:l.className,pocket:r.pocket,sourceClass:l.className) } }
        let summary = [["班级","学号","姓名"]+SubmissionState.allCases.map { $0.rawValue+"次数" }] + people.values.sorted { $0.id < $1.id }.map { p in
            let rows = lessons.filter { $0.className == p.className }.flatMap(\.records).filter { $0.sid == p.sid }
            return [p.className,p.sid,p.name]+SubmissionState.allCases.map { state in String(rows.filter { $0.state == state }.count) }
        }
        let detail = [["班级","学期","日期","课次","袋号","学号","姓名","最终状态","备注"]] + lessons.sorted { $0.day < $1.day }.flatMap { l in l.records.map { [l.className,l.term,l.day,l.period,$0.pocket,$0.sid,$0.name,$0.state.rawValue,$0.note] } }
        return create([("汇总",summary),("明细",detail)])
    }
    static func create(_ sheets: [(String,[[String]])]) -> Data {
        func esc(_ s: String) -> String { s.filter { $0 == "\n" || $0 == "\t" || $0.unicodeScalars.allSatisfy { $0.value >= 32 } }.replacingOccurrences(of:"&",with:"&amp;").replacingOccurrences(of:"<",with:"&lt;").replacingOccurrences(of:">",with:"&gt;").replacingOccurrences(of:"\"",with:"&quot;") }
        var files: [(String,Data)] = []
        func add(_ path: String,_ body: String) { files.append((path,Data(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+body).utf8))) }
        add("[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"+sheets.indices.map { "<Override PartName=\"/xl/worksheets/sheet\($0+1).xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }.joined()+"</Types>")
        add("_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
        add("xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"+sheets.enumerated().map { "<sheet name=\"\(esc($0.element.0))\" sheetId=\"\($0.offset+1)\" r:id=\"rId\($0.offset+1)\"/>" }.joined()+"</sheets></workbook>")
        add("xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+sheets.indices.map { "<Relationship Id=\"rId\($0+1)\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet\($0+1).xml\"/>" }.joined()+"</Relationships>")
        for (i,sheet) in sheets.enumerated() {
            let rows = sheet.1.enumerated().map { index,row in "<row r=\"\(index+1)\">"+row.enumerated().map { col,value in
                var n=col+1, ref=""; while n>0 { n-=1; ref=String(UnicodeScalar(65+n%26)!)+ref; n/=26 }
                return "<c r=\"\(ref)\(index+1)\" t=\"inlineStr\"><is><t xml:space=\"preserve\">\(esc(value))</t></is></c>"
            }.joined()+"</row>" }.joined()
            add("xl/worksheets/sheet\(i+1).xml","<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>\(rows)</sheetData></worksheet>")
        }
        return ZipContainer.write(files)
    }
}
