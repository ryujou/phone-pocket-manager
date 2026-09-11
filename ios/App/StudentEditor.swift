import SwiftUI
struct StudentEditor:View {
    @State var student:Student
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    var body:some View {
        NavigationStack { Form {
            LabeledContent("学号",value:student.sid)
            TextField("姓名",text:$student.name)
            TextField("袋号（留空待分配）",text:$student.pocket).keyboardType(.numberPad)
            Text("当前班级：\(student.className)")
            Text("原班级：\(student.sourceClass)").foregroundStyle(.secondary)
            Text("修改不影响旧课次的姓名与袋号快照。交换袋号时，可先将一个袋号留空保存。")
        }.navigationTitle("编辑学生").toolbar { Button("保存") { model.importStudents([student]); dismiss() } } }
    }
}
