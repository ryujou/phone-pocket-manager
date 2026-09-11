package local.phonemanager
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class RosterTest {
    @Test fun classesCanReusePocket() { RosterRules.validate(listOf(Student("A","0001","张三","01"),Student("B","0001","李四","01"))) }
    @Test(expected=IllegalArgumentException::class) fun rejectConflict() { RosterRules.validate(listOf(Student("A","0001","张三","01"),Student("A","1001","李四","01"))) }
    @Test(expected=IllegalArgumentException::class) fun rejectDuplicateId() { RosterRules.validate(listOf(Student("A","0001","张三","01"),Student("A","0001","李四","02"))) }
    @Test fun transfersKeepOriginalAndDoNotDisplaceNative() {
        val result=RosterRules.currentClass(listOf(Student("B","0020","转入","20"),Student("A","1020","本班","20")),"A")
        assertNull(result.first { it.sid=="0020" }.pocket); assertEquals("B",result.first { it.sid=="0020" }.source)
        assertEquals("20",result.first { it.sid=="1020" }.pocket)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectPocket55() { RosterRules.validate(listOf(Student("A","0055","张三","55"))) }
    @Test fun preserveTextAndBlankPocketThroughExcel() {
        val bytes=Xlsx.write(linkedMapOf("名单" to listOf(listOf("学号/工号","姓名","班级","袋号"),listOf("0001","=张三&小明","A",""))))
        val row=Xlsx.read(ByteArrayInputStream(bytes)).single()
        assertEquals("0001",row.sid); assertNull(row.pocket); assertEquals("=张三&小明",row.name)
    }
    @Test fun importMultipleSheets() {
        val header=listOf("学号/工号","姓名","班级")
        val bytes=Xlsx.write(linkedMapOf("一" to listOf(header,listOf("0001","甲","A")),"二" to listOf(header,listOf("1002","乙","B"))))
        val rows=Xlsx.read(ByteArrayInputStream(bytes)); assertEquals(2,rows.size); assertEquals(listOf("01","02"),rows.map { it.pocket })
    }
    @Test fun mockRosterIsValid() {
        val raw=javaClass.classLoader!!.getResourceAsStream("rosters.json")!!.bufferedReader().readText()
        val rows=org.json.JSONArray(raw).objects().map(Student::from)
        RosterRules.validate(rows); assertEquals(12,rows.size)
        assertEquals(rows,rows.map { Student.from(it.json()) })
    }
}
