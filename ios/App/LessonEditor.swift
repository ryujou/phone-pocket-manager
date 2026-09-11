import SwiftUI
struct LessonEditor:View {
    @State var lesson:Lesson
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    var body:some View {
        NavigationStack { Form {
            Section { Text("\(lesson.day) · \(lesson.period)"); Text(lesson.term); if let image=UIImage(contentsOfFile:model.folder.appending(path:lesson.photo).path) { Image(uiImage:image).resizable().scaledToFit().frame(maxHeight:220) } }
            ForEach($lesson.records) { $record in Section("\(record.pocket) · \(record.name)") { Text(record.sid).font(.caption); Text("原始预测：\(record.prediction.rawValue)").foregroundStyle(.secondary); Picker("最终状态",selection:$record.state) { ForEach(SubmissionState.allCases) { Text($0.rawValue).tag($0) } }; TextField("备注（例如补交）",text:$record.note) } }
            Section("修改日志") { Text("本课次已保存 \(model.archive.changes.filter { $0.lessonID==lesson.id }.count) 次，修改前后的状态保留在本机。") }
        }.navigationTitle("更正课次").toolbar { Button("保存") { model.save(lesson); dismiss() } } }
    }
}
