package local.phonemanager

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class Repository(context: Context, dbName: String = "phone-manager.sqlite3") : SQLiteOpenHelper(context, dbName, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE students(space TEXT,cls TEXT,sid TEXT,name TEXT,pocket TEXT,source TEXT,PRIMARY KEY(space,cls,sid),UNIQUE(space,cls,pocket))")
        db.execSQL("CREATE TABLE lessons(id INTEGER PRIMARY KEY,space TEXT,cls TEXT,term TEXT,day TEXT,title TEXT,photo TEXT,calibration TEXT,UNIQUE(space,cls,term,day,title))")
        db.execSQL("CREATE TABLE records(lesson INTEGER,sid TEXT,payload TEXT,PRIMARY KEY(lesson,sid))")
        db.execSQL("CREATE TABLE audit(id INTEGER PRIMARY KEY,time TEXT DEFAULT CURRENT_TIMESTAMP,lesson INTEGER,sid TEXT,before_value TEXT,after_value TEXT)")
        db.execSQL("CREATE TABLE config(key TEXT PRIMARY KEY,value TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun seed(context: Context) {
        readableDatabase.rawQuery("SELECT 1 FROM config WHERE key='seed1'",null).use { if (it.moveToFirst()) return }
        val rows = JSONArray(context.assets.open("rosters.json").bufferedReader().readText()).objects().map(Student::from)
        transaction { importRows("demo",rows); importRows("official",rows); execSQL("INSERT INTO config VALUES('seed1','1')") }
    }
    fun <T> transaction(block: SQLiteDatabase.() -> T): T {
        val db = writableDatabase; db.beginTransaction()
        return try { val value = db.block(); db.setTransactionSuccessful(); value } finally { db.endTransaction() }
    }
    fun classes(space: String): List<String> = readableDatabase.rawQuery("SELECT DISTINCT cls FROM students WHERE space=? ORDER BY cls",arrayOf(space)).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
    fun students(space: String, cls: String): List<Student> = readableDatabase.rawQuery("SELECT sid,name,pocket,source FROM students WHERE space=? AND cls=? ORDER BY pocket IS NULL,pocket,sid",arrayOf(space,cls)).use { c -> buildList { while(c.moveToNext()) add(Student(cls,c.getString(0),c.getString(1),c.getString(2),c.getString(3))) } }
    fun importRows(space: String, rows: List<Student>) {
        RosterRules.validate(rows)
        val merged = rows.groupBy { it.cls }.flatMap { (cls,new) -> (students(space,cls).associateBy { it.sid } + new.associateBy { it.sid }).values }
        RosterRules.validate(merged)
        transaction {
            rows.map { it.cls }.distinct().forEach { execSQL("DELETE FROM students WHERE space=? AND cls=?",arrayOf<Any?>(space,it)) }
            merged.forEach { execSQL("INSERT INTO students VALUES(?,?,?,?,?,?)",arrayOf<Any?>(space,it.cls,it.sid,it.name,it.pocket,it.source)) }
        }
    }
    fun lessons(space: String, cls: String) = readableDatabase.rawQuery("SELECT id,term,day,title,photo,calibration FROM lessons WHERE space=? AND cls=? ORDER BY day DESC,id DESC",arrayOf(space,cls)).use { c -> buildList { while(c.moveToNext()) add(Lesson(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5))) } }
    fun records(id: Long) = readableDatabase.rawQuery("SELECT payload FROM records WHERE lesson=? ORDER BY sid",arrayOf(id.toString())).use { c -> buildList { while(c.moveToNext()) add(Record.from(JSONObject(c.getString(0)))) } }
    fun save(space: String, cls: String, term: String, day: String, title: String, photo: String, calibration: String, input: List<Record>): Long {
        require(term.isNotBlank() && title.isNotBlank()) { "请填写学期和课次" }; java.time.LocalDate.parse(day)
        require(input.isNotEmpty() && input.distinctBy { it.student.sid }.size == input.size) { "记录为空或学号重复" }
        require(input.all { it.state in STATES && it.student.cls == cls }) { "记录状态或班级无效" }
        return transaction {
            execSQL("INSERT OR IGNORE INTO lessons(space,cls,term,day,title,photo,calibration) VALUES(?,?,?,?,?,?,?)",arrayOf<Any?>(space,cls,term.trim(),day,title.trim(),photo,calibration))
            val id = rawQuery("SELECT id FROM lessons WHERE space=? AND cls=? AND term=? AND day=? AND title=?",arrayOf(space,cls,term.trim(),day,title.trim())).use { it.moveToFirst(); it.getLong(0) }
            val old = records(id).associateBy { it.student.sid }
            require(old.isEmpty() || old.keys == input.map { it.student.sid }.toSet()) { "此课次已有名单快照，请从历史记录更正，或使用新的课次名称" }
            require(old.isEmpty() || input.all { old[it.student.sid]?.student?.pocket == it.student.pocket }) { "本班袋号分配已变化，请到历史记录更正旧课次，或使用新的课次名称" }
            execSQL("UPDATE lessons SET photo=?,calibration=? WHERE id=?",arrayOf<Any?>(photo,calibration,id))
            input.forEach { incoming ->
                val prior = old[incoming.student.sid]
                val r = incoming.copy(student=prior?.student ?: incoming.student)
                execSQL("INSERT INTO audit(lesson,sid,before_value,after_value) VALUES(?,?,?,?)",arrayOf<Any?>(id,r.student.sid,prior?.json()?.toString(),r.json().toString()))
                execSQL("INSERT OR REPLACE INTO records VALUES(?,?,?)",arrayOf<Any?>(id,r.student.sid,r.json().toString()))
            }; id
        }
    }
    fun correct(id: Long, updated: Record) = transaction {
        require(updated.state in STATES)
        val prior = records(id).first { it.student.sid == updated.student.sid }
        val result = prior.copy(state=updated.state,note=updated.note)
        execSQL("UPDATE records SET payload=? WHERE lesson=? AND sid=?",arrayOf<Any?>(result.json().toString(),id,result.student.sid))
        execSQL("INSERT INTO audit(lesson,sid,before_value,after_value) VALUES(?,?,?,?)",arrayOf<Any?>(id,result.student.sid,prior.json().toString(),result.json().toString()))
    }
    fun export(space: String, cls: String, term: String, start: String, end: String): ByteArray {
        java.time.LocalDate.parse(start); java.time.LocalDate.parse(end); require(start<=end) { "开始日期不能晚于结束日期" }
        val selected = lessons(space,cls).filter { (term.isBlank() || it.term == term) && it.day >= start && it.day <= end }
        val detail = selected.flatMap { l -> records(l.id).map { l to it } }
        val people = (students(space,cls) + detail.map { it.second.student }).associateBy { it.sid }.values.sortedBy { it.sid }
        val sums = listOf(listOf("班级","学号","姓名") + STATES.map { if(it=="未交") "确认未交次数" else "${it}次数" }) + people.map { p -> listOf(cls,p.sid,p.name) + STATES.map { s -> detail.count { it.second.student.sid==p.sid && it.second.state==s }.toString() } }
        val rows = listOf(listOf("班级","学期","日期","课次","袋号","学号","姓名","最终状态","备注")) + detail.map { (l,r) -> listOf(cls,l.term,l.day,l.title,r.student.pocket ?: "待分配",r.student.sid,r.student.name,r.state,r.note) }
        return Xlsx.write(linkedMapOf("汇总" to sums,"明细" to rows))
    }
}
