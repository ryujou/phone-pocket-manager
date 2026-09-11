package local.phonemanager

import org.json.JSONArray
import org.json.JSONObject

val STATES = listOf("已交", "未交", "待复核", "请假", "缺勤", "免交")
fun JSONObject.text(key: String, default: String = "") = if (isNull(key)) default else optString(key, default)
fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
data class Student(val cls: String, val sid: String, val name: String, val pocket: String?, val source: String = cls) {
    fun json() = JSONObject().put("class_name",cls).put("sid",sid).put("name",name).put("pocket",pocket ?: JSONObject.NULL).put("source_class",source)
    companion object { fun from(j: JSONObject) = Student(j.text("class_name"),j.text("sid"),j.text("name"),j.text("pocket").ifBlank { null },j.text("source_class",j.text("class_name"))) }
}
data class Record(val student: Student, val prediction: String, val score: Double, var state: String, var note: String = "") {
    fun json() = student.json().put("prediction",prediction).put("score",score).put("state",state).put("note",note)
    companion object { fun from(j: JSONObject) = Record(Student.from(j),j.text("prediction","review"),j.optDouble("score",0.0),j.text("state","待复核"),j.text("note")) }
}
data class Lesson(val id: Long, val term: String, val day: String, val title: String, val photo: String, val calibration: String)
object RosterRules {
    fun validate(rows: List<Student>) {
        require(rows.isNotEmpty()) { "没有有效学生" }
        rows.forEach { r ->
            require(r.cls.isNotBlank() && r.name.isNotBlank() && r.sid.matches(Regex("[0-9]{2,}"))) { "班级、姓名不能为空，学号须为至少两位数字" }
            require(r.pocket == null || (r.pocket.matches(Regex("[0-9]{2}")) && r.pocket.toInt() in 1..54)) { "袋号须为01～54，或留空待分配" }
        }
        require(rows.distinctBy { it.cls to it.sid }.size == rows.size) { "同班学号重复" }
        val assigned = rows.filter { it.pocket != null }
        require(assigned.distinctBy { it.cls to it.pocket }.size == assigned.size) { "同班袋号冲突，请修改袋号或留空" }
    }
    fun currentClass(rows: List<Student>, target: String): List<Student> {
        require(target.isNotBlank()) { "请输入当前班级" }
        val used = mutableSetOf<String>()
        return rows.sortedBy { it.cls != target }.map { r -> r.copy(cls=target, pocket=r.pocket?.takeIf { used.add(it) }) }.also { validate(it) }
    }
}
