import SwiftUI
struct ImportPreviewView:View {
    @State var students:[Student]
    var confirm:([Student])->Void
    @Environment(\.dismiss) private var dismiss
    var body:some View {
        NavigationStack { List {
            Section { Text("共 \(students.count) 人。转班学生可在下方修改当前班级；原班级保留为备注。") }
            ForEach($students) { $s in VStack(alignment:.leading) { Text("\(s.sid) · \(s.name)").font(.headline); TextField("当前班级",text:$s.className); TextField("袋号（留空待分配）",text:$s.pocket).keyboardType(.numberPad); Text("原班级：\(s.sourceClass)").font(.caption).foregroundStyle(.secondary) } }
        }.navigationTitle("导入预览").toolbar { ToolbarItem(placement:.cancellationAction) { Button("取消") { dismiss() } }; ToolbarItem(placement:.confirmationAction) { Button("确认导入") { confirm(students); dismiss() } } } }
    }
}
