package local.phonemanager

import androidx.appcompat.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.content.res.ColorStateList
import android.view.ContextThemeWrapper
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
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

class MainActivity: AppCompatActivity() {
    private val vm: PhoneViewModel by viewModels()
    private lateinit var body: LinearLayout
    private var historyId: Long?=null
    private var termField: EditText?=null
    private var lessonField: EditText?=null
    private val teal get()=tone(androidx.appcompat.R.attr.colorPrimary)
    private lateinit var root: LinearLayout
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
        DynamicColors.applyToActivityIfAvailable(this)
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
    private fun tone(attr: Int)=MaterialColors.getColor(this,attr,"PhonePocketManager")
    private fun text(value: String,size: Float=15f)=TextView(this).apply {
        text=value; textSize=size; setTextColor(tone(com.google.android.material.R.attr.colorOnSurface)); setPadding(0,dp(6),0,dp(6)); setLineSpacing(dp(3).toFloat(),1f)
        if(size>=20) { typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); letterSpacing=-.015f }
        if(size<=13) setTextColor(tone(com.google.android.material.R.attr.colorOnSurfaceVariant))
    }
    private fun button(label: String,action: ()->Unit)=MaterialButton(ContextThemeWrapper(this,com.google.android.material.R.style.Widget_Material3_Button_TonalButton),null,com.google.android.material.R.attr.materialButtonStyle).apply {
        text=label; isAllCaps=false; setOnClickListener { action() }; minHeight=dp(52); cornerRadius=dp(26); textSize=14f
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(6) }; setPadding(dp(16),dp(8),dp(16),dp(8))
    }
    private fun primary(label: String,iconRes: Int,action: ()->Unit)=button(label,action).apply {
        backgroundTintList=ColorStateList.valueOf(teal); setTextColor(tone(com.google.android.material.R.attr.colorOnPrimary)); iconTint=ColorStateList.valueOf(tone(com.google.android.material.R.attr.colorOnPrimary)); setIconResource(iconRes); iconGravity=MaterialButton.ICON_GRAVITY_TEXT_START
    }
    private fun row(parent: LinearLayout,vararg views: View) { val line=LinearLayout(this); views.forEachIndexed { i,v -> line.addView(v,LinearLayout.LayoutParams(0,-2,1f).apply { if(i>0) marginStart=dp(8); bottomMargin=dp(6) }) }; parent.addView(line) }
    private fun card(parent: LinearLayout=body): LinearLayout {
        val shell=MaterialCardView(this).apply { radius=dp(24).toFloat(); cardElevation=0f; strokeWidth=0; setCardBackgroundColor(tone(com.google.android.material.R.attr.colorSurfaceContainerLow)); layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(16) } }
        val content=layout().apply { setPadding(dp(18),dp(14),dp(18),dp(14)) }; shell.addView(content); parent.addView(shell); return content
    }
    private fun edit(label: String,value: String,parent: LinearLayout=body): EditText {
        val wrapper=TextInputLayout(ContextThemeWrapper(this,com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox)).apply {
            hint=label; boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE; setBoxCornerRadii(dp(14).toFloat(),dp(14).toFloat(),dp(14).toFloat(),dp(14).toFloat()); layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8); bottomMargin=dp(8) }
        }
        val field=TextInputEditText(wrapper.context).apply { setText(value); setSingleLine(true); textSize=16f; minHeight=dp(56) }; wrapper.addView(field); parent.addView(wrapper); return field
    }
    private fun message(value: String) { MaterialAlertDialogBuilder(this).setTitle("手机上交管理").setMessage(value).setPositiveButton("知道了",null).show() }
    private fun toast(value: String) { if(::root.isInitialized) Snackbar.make(root,value,Snackbar.LENGTH_LONG).setAnchorView(root.getChildAt(root.childCount-1)).show() else Toast.makeText(this,value,Toast.LENGTH_SHORT).show() }
    private fun guarded(action: ()->Unit) { try { action() } catch(e: Exception) { message(e.message ?: "操作失败") } }
    private fun spacer() { body.addView(View(this),LinearLayout.LayoutParams(-1,dp(12))) }
    private fun render() {
        if(::root.isInitialized) syncFields()
        termField=null; lessonField=null; summaryLabel=null
        root=layout(); root.setBackgroundColor(tone(com.google.android.material.R.attr.colorSurface))
        ViewCompat.setOnApplyWindowInsetsListener(root) { v,insets -> val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()); v.setPadding(0,bars.top,0,bars.bottom); insets }
        val titles=listOf("拍照核对","班级名单","历史记录","统计导出")
        root.addView(MaterialToolbar(this).apply { title=titles[vm.page]; subtitle="手机上交管理"; setTitleTextAppearance(this@MainActivity,com.google.android.material.R.style.TextAppearance_Material3_TitleLarge) })
        val filters=layout().apply { setPadding(dp(20),0,dp(20),0) }
        row(filters,button(if(vm.space=="demo") "演示空间" else "正式记录") { syncFields(); MaterialAlertDialogBuilder(this).setTitle("数据空间").setSingleChoiceItems(arrayOf("演示空间 · 独立测试数据","正式记录 · 日常登记"),if(vm.space=="demo") 0 else 1) { dialog,i -> val space=if(i==0) "demo" else "official"; vm.switch(space,vm.repo.classes(space).first()); historyId=null; dialog.dismiss(); render() }.show() }.apply { setIconResource(R.drawable.ic_expand); iconGravity=MaterialButton.ICON_GRAVITY_END },button(vm.cls) { syncFields(); val classes=vm.repo.classes(vm.space); MaterialAlertDialogBuilder(this).setTitle("选择教学班").setSingleChoiceItems(classes.toTypedArray(),classes.indexOf(vm.cls)) { dialog,i -> vm.switch(vm.space,classes[i]); historyId=null; dialog.dismiss(); render() }.show() }.apply { setIconResource(R.drawable.ic_people) })
        root.addView(filters)
        val scroll=ScrollView(this).apply { isFillViewport=true; clipToPadding=false }; body=layout(); body.setPadding(dp(20),dp(8),dp(20),dp(24)); scroll.addView(body); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val nav=BottomNavigationView(this).apply {
            labelVisibilityMode=NavigationBarView.LABEL_VISIBILITY_LABELED
            listOf("核对" to R.drawable.ic_camera,"班级" to R.drawable.ic_people,"历史" to R.drawable.ic_history,"统计" to R.drawable.ic_stats).forEachIndexed { i,(title,icon) -> menu.add(0,100+i,i,title).setIcon(icon) }
            selectedItemId=100+vm.page
            setOnItemSelectedListener { item -> val target=item.itemId-100; if(target!=vm.page) { syncFields(); vm.page=target; historyId=null; render() }; true }
            setPadding(0,0,0,0)
        }
        ViewCompat.setOnApplyWindowInsetsListener(nav) { _,insets -> insets }
        root.addView(nav); setContentView(root)
        when(vm.page) { 0->recognitionPage();1->rosterPage();2->historyPage();3->statisticsPage() }
    }
    private fun recognitionPage() {
        val hero=card()
        hero.addView(text("让每次核对，更轻松",26f))
        hero.addView(text(if(vm.space=="demo") "用示例照片体验完整流程，测试记录独立保存。" else "拍下手机袋，核对学生姓名，再保存本次课次。",14f))
        row(hero,primary("拍照识别",R.drawable.ic_camera) { syncFields(); takePhoto() },button("从相册选择") { syncFields(); album.launch(arrayOf("image/*")) }.apply { setIconResource(R.drawable.ic_photo) })
        if(vm.space=="demo") hero.addView(button("试用示例照片") { syncFields(); val file=File(filesDir,"sample.jpg"); assets.open("example_edited.jpg").use { input -> file.outputStream().use { input.copyTo(it) } }; vm.analyze(file.path) }.apply { setIconResource(R.drawable.ic_grid); backgroundTintList=ColorStateList.valueOf(Color.TRANSPARENT) })
        val lessonCard=card(); lessonCard.addView(text("本次课次",20f))
        termField=edit("学期",vm.term,lessonCard)
        row(lessonCard,button(vm.day) { syncFields(); val d=runCatching { LocalDate.parse(vm.day) }.getOrDefault(LocalDate.now()); DatePickerDialog(this,{_,y,m,day -> vm.day=LocalDate.of(y,m+1,day).toString(); vm.persist(); render() },d.year,d.monthValue-1,d.dayOfMonth).show() }.apply { setIconResource(R.drawable.ic_calendar) })
        lessonField=edit("课次",vm.lesson,lessonCard)
        val state=vm.state.value
        if(state.loading) { body.addView(CircularProgressIndicator(this).apply { isIndeterminate=true }); body.addView(text("正在本机识别54个袋口…")); return }
        if(vm.photo.isBlank()) { val tips=card(); tips.addView(text("拍摄小提示",18f)); tips.addView(text("拍全 9 行 × 6 列手机袋，尽量正面拍摄，让手机顶部露出袋口。",14f)); return }
        if(state.error.isNotBlank()) body.addView(text(state.error).apply { setTextColor(tone(androidx.appcompat.R.attr.colorError)) })
        state.analysis?.let { a ->
            if(a.warning.isNotBlank()) body.addView(text(a.warning))
            val shown=a.bitmap.copy(Bitmap.Config.ARGB_8888,true); val canvas=Canvas(shown); val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth=3f; textSize=18f }
            a.pockets.forEach { p -> val b=p.box; paint.color=stateColor(when(p.prediction){"phone"->"已交";"empty"->"未交";else->"待复核"}); paint.style=Paint.Style.STROKE; canvas.drawRect(b[0].toFloat(),b[1].toFloat(),b[2].toFloat(),b[3].toFloat(),paint); paint.style=Paint.Style.FILL; canvas.drawText(p.number,b[0].toFloat(),b[3]-3f,paint) }
            body.addView(ImageView(this).apply { setImageBitmap(shown); contentDescription="54 个袋位的识别标注，点击放大"; adjustViewBounds=true; scaleType=ImageView.ScaleType.FIT_CENTER; layoutParams=LinearLayout.LayoutParams(-1,dp(420)); setOnClickListener { showImage(a.bitmap) } })
        }
        row(body,button("校正四角") { syncFields(); calibrate() },button("微调行列") { syncFields(); adjustGrid() })
        body.addView(text("袋位核对",22f))
        body.addView(text("点击卡片查看袋口、修正状态。初筛结果需人工确认。",13f))
        summaryLabel=text(""); body.addView(summaryLabel); updateSummary()
        val byPocket=vm.records.filter { it.student.pocket!=null }.associateBy { it.student.pocket }
        for(r in 0..26) { val views=(0..1).map { c ->
            val number="%02d".format(r*2+c+1); val record=byPocket[number]
            if(record==null) text("$number  未分配\n不参与统计",14f).apply { gravity=Gravity.CENTER; minHeight=dp(88); background=GradientDrawable().apply { cornerRadius=dp(20).toFloat(); setColor(tone(com.google.android.material.R.attr.colorSurfaceContainerLow)) } }
            else recordButton(record) { updateSummary(); vm.persist() }
        }; row(body,*views.toTypedArray()) }
        vm.records.filter { it.student.pocket==null }.forEach { r -> body.addView(recordButton(r) { updateSummary(); vm.persist() }) }
        spacer(); val confirmed=MaterialCheckBox(this).apply { text="我已核对，待复核项暂不计入未交次数" }; body.addView(confirmed)
        body.addView(primary("确认保存课次",R.drawable.ic_check) { syncFields(); if(!confirmed.isChecked) { toast("请先核对并勾选确认"); return@primary }; guarded { vm.save(); toast("课次已保存，重复保存不会重复计次") } })
    }
    private fun stateColor(state: String): Int {
        val dark=(resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES
        return when(state) { "已交"->Color.parseColor(if(dark) "#8DD8B0" else "#146C43"); "未交"->tone(androidx.appcompat.R.attr.colorError); "待复核"->Color.parseColor(if(dark) "#EFCA79" else "#805A00"); else->tone(com.google.android.material.R.attr.colorOnSurfaceVariant) }
    }
    private fun recordButton(r: Record,changed: ()->Unit): MaterialButton {
        val button=button("") {}; fun refresh() { button.text="${r.student.pocket ?: "待分配"}  ${r.student.name}\n${r.state}"; button.setTextColor(stateColor(r.state)); button.backgroundTintList=ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(stateColor(r.state),24)); button.cornerRadius=dp(20); button.minHeight=dp(88); button.gravity=Gravity.START or Gravity.CENTER_VERTICAL; button.textSize=16f; button.contentDescription="${r.student.pocket ?: "待分配"} 号，${r.student.name}，${r.state}，点击修改" }
        refresh(); button.setOnClickListener { editRecord(r) { refresh(); changed() } }; return button
    }
    private fun updateSummary() { summaryLabel?.text="${vm.records.size}人 · 未交 ${vm.records.count { it.state=="未交" }} · 待复核 ${vm.records.count { it.state=="待复核" }}" }
    private fun editRecord(r: Record,done: ()->Unit) {
        val content=layout(); content.setPadding(dp(16),0,dp(16),0)
        content.addView(text("学号：${r.student.sid}"))
        if(vm.page==0) vm.state.value.analysis?.let { a -> a.pockets.firstOrNull { it.number==r.student.pocket }?.let { p -> val b=p.box; val crop=Bitmap.createBitmap(a.bitmap,b[0],b[1],b[2]-b[0],b[3]-b[1]); content.addView(ImageView(this).apply { setImageBitmap(crop); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(200)) }) } }
        val field=TextInputLayout(ContextThemeWrapper(this,com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox_ExposedDropdownMenu)).apply { hint="上交状态"; endIconMode=TextInputLayout.END_ICON_DROPDOWN_MENU }
        val select=MaterialAutoCompleteTextView(field.context).apply { inputType=0; setSimpleItems(STATES.toTypedArray()); setText(r.state,false) }; field.addView(select); content.addView(field)
        val note=edit("备注（补交可改为已交并备注补交）",r.note,content)
        MaterialAlertDialogBuilder(this).setTitle("${r.student.pocket ?: "待分配"} ${r.student.name}").setView(content).setNegativeButton("取消",null).setPositiveButton("确定") { _,_ -> r.state=select.text.toString(); r.note=note.text.toString(); done() }.show()
    }
    private fun showImage(bitmap: Bitmap) { val image=ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds=true }; MaterialAlertDialogBuilder(this).setView(ScrollView(this).apply { addView(image) }).setPositiveButton("关闭",null).show() }
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
            val dialog=MaterialAlertDialogBuilder(this).setTitle("标定手机袋四角").setView(content).setNegativeButton("取消",null).setPositiveButton("应用",null).create()
            dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if(view.points.size!=4) toast("请选满四个角点") else { vm.analyze(corners=view.points.toList()); dialog.dismiss() } } }; dialog.show()
        }
    }
    private fun adjustGrid() {
        val a=vm.state.value.analysis ?: return message("请先识别或标定四角")
        val content=layout(); content.setPadding(dp(16),0,dp(16),0)
        val rows=edit("9条袋口行线（0～1080像素，逗号分隔）",a.rows.joinToString(","),content)
        val cols=edit("7条列边界（0～600像素）",a.cols.joinToString(","),content)
        MaterialAlertDialogBuilder(this).setTitle("微调行列位置").setView(content).setNegativeButton("取消",null).setPositiveButton("重新识别") { _,_ -> guarded { val parse: (String) -> List<Int> = { s -> s.replace('，',',').split(',').map { it.trim().toInt() } }; vm.analyze(corners=a.corners,rows=parse(rows.text.toString()),cols=parse(cols.text.toString())) } }.show()
    }
    private fun rosterPage() {
        body.addView(text("学生名单",26f)); body.addView(primary("导入 Excel 名单",R.drawable.ic_upload) { roster.launch(arrayOf("*/*")) })
        body.addView(text("按学号后两位默认分袋；转入学生可归入当前班并保留原班级。点击学生修改姓名或袋号。",13f))
        val search=edit("搜索姓名或学号",""); val list=layout(); body.addView(list)
        fun fill() { list.removeAllViews(); vm.repo.students(vm.space,vm.cls).filter { search.text.isBlank() || it.name.contains(search.text) || it.sid.contains(search.text) }.forEach { r ->
            list.addView(button("${r.pocket ?: "待分配"}  ${r.name}\n${r.sid}") { val content=layout(); content.setPadding(dp(16),0,dp(16),0); val name=edit("姓名",r.name,content); val pocket=edit("袋号01～54，留空待分配",r.pocket ?: "",content); content.addView(text("表内原班级：${r.source}",13f))
                MaterialAlertDialogBuilder(this).setTitle("修改分配").setView(content).setNegativeButton("取消",null).setPositiveButton("保存") { _,_ -> guarded { val raw=pocket.text.toString().trim(); val p=if(raw.isBlank()) null else "%02d".format(raw.toInt()); vm.repo.importRows(vm.space,listOf(r.copy(name=name.text.toString().trim(),pocket=p))); vm.switch(vm.space,vm.cls); render() } }.show()
            })
        } }; search.addTextChangedListener(SimpleWatcher { fill() }); fill()
    }
    private fun previewImport(rows: List<Student>) {
        val content=layout(); content.setPadding(dp(16),0,dp(16),0)
        content.addView(text("读取 ${rows.size} 人，${rows.map { it.cls }.distinct().size} 个表内班级"))
        val unified=MaterialCheckBox(this).apply { text="统一归入当前教学班（适合转专业学生）" }; content.addView(unified)
        val target=edit("当前教学班名称",rows.groupingBy { it.cls }.eachCount().maxBy { it.value }.key,content)
        MaterialAlertDialogBuilder(this).setTitle("名单导入方式").setView(content).setNegativeButton("取消",null).setPositiveButton("预览") { _,_ -> guarded {
            val incoming=if(unified.isChecked) RosterRules.currentClass(rows,target.text.toString().trim()) else rows
            MaterialAlertDialogBuilder(this).setTitle("确认导入 ${incoming.size} 人").setMessage(incoming.joinToString("\n") { "${it.cls} · ${it.sid} ${it.name} · ${it.pocket ?: "待分配"}" }).setNegativeButton("取消",null).setPositiveButton("导入") { _,_ -> guarded { vm.repo.importRows(vm.space,incoming); vm.switch(vm.space,incoming.first().cls); render(); toast("名单已导入") } }.show()
        } }.show()
    }
    private fun historyPage() {
        body.addView(text("每次登记，都有记录",24f)); val lessons=vm.repo.lessons(vm.space,vm.cls)
        if(lessons.isEmpty()) { card().apply { addView(text("还没有课次记录",20f)); addView(text("完成照片核对后，保存的课次会显示在这里。",14f)) }; return }
        body.addView(button("选择课次") { MaterialAlertDialogBuilder(this).setTitle("历史课次").setItems(lessons.map { "${it.day} ${it.title} · ${it.term}" }.toTypedArray()) { _,i -> historyId=lessons[i].id; render() }.show() })
        val selected=lessons.firstOrNull { it.id==historyId } ?: lessons.first(); historyId=selected.id
        body.addView(text("${selected.term}\n${selected.day} ${selected.title}"))
        body.addView(button("查看当次照片") { guarded { showImage(Recognizer.load(File(selected.photo))) } })
        body.addView(text("点击更正状态及备注。保留当次姓名、袋号快照，统计自动更新。",13f))
        vm.repo.records(selected.id).sortedBy { it.student.pocket ?: "99" }.forEach { r -> body.addView(recordButton(r) { guarded { vm.repo.correct(selected.id,r); toast("更正已保存") } }) }
    }
    private fun statisticsPage() {
        body.addView(text("看见每一次变化",26f)); body.addView(text("${vm.cls} · ${if(vm.space=="demo") "样图测试" else "正式记录"}",13f))
        val term=edit("学期（留空为全部）",vm.term)
        val start=edit("开始日期 YYYY-MM-DD","${LocalDate.now().year}-01-01")
        val end=edit("结束日期 YYYY-MM-DD",LocalDate.now().toString())
        body.addView(primary("导出 Excel 汇总与明细",R.drawable.ic_file) { guarded {
            val bytes=vm.repo.export(vm.space,vm.cls,term.text.toString().trim(),start.text.toString().trim(),end.text.toString().trim())
            File(filesDir,"pending-export.xlsx").writeBytes(bytes); export.launch("手机上交统计_${vm.cls}_${LocalDate.now()}.xlsx")
        } })
        body.addView(text("未交仅统计已保存且最终状态为“未交”的记录。补交请到历史改为“已交”并填写备注。",13f))
        val result=layout(); body.addView(button("查询次数") { guarded {
            val s=start.text.toString(); val e=end.text.toString(); LocalDate.parse(s); LocalDate.parse(e); require(s<=e){"开始日期不能晚于结束日期"}
            val records=vm.repo.lessons(vm.space,vm.cls).filter { it.day>=s && it.day<=e && (term.text.isBlank() || it.term==term.text.toString()) }.flatMap { vm.repo.records(it.id) }
            result.removeAllViews(); result.addView(text("共 ${records.size} 条学生课次记录"))
            (vm.repo.students(vm.space,vm.cls)+records.map { it.student }).distinctBy { it.sid }.forEach { p -> val rs=records.filter { it.student.sid==p.sid }; card(result).apply { addView(text(p.name,20f)); addView(text(p.sid,13f)); addView(text("已交 ${rs.count { it.state=="已交" }}  ·  未交 ${rs.count { it.state=="未交" }}  ·  待复核 ${rs.count { it.state=="待复核" }}",15f)) } }
        } }); body.addView(result)
    }
}
class SimpleWatcher(val changed: ()->Unit): android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
    override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { changed() }
    override fun afterTextChanged(s: android.text.Editable?) {}
}
