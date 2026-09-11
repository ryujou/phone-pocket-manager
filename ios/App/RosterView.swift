import SwiftUI
import UniformTypeIdentifiers
struct RosterView:View {
    @Environment(AppModel.self) private var model
    @State private var importing=false
    @State private var pending:[Student]=[]
    @State private var preview=false
    @State private var search=""
    @State private var editing:Student?
    var body:some View {
        @Bindable var model=model
        List {
            Section("数据空间") { Toggle("使用虚构演示数据",isOn:$model.demo).onChange(of:model.demo) { model.load() }; Text("正式记录初始为空。两个空间独立保存在本机。").font(.caption).foregroundStyle(.secondary) }
            Section { Button("导入 Excel 名单",systemImage:"square.and.arrow.down") { importing=true }; Text("支持多个班级，保留学号文本。先预览，再确认导入。").font(.caption).foregroundStyle(.secondary) }
            Section(model.className.isEmpty ? "尚无班级" : model.className) {
                ForEach(model.students.filter { search.isEmpty || $0.name.contains(search) || $0.sid.contains(search) }) { s in Button { editing=s } label: { HStack { Text(s.pocket.isEmpty ? "—" : s.pocket).font(.title3.monospacedDigit()).foregroundStyle(.teal); VStack(alignment:.leading) { Text(s.name).foregroundStyle(.primary); Text(s.sid).font(.caption).foregroundStyle(.secondary) }; Spacer(); Image(systemName:"pencil").foregroundStyle(.secondary) } } }
            }
        }.navigationTitle("班级与名单").searchable(text:$search,prompt:"姓名或学号")
        .fileImporter(isPresented:$importing,allowedContentTypes:[UTType(filenameExtension:"xlsx") ?? .data]) { result in
            do { let url=try result.get(); let access=url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }; pending=try Workbook.students(Data(contentsOf:url)); preview=true } catch { model.error=error.localizedDescription }
        }
        .sheet(isPresented:$preview) { ImportPreviewView(students:pending) { model.importStudents($0) } }
        .sheet(item:$editing) { StudentEditor(student:$0) }
    }
}
