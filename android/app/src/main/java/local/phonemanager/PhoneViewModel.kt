package local.phonemanager

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.core.Point
import java.io.File
import java.time.LocalDate

data class PhotoState(val loading: Boolean=false,val analysis: Analysis?=null,val error: String="")
class PhoneViewModel(app: Application): AndroidViewModel(app) {
    val repo=Repository(app).also { it.seed(app) }
    val prefs=app.getSharedPreferences("ui",0)
    var space=prefs.getString("space","demo")!!
    var cls=prefs.getString("class",null) ?: repo.classes(space).first()
    var term=prefs.getString("term","2026—2027 学年上学期")!!
    var day=LocalDate.now().toString()
    var lesson=prefs.getString("lesson","第1节")!!
    var photo=""
    var page=0
    var records=mutableListOf<Record>()
    private var serial=0
    private val _state=MutableStateFlow(PhotoState())
    val state=_state.asStateFlow()
    init {
        if(cls !in repo.classes(space)) cls=repo.classes(space).first()
        runCatching {
            val j=JSONObject(prefs.getString("draft","{}")!!)
            if(j.text("space")==space && j.text("class")==cls && File(j.text("photo")).exists()) {
                photo=j.getString("photo"); day=j.text("day",day); term=j.text("term",term); lesson=j.text("lesson",lesson)
                records=j.getJSONArray("records").objects().map(Record::from).toMutableList()
                val warp=android.graphics.BitmapFactory.decodeFile(File(getApplication<Application>().filesDir,"draft-warp.jpg").path)
                if(warp!=null && j.has("analysis")) _state.value=PhotoState(analysis=Analysis.restore(warp,j.getJSONObject("analysis")))
            }
        }
    }
    fun persist() {
        val j=JSONObject().put("space",space).put("class",cls).put("photo",photo).put("day",day).put("term",term).put("lesson",lesson).put("records",JSONArray(records.map { it.json() }))
        _state.value.analysis?.let { j.put("analysis",it.metadata()) }
        prefs.edit().putString("space",space).putString("class",cls).putString("term",term).putString("lesson",lesson).putString("draft",j.toString()).apply()
    }
    fun switch(space: String, cls: String) {
        serial++; this.space=space; this.cls=cls; photo=""; records.clear(); _state.value=PhotoState(); persist()
    }
    fun analyze(path: String=photo,corners: List<Point>?=null,rows: List<Int>?=null,cols: List<Int>?=null) {
        photo=path; val token=++serial; _state.value=PhotoState(loading=true)
        viewModelScope.launch {
            try {
                val result=withContext(Dispatchers.Default) { val bitmap=Recognizer.load(File(path)); try { Recognizer.recognize(bitmap,corners,rows,cols) } finally { bitmap.recycle() } }
                if(token!=serial) return@launch
                val predictions=result.pockets.associateBy { it.number }
                records=repo.students(space,cls).map { s -> val p=predictions[s.pocket]; Record(s,p?.prediction ?: "review",p?.score ?: 0.0,when(p?.prediction){ "phone"->"已交";"empty"->"未交";else->"待复核" }) }.toMutableList()
                withContext(Dispatchers.IO) { File(getApplication<Application>().filesDir,"draft-warp.jpg").outputStream().use { result.bitmap.compress(Bitmap.CompressFormat.JPEG,95,it) } }
                _state.value=PhotoState(analysis=result); persist()
            } catch(e: Exception) {
                if(token==serial) { records=repo.students(space,cls).map { Record(it,"review",0.0,"待复核") }.toMutableList(); _state.value=PhotoState(error=e.message ?: "无法识别，请手动核对"); persist() }
            }
        }
    }
    fun save(): Long {
        val id=repo.save(space,cls,term,day,lesson,photo,_state.value.analysis?.metadata()?.toString() ?: "{}",records)
        persist(); return id
    }
    override fun onCleared() { repo.close() }
}
