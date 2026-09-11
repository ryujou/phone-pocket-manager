package local.phonemanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SystemTest {
    @Test fun duplicateSaveCorrectionSnapshotAndIsolation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test-${System.nanoTime()}.sqlite3"; val db=Repository(context,name)
        try {
            val student=Student("测试班","0001","甲","01")
            db.importRows("demo",listOf(student)); db.importRows("official",listOf(student))
            val row=Record(student,"empty",.9,"未交")
            val id=db.save("demo","测试班","测试学期","2026-09-10","1","","{}",listOf(row))
            assertEquals(id,db.save("demo","测试班","测试学期","2026-09-10","1","","{}",listOf(row)))
            assertEquals(1,db.records(id).size); assertTrue(db.lessons("official","测试班").isEmpty())
            db.importRows("demo",listOf(student.copy(pocket="02",name="甲新")))
            db.correct(id,row.copy(state="已交",note="补交")); val saved=db.records(id).single()
            assertEquals("01",saved.student.pocket); assertEquals("甲",saved.student.name); assertEquals("empty",saved.prediction); assertEquals("已交",saved.state)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun seedAndLocalPhotoRecognition() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="seed-test-${System.nanoTime()}.sqlite3"; val db=Repository(context,name)
        try {
            db.seed(context); db.seed(context); assertEquals(2,db.classes("demo").size)
            assertEquals(12,db.classes("demo").sumOf { db.students("demo",it).size })
            assertTrue(db.classes("official").all { db.lessons("official",it).isEmpty() })
            val f=File(context.cacheDir,"recognizer-test.jpg")
            context.assets.open("示例照片.jpg").use { input -> f.outputStream().use { input.copyTo(it) } }
            val bitmap=Recognizer.load(f); val result=Recognizer.recognize(bitmap)
            assertEquals(54,result.pockets.size); assertEquals("empty",result.pockets[7].prediction)
            assertEquals("phone",result.pockets[0].prediction); bitmap.recycle(); result.bitmap.recycle(); f.delete()
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
