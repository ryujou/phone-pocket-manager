import SwiftUI
struct RootView: View {
    @Environment(AppModel.self) private var model
    var body: some View {
        @Bindable var model=model
        TabView {
            Tab("识别",systemImage:"camera.viewfinder") { NavigationStack { RecognitionView() } }
            Tab("班级",systemImage:"person.2") { NavigationStack { RosterView() } }
            Tab("历史",systemImage:"clock") { NavigationStack { HistoryView() } }
            Tab("统计",systemImage:"chart.bar.xaxis") { NavigationStack { StatisticsView() } }
        }
        .safeAreaInset(edge:.top) { HStack { Text(model.demo ? "演示空间" : "正式记录").font(.caption).foregroundStyle(model.demo ? .orange : .teal); Spacer(); Menu(model.className.isEmpty ? "导入班级" : model.className) { ForEach(model.classes,id:\.self) { cls in Button(cls) { model.className=cls } } } }.padding(.horizontal).padding(.vertical,6).background(.bar) }
        .alert("提示",isPresented:Binding(get:{ model.error != nil || model.notice != nil },set:{ if !$0 { model.error=nil; model.notice=nil } })) { Button("知道了") { model.error=nil; model.notice=nil } } message: { Text(model.error ?? model.notice ?? "") }
    }
}
