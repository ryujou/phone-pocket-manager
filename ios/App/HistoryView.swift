import SwiftUI
struct HistoryView:View {
    @Environment(AppModel.self) private var model
    @State private var selected:Lesson?
    var body:some View {
        List {
            ForEach(model.archive.lessons.filter { $0.className==model.className }.sorted { $0.day+$0.period > $1.day+$1.period }) { lesson in
                Button { selected=lesson } label: { VStack(alignment:.leading,spacing:6) { Text("\(lesson.day) · \(lesson.period)").font(.headline).foregroundStyle(.primary); Text(lesson.term).font(.caption).foregroundStyle(.secondary); Text("\(lesson.records.count) 人 · 确认未交 \(lesson.records.filter { $0.state == .missing }.count) 人").foregroundStyle(.teal) } }
            }
        }.navigationTitle("历史记录")
        .overlay { if model.archive.lessons.filter({ $0.className==model.className }).isEmpty { ContentUnavailableView("还没有课次",systemImage:"clock",description:Text("核对照片并保存后，可在这里更正记录。")) } }
        .sheet(item:$selected) { LessonEditor(lesson:$0) }
    }
}
