package local.phonemanager

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

class MainActivity: ComponentActivity() {
    private val vm: PhoneViewModel by viewModels()
    private lateinit var body: LinearLayout
    private var historyId: Long?=null
    private var termField: EditText?=null
    private var lessonField: EditText?=null
    private val teal=Color.rgb(8,127,140)
    private var summaryLabel: TextView?=null
    private val camera=registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path=vm.prefs.getString("pendingCamera",null)
        vm.prefs.edit().remove("pendingCamera").apply()
        if(ok && path!=null && File(path).length()>0) vm.analyze(path) else toast("已取消拍照")
    }
    private val album=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) importPhoto(uri) }
    private val roster=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) lifecycleScope.launch {
            try {
                val students=withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)!!.use { Xlsx.read(it) } }
                previewImport(students)
            } catch(e: Exception) { message(e.message ?: "名单导入失败") }
        }
    }
    private val export=registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if(uri!=null) lifecycleScope.launch {
            try { withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri,"wt")!!.use { out -> File(filesDir,"pending-export.xlsx").inputStream().use { it.copyTo(out) } } }; toast("Excel 已保存") }
            catch(e: Exception) { message("保存失败：${e.message}") }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        historyId=savedInstanceState?.getLong("history",-1L)?.takeIf { it>=0 }
        render()
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { vm.state.collect { render() } } }
    }
    override fun onSaveInstanceState(out: Bundle) { syncFields(); out.putLong("history",historyId ?: -1L); super.onSaveInstanceState(out) }
    override fun onPause() { syncFields(); super.onPause() }
    private fun syncFields() { termField?.let { vm.term=it.text.toString() }; lessonField?.let { vm.lesson=it.text.toString() }; vm.persist() }
    private fun dp(n: Int)=(n*resources.displayMetrics.density).toInt()
    private fun layout()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun text(value: String,size: Float=15f)=TextView(this).apply { text=value; textSize=size; setTextColor(Color.rgb(23,42,52)); setPadding(dp(4),dp(5),dp(4),dp(5)) }
    private fun button(label: String,action: ()->Unit)=Button(this).apply { text=label; isAllCaps=false; setTextColor(teal); setOnClickListener { action() }; minHeight=dp(48) }
    private fun row(parent: LinearLayout,vararg views: View) { val line=LinearLayout(this); views.forEach { line.addView(it,LinearLayout.LayoutParams(0,-2,1f)) }; parent.addView(line) }
    private fun edit(label: String,value: String,parent: LinearLayout=body): EditText {
        parent.addView(text(label,13f)); return EditText(this).apply { setText(value); setSingleLine(true); textSize=16f; parent.addView(this) }
    }
    private fun message(value: String) { AlertDialog.Builder(this).setTitle("手机上交管理").setMessage(value).setPositiveButton("知道了",null).show() }
    private fun toast(value: String)=Toast.makeText(this,value,Toast.LENGTH_SHORT).show()
    private fun guarded(action: ()->Unit) { try { action() } catch(e: Exception) { message(e.message ?: "操作失败") } }
    private fun spacer() { body.addView(View(this),LinearLayout.LayoutParams(-1,dp(12))) }
    private fun render() {
        termField=null; lessonField=null; summaryLabel=null
        val root=layout(); root.setBackgroundColor(Color.rgb(244,247,248)); root.setPadding(dp(12),0,dp(12),0)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v,insets -> val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()); v.setPadding(dp(12),bars.top,dp(12),bars.bottom); insets }
        root.addView(text("手机上交管理",23f).apply { setTextColor(teal) })
        row(root,button(if(vm.space=="demo") "样图测试 ▾" else "正式记录 ▾") { syncFields(); AlertDialog.Builder(this).setTitle("数据空间").setItems(arrayOf("样图测试","正式记录")) { _,i -> val space=if(i==0) "demo" else "official"; vm.switch(space,vm.repo.classes(space).first()); historyId=null; render() }.show() },button("选择班级 ▾") { syncFields(); val classes=vm.repo.classes(vm.space); AlertDialog.Builder(this).setTitle("当前教学班").setItems(classes.toTypedArray()) { _,i -> vm.switch(vm.space,classes[i]); historyId=null; render() }.show() })
        root.addView(text("${vm.cls} · ${vm.repo.students(vm.space,vm.cls).size} 人",13f))
        row(root,*listOf("拍照核对","名单","历史","统计").mapIndexed { i,title -> button(title) { syncFields(); vm.page=i; render() }.apply { if(vm.page==i) setTypeface(null,Typeface.BOLD) } }.toTypedArray())
        val scroll=ScrollView(this); body=layout(); body.setPadding(0,dp(8),0,dp(24)); scroll.addView(body); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
        when(vm.page) { 0->recognitionPage();1->rosterPage();2->historyPage();3->statisticsPage() }
    }
    private fun recognitionPage() {
        body.addView(text(if(vm.space=="demo") "当前为测试空间，保存不会增加正式次数。" else "先拍照，再核对。确认保存后才登记本课次。",13f))
        termField=edit("学期",vm.term)
        row(body,button(vm.day) { syncFields(); val d=runCatching { LocalDate.parse(vm.day) }.getOrDefault(LocalDate.now()); DatePickerDialog(this,{_,y,m,day -> vm.day=LocalDate.of(y,m+1,day).toString(); vm.persist(); render() },d.year,d.monthValue-1,d.dayOfMonth).show() })
        lessonField=edit("课次（同一天每节课用不同名称）",vm.lesson)
        row(body,button("直接拍照") { syncFields(); takePhoto() },button("相册选择") { syncFields(); album.launch(arrayOf("image/*")) })
        if(vm.space=="demo") body.addView(button("试用AI修改示例图") { syncFields(); val file=File(filesDir,"sample.jpg"); assets.open("example_edited.jpg").use { input -> file.outputStream().use { input.copyTo(it) } }; vm.analyze(file.path) })
        val state=vm.state.value
        if(state.loading) { body.addView(ProgressBar(this)); body.addView(text("正在本机识别54个袋口…")); return }
        if(vm.photo.isBlank()) { body.addView(text("请拍全9行×6列手机袋，尽量正面拍摄，让手机顶部露出袋口。",17f)); return }
        if(state.error.isNotBlank()) body.addView(text(state.error))
        state.analysis?.let { a ->
            if(a.warning.isNotBlank()) body.addView(text(a.warning))
            val shown=a.bitmap.copy(Bitmap.Config.ARGB_8888,true); val canvas=Canvas(shown); val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth=3f; textSize=18f }
            a.pockets.forEach { p -> val b=p.box; paint.color=stateColor(when(p.prediction){"phone"->"已交";"empty"->"未交";else->"待复核"}); paint.style=Paint.Style.STROKE; canvas.drawRect(b[0].toFloat(),b[1].toFloat(),b[2].toFloat(),b[3].toFloat(),paint); paint.style=Paint.Style.FILL; canvas.drawText(p.number,b[0].toFloat(),b[3]-3f,paint) }
            body.addView(ImageView(this).apply { setImageBitmap(shown); adjustViewBounds=true; scaleType=ImageView.ScaleType.FIT_CENTER; layoutParams=LinearLayout.LayoutParams(-1,dp(420)); setOnClickListener { showImage(a.bitmap) } })
        }
        row(body,button("校正四角") { syncFields(); calibrate() },button("微调行列") { syncFields(); adjustGrid() })
        body.addView(text("点击学生卡片可放大袋口，并修改状态、填写备注。颜色为初筛结果。",13f))
        summaryLabel=text(""); body.addView(summaryLabel); updateSummary()
        val byPocket=vm.records.filter { it.student.pocket!=null }.associateBy { it.student.pocket }
        for(r in 0..26) { val views=(0..1).map { c ->
            val number="%02d".format(r*2+c+1); val record=byPocket[number]
            if(record==null) text("$number  未分配\n不参与统计",14f).apply { setPadding(dp(8),dp(14),dp(8),dp(14)); setBackgroundColor(Color.LTGRAY) }
            else recordButton(record) { updateSummary(); vm.persist() }
        }; row(body,*views.toTypedArray()) }
        vm.records.filter { it.student.pocket==null }.forEach { r -> body.addView(recordButton(r) { updateSummary(); vm.persist() }) }
        spacer(); val confirmed=CheckBox(this).apply { text="我已核对，待复核项暂不计入未交次数" }; body.addView(confirmed)
        body.addView(button("确认保存课次") { syncFields(); if(!confirmed.isChecked) { toast("请先核对并勾选确认"); return@button }; guarded { vm.save(); toast("课次已保存，重复保存不会重复计次") } })
    }
    private fun stateColor(state: String)=when(state){"已交"->Color.rgb(18,143,101);"未交"->Color.rgb(202,57,68);"待复核"->Color.rgb(173,117,0);else->Color.DKGRAY}
    private fun recordButton(r: Record,changed: ()->Unit): Button {
        val button=button("") {}; fun refresh() { button.text="${r.student.pocket ?: "待分配"}  ${r.student.name}\n${r.state}"; button.setTextColor(stateColor(r.state)) }
        refresh(); button.setOnClickListener { editRecord(r) { refresh(); changed() } }; return button
    }
    private fun updateSummary() { summaryLabel?.text="${vm.records.size}人 · 未交 ${vm.records.count { it.state=="未交" }} · 待复核 ${vm.records.count { it.state=="待复核" }}" }
    private fun editRecord(r: Record,done: ()->Unit) {
        val content=layout(); content.setPadding(dp(16),0,dp(16),0)
        content.addView(text("学号：${r.student.sid}"))
        if(vm.page==0) vm.state.value.analysis?.let { a -> a.pockets.firstOrNull { it.number==r.student.pocket }?.let { p -> val b=p.box; val crop=Bitmap.createBitmap(a.bitmap,b[0],b[1],b[2]-b[0],b[3]-b[1]); content.addView(ImageView(this).apply { setImageBitmap(crop); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(200)) }) } }
        val select=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,STATES); setSelection(STATES.indexOf(r.state)) }; content.addView(select)
        val note=edit("备注（补交可改为已交并备注补交）",r.note,content)
        AlertDialog.Builder(this).setTitle("${r.student.pocket ?: "待分配"} ${r.student.name}").setView(content).setNegativeButton("取消",null).setPositiveButton("确定") { _,_ -> r.state=STATES[select.selectedItemPosition]; r.note=note.text.toString(); done() }.show()
    }
    private fun showImage(bitmap: Bitmap) { val image=ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds=true }; AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(image) }).setPositiveButton("关闭",null).show() }
    private fun takePhoto() {
        val intent=Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        if(intent.resolveActivity(packageManager)==null) { message("这台设备没有可用的相机应用，请使用相册选择"); return }
        val file=File(File(filesDir,"camera").apply { mkdirs() },"photo-${System.currentTimeMillis()}.jpg")
        vm.prefs.edit().putString("pendingCamera",file.path).commit()
        try { camera.launch(FileProvider.getUriForFile(this,"$packageName.files",file)) }
        catch(e: Exception) { message("无法打开相机：${e.message}") }
    }
    private fun importPhoto(uri: Uri) { lifecycleScope.launch {
        try {
            val file=withContext(Dispatchers.IO) {
                val dest=File(File(filesDir,"camera").apply { mkdirs() },"album-${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)!!.use { input -> dest.outputStream().use { out -> val b=ByteArray(8192);var total=0;while(true){val n=input.read(b);if(n<0)break;total+=n;require(total<=30_000_000){"照片超过30MB，请缩小后重试"};out.write(b,0,n)} } }; dest
            }; vm.analyze(file.path)
        } catch(e: Exception) { message(e.message ?: "读取图片失败") }
    } }
    private fun calibrate() {
        guarded {
            val bitmap=Recognizer.load(File(vm.photo)); val content=layout(); val hint=text("按左上 → 右上 → 右下 → 左下点击，包含第一排上方区域")
            val view=CalibrationView(this,bitmap); content.addView(hint); content.addView(view); view.changed={ hint.text="已选 $it / 4 个角点；选满后再次点击可重选" }
            val dialog=AlertDialog.Builder(this).setTitle("标定手机袋四角").setView(content).setNegativeButton("取消",null).setPositiveButton("应用",null).create()
            dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if(view.points.size!=4) toast("请选满四个角点") else { vm.analyze(corners=view.points.toList()); dialog.dismiss() } } }; dialog.show()
        }
    }
    private fun adjustGrid() {
        val a=vm.state.value.analysis ?: return message("请先识别或标定四角")
        val content=layout(); content.setPadding(dp(16),0,dp(16),0)
        val rows=edit("9条袋口行线（0～1080像素，逗号分隔）",a.rows.joinToString(","),content)
        val cols=edit("7条列边界（0～600像素）",a.cols.joinToString(","),content)
        AlertDialog.Builder(this).setTitle("微调行列位置").setView(content).setNegativeButton("取消",null).setPositiveButton("重新识别") { _,_ -> guarded { val parse: (String) -> List<Int> = { s -> s.replace('，',',').split(',').map { it.trim().toInt() } }; vm.analyze(corners=a.corners,rows=parse(rows.text.toString()),cols=parse(cols.text.toString())) } }.show()
    }
    private fun rosterPage() {
        body.addView(text("名单管理",22f)); body.addView(button("导入 XLSX 新班级 / 更新名单") { roster.launch(arrayOf("*/*")) })
        body.addView(text("按学号后两位默认分袋；转入学生可归入当前班并保留原班级。点击学生修改姓名或袋号。",13f))
        val search=edit("搜索姓名或学号",""); val list=layout(); body.addView(list)
        fun fill() { list.removeAllViews(); vm.repo.students(vm.space,vm.cls).filter { search.text.isBlank() || it.name.contains(search.text) || it.sid.contains(search.text) }.forEach { r ->
            list.addView(button("${r.pocket ?: "待分配"}  ${r.name}  ${r.sid}") { val content=layout(); content.setPadding(dp(16),0,dp(16),0); val name=edit("姓名",r.name,content); val pocket=edit("袋号01～54，留空待分配",r.pocket ?: "",content); content.addView(text("表内原班级：${r.source}",13f))
                AlertDialog.Builder(this).setTitle("修改分配").setView(content).setNegativeButton("取消",null).setPositiveButton("保存") { _,_ -> guarded { val raw=pocket.text.toString().trim(); val p=if(raw.isBlank()) null else "%02d".format(raw.toInt()); vm.repo.importRows(vm.space,listOf(r.copy(name=name.text.toString().trim(),pocket=p))); vm.switch(vm.space,vm.cls); render() } }.show()
            })
        } }; search.addTextChangedListener(SimpleWatcher { fill() }); fill()
    }
    private fun previewImport(rows: List<Student>) {
        val content=layout(); content.setPadding(dp(16),0,dp(16),0)
        content.addView(text("读取 ${rows.size} 人，${rows.map { it.cls }.distinct().size} 个表内班级"))
        val unified=CheckBox(this).apply { text="统一归入当前教学班（适合转专业学生）" }; content.addView(unified)
        val target=edit("当前教学班名称",rows.groupingBy { it.cls }.eachCount().maxBy { it.value }.key,content)
        AlertDialog.Builder(this).setTitle("名单导入方式").setView(content).setNegativeButton("取消",null).setPositiveButton("预览") { _,_ -> guarded {
            val incoming=if(unified.isChecked) RosterRules.currentClass(rows,target.text.toString().trim()) else rows
            AlertDialog.Builder(this).setTitle("确认导入 ${incoming.size} 人").setMessage(incoming.joinToString("\n") { "${it.cls} · ${it.sid} ${it.name} · ${it.pocket ?: "待分配"}" }).setNegativeButton("取消",null).setPositiveButton("导入") { _,_ -> guarded { vm.repo.importRows(vm.space,incoming); vm.switch(vm.space,incoming.first().cls); render(); toast("名单已导入") } }.show()
        } }.show()
    }
    private fun historyPage() {
        body.addView(text("历史课次",22f)); val lessons=vm.repo.lessons(vm.space,vm.cls)
        if(lessons.isEmpty()) { body.addView(text("当前班级还没有已保存课次")); return }
        body.addView(button("选择课次") { AlertDialog.Builder(this).setTitle("历史课次").setItems(lessons.map { "${it.day} ${it.title} · ${it.term}" }.toTypedArray()) { _,i -> historyId=lessons[i].id; render() }.show() })
        val selected=lessons.firstOrNull { it.id==historyId } ?: lessons.first(); historyId=selected.id
        body.addView(text("${selected.term}\n${selected.day} ${selected.title}"))
        body.addView(button("查看当次照片") { guarded { showImage(Recognizer.load(File(selected.photo))) } })
        body.addView(text("点击更正状态及备注。保留当次姓名、袋号快照，统计自动更新。",13f))
        vm.repo.records(selected.id).sortedBy { it.student.pocket ?: "99" }.forEach { r -> body.addView(recordButton(r) { guarded { vm.repo.correct(selected.id,r); toast("更正已保存") } }) }
    }
    private fun statisticsPage() {
        body.addView(text("学期统计与导出",22f)); body.addView(text("${vm.cls} · ${if(vm.space=="demo") "样图测试" else "正式记录"}",13f))
        val term=edit("学期（留空为全部）",vm.term)
        val start=edit("开始日期 YYYY-MM-DD","${LocalDate.now().year}-01-01")
        val end=edit("结束日期 YYYY-MM-DD",LocalDate.now().toString())
        body.addView(button("导出 Excel 汇总与明细") { guarded {
            val bytes=vm.repo.export(vm.space,vm.cls,term.text.toString().trim(),start.text.toString().trim(),end.text.toString().trim())
            File(filesDir,"pending-export.xlsx").writeBytes(bytes); export.launch("手机上交统计_${vm.cls}_${LocalDate.now()}.xlsx")
        } })
        body.addView(text("未交仅统计已保存且最终状态为“未交”的记录。补交请到历史改为“已交”并填写备注。",13f))
        val result=layout(); body.addView(button("查询次数") { guarded {
            val s=start.text.toString(); val e=end.text.toString(); LocalDate.parse(s); LocalDate.parse(e); require(s<=e){"开始日期不能晚于结束日期"}
            val records=vm.repo.lessons(vm.space,vm.cls).filter { it.day>=s && it.day<=e && (term.text.isBlank() || it.term==term.text.toString()) }.flatMap { vm.repo.records(it.id) }
            result.removeAllViews(); result.addView(text("共 ${records.size} 条学生课次记录"))
            (vm.repo.students(vm.space,vm.cls)+records.map { it.student }).distinctBy { it.sid }.forEach { p -> val rs=records.filter { it.student.sid==p.sid }; result.addView(text("${p.name} · ${p.sid}\n未交 ${rs.count { it.state=="未交" }} 次 · 已交 ${rs.count { it.state=="已交" }} 次 · 待复核 ${rs.count { it.state=="待复核" }} 次")) }
        } }); body.addView(result)
    }
}
class SimpleWatcher(val changed: ()->Unit): android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
    override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { changed() }
    override fun afterTextChanged(s: android.text.Editable?) {}
}
