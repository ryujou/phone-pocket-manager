import SwiftUI
@Observable @MainActor
final class AppModel {
    var archive = Archive()
    var demo = true
    var className = ""
    var error: String?
    var notice: String?
    var ready = false
    let root: URL
    init(root: URL? = nil) {
        self.root = root ?? URL.applicationSupportDirectory.appending(path:"PhonePocketManager",directoryHint:.isDirectory)
        load()
    }
    var folder: URL { root.appending(path:demo ? "demo" : "official",directoryHint:.isDirectory) }
    var classes: [String] { Array(Set(archive.students.map(\.className))).sorted() }
    var students: [Student] { archive.students.filter { $0.className == className }.sorted { $0.pocket < $1.pocket } }
    func load() {
        ready = false
        do {
            var next = try ArchiveFile(url:folder.appending(path:"records.json")).load()
            if demo && next.students.isEmpty, let url=Bundle.main.url(forResource:"roster",withExtension:"xlsx") { try next.importStudents(Workbook.students(Data(contentsOf:url))) }
            archive=next; className=classes.first ?? ""; ready=true
        } catch { self.error="读取记录失败，已阻止保存以保护原文件：\(error.localizedDescription)" }
    }
    func commit(_ value: Archive) throws {
        guard ready else { throw PocketError.message("数据尚未正确加载，请重新打开应用。") }
        try ArchiveFile(url:folder.appending(path:"records.json")).save(value)
        archive=value
    }
    func importStudents(_ rows:[Student]) {
        do { var next=archive; try next.importStudents(rows); try commit(next); if !classes.contains(className) { className=classes.first ?? "" }; notice="已导入 \(rows.count) 名学生" } catch { self.error=error.localizedDescription }
    }
    func save(_ lesson:Lesson, data:Data? = nil) {
        do {
            var draft=lesson, next=archive
            if let data { let name=UUID().uuidString+".jpg"; try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true); try data.write(to:folder.appending(path:name),options:.atomic); draft.photo=name }
            try next.save(draft); try commit(next); notice="已保存；重复保存不会增加次数。"
        } catch { self.error=error.localizedDescription }
    }
}
