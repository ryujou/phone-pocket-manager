import SwiftUI
import PhotosUI
import AVFoundation
struct RecognitionView:View {
    @Environment(AppModel.self) private var model
    @State private var selectedPhoto:PhotosPickerItem?
    @State private var data:Data?
    @State private var analysis:PocketAnalysis?
    @State private var records:[Record]=[]
    @State private var term="2026—2027 学年上学期"
    @State private var day=Date()
    @State private var period="第1节"
    @State private var busy=false
    @State private var camera=false
    @State private var calibration=false
    @State private var accepted=false
    @State private var inspected:Record?
    @State private var token=UUID()
    var body:some View {
        ScrollView {
            VStack(alignment:.leading,spacing:18) {
                Text("拍照核对，轻松登记").font(.title2.bold())
                Text("先识别袋位，再核对姓名。待复核不会计入未交次数。").foregroundStyle(.secondary)
                HStack {
                    Button("拍照",systemImage:"camera",action:openCamera).buttonStyle(.borderedProminent)
                    PhotosPicker(selection:$selectedPhoto,matching:.images) { Label("相册",systemImage:"photo") }.buttonStyle(.bordered)
                    if model.demo { Button("示例",systemImage:"sparkles",action:sample).buttonStyle(.bordered) }
                }.disabled(busy)
                GroupBox("本次课次") {
                    VStack { TextField("学期",text:$term); DatePicker("日期",selection:$day,displayedComponents:.date); TextField("课次",text:$period) }.textFieldStyle(.roundedBorder)
                }
                if busy { ProgressView("正在分析 54 个袋口…") }
                if let data,let image=UIImage(data:data) {
                    Image(uiImage:image).resizable().scaledToFit().frame(maxHeight:260).clipShape(.rect(cornerRadius:16)).accessibilityLabel("本次手机袋照片")
                    Button("校正四角与袋口线",systemImage:"crop.rotate") { calibration=true }.disabled(busy)
                }
                if let analysis {
                    if analysis.clipped { Text("袋体可能未拍全，本次全部待复核。").foregroundStyle(.orange) }
                    Text("袋位核对").font(.headline)
                    Text("绿色已交 · 红色未交 · 黄色待复核 · 灰色未分配").font(.caption).foregroundStyle(.secondary)
                    LazyVGrid(columns:[GridItem(.adaptive(minimum:100))],spacing:10) {
                        ForEach(1...54,id:\.self) { number in
                            let pocket=String(format:"%02d",number)
                            if let i=records.firstIndex(where:{ $0.pocket==pocket }) {
                                PocketCard(record:$records[i]) { inspected=records[i] }
                            } else { VStack { Text(pocket).font(.headline); Text("未分配").font(.caption) }.frame(maxWidth:.infinity,minHeight:90).background(.quaternary,in:.rect(cornerRadius:14)) }
                        }
                    }
                    ForEach(records.filter { $0.pocket.isEmpty }) { row in Text("\(row.name)：袋号待分配，不自动判断").foregroundStyle(.orange) }
                    Toggle("我已核对本次结果",isOn:$accepted)
                    Button("确认保存",systemImage:"checkmark.circle.fill",action:save).buttonStyle(.borderedProminent).controlSize(.large).frame(maxWidth:.infinity).disabled(!accepted || records.isEmpty || busy)
                } else if data==nil { ContentUnavailableView("从一张照片开始",systemImage:"iphone.and.arrow.right.outward",description:Text("拍全 9 行 × 6 列手机袋，让手机顶部露出袋口。")) }
            }.padding()
        }
        .navigationTitle("本次识别")
        .sheet(isPresented:$camera) { CameraPicker { value in data=value; run() }.ignoresSafeArea() }
        .sheet(isPresented:$calibration) { if let data { CalibrationView(data:data,previous:analysis) { points,rows in run(corners:points,rows:rows) } } }
        .sheet(item:$inspected) { row in PocketDetailView(record:row,analysis:analysis) }
        .onChange(of:selectedPhoto) { _,item in Task { do { if let value=try await item?.loadTransferable(type:Data.self) { data=value; run() } } catch { model.error=error.localizedDescription } } }
        .onChange(of:model.className) { resetRecords() }
        .onChange(of:model.demo) { token=UUID(); data=nil; analysis=nil; records=[]; busy=false; accepted=false }
        .onChange(of:day) { accepted=false }
        .onChange(of:term) { accepted=false }
        .onChange(of:period) { accepted=false }
    }
    private func sample() {
        guard let url=Bundle.main.url(forResource:"example_edited",withExtension:"jpg") else { return }
        do { data=try Data(contentsOf:url); run() } catch { model.error=error.localizedDescription }
    }
    private func openCamera() {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else { model.error="模拟器没有相机，请使用示例或相册。"; return }
        Task { if await AVCaptureDevice.requestAccess(for:.video) { camera=true } else { model.error="相机权限未开启，请在系统设置中允许相机访问。" } }
    }
    private func run(corners:[CGPoint]?=nil,rows:[Int]?=nil) {
        guard let data else { return }; accepted=false; busy=true; analysis=nil; records=[]
        let current=UUID(); token=current
        Task {
            do {
                let result=try await Task.detached(priority:.userInitiated) { try PhoneAnalyzer.analyze(data,corners:corners,rows:rows) }.value
                guard token==current else { return }; analysis=result; resetRecords()
            } catch { if token==current { model.error=error.localizedDescription } }
            if token==current { busy=false }
        }
    }
    private func resetRecords() {
        accepted=false
        records=model.students.map { s in
            let i=(Int(s.pocket) ?? 0)-1
            let state = analysis.flatMap { $0.states.indices.contains(i) ? $0.states[i] : nil } ?? .review
            return Record(sid:s.sid,name:s.name,pocket:s.pocket,prediction:state,score:analysis.flatMap { $0.scores.indices.contains(i) ? $0.scores[i] : nil } ?? 0,state:state)
        }
    }
    private func save() {
        guard let analysis else { return }
        let f=DateFormatter(); f.dateFormat="yyyy-MM-dd"; f.locale=Locale(identifier:"en_US_POSIX")
        let lesson=Lesson(className:model.className,term:term.trimmingCharacters(in:.whitespaces),day:f.string(from:day),period:period.trimmingCharacters(in:.whitespaces),photo:"",calibration:analysis.corners.flatMap { [$0.x,$0.y] }+analysis.rows.map(Double.init),records:records)
        model.save(lesson,data:data)
    }
}
