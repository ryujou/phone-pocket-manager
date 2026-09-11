import SwiftUI
struct StatisticsView:View {
    @Environment(AppModel.self) private var model
    @State private var allClasses=false
    @State private var term="全部学期"
    @State private var start=Calendar.current.date(from:DateComponents(year:2026,month:1,day:1)) ?? Date()
    @State private var end=Date()
    @State private var exported:URL?
    private var lessons:[Lesson] {
        let f=DateFormatter(); f.dateFormat="yyyy-MM-dd"; f.locale=Locale(identifier:"en_US_POSIX")
        return model.archive.lessons.filter { (allClasses || $0.className==model.className) && (term=="全部学期" || $0.term==term) && $0.day>=f.string(from:start) && $0.day<=f.string(from:end) }
    }
    var body:some View {
        List {
            Section("筛选") {
                Toggle("全部班级",isOn:$allClasses)
                Picker("学期",selection:$term) { Text("全部学期").tag("全部学期"); ForEach(Array(Set(model.archive.lessons.map(\.term))).sorted(),id:\.self) { Text($0).tag($0) } }
                DatePicker("开始",selection:$start,displayedComponents:.date); DatePicker("结束",selection:$end,displayedComponents:.date)
            }
            Section("当前结果") {
                LabeledContent("课次",value:"\(lessons.count)")
                ForEach(SubmissionState.allCases) { state in LabeledContent(state.rawValue+"次数",value:"\(lessons.flatMap(\.records).filter { $0.state==state }.count)") }
                Text("未交仅统计最终状态为未交的有效明细。").font(.caption).foregroundStyle(.secondary)
            }
            Section {
                Button("生成 Excel 汇总与明细",systemImage:"tablecells",action:export).disabled(start>end)
                if let exported { ShareLink("分享 / 存储到文件",item:exported) }
                Text("照片和记录只保存在本机；卸载前请导出。与 Mac 暂不自动同步。").font(.caption).foregroundStyle(.secondary)
            }
        }.navigationTitle("统计导出")
        .onChange(of:allClasses) { exported=nil }.onChange(of:term) { exported=nil }.onChange(of:start) { exported=nil }.onChange(of:end) { exported=nil }.onChange(of:model.className) { exported=nil }.onChange(of:model.demo) { exported=nil; term="全部学期" }.onChange(of:model.archive.lessons) { exported=nil }
    }
    private func export() {
        do { let url=URL.temporaryDirectory.appending(path:"手机上交统计-\(UUID().uuidString.prefix(8)).xlsx"); try Workbook.export(students:allClasses ? model.archive.students : model.students,lessons:lessons).write(to:url,options:.atomic); exported=url } catch { model.error=error.localizedDescription }
    }
}
