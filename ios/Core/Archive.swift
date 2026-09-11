import Foundation
struct Archive: Codable, Sendable {
    var students: [Student] = []
    var lessons: [Lesson] = []
    var changes: [ChangeLog] = []
    mutating func importStudents(_ incoming: [Student]) throws {
        guard !incoming.isEmpty else { throw PocketError.message("没有找到学生数据。") }
        guard Set(incoming.map(\.id)).count == incoming.count else { throw PocketError.message("导入表存在同班重复学号。") }
        let ids = Set(incoming.map(\.id))
        let merged = students.filter { !ids.contains($0.id) } + incoming
        for row in merged {
            guard !row.sid.isEmpty, !row.name.isEmpty, !row.className.isEmpty else { throw PocketError.message("学号、姓名、班级不能为空。") }
            guard row.pocket.isEmpty || (Int(row.pocket).map { (1...54).contains($0) } ?? false) else { throw PocketError.message("袋号须为 01～54，或留空待分配。") }
        }
        let assigned = merged.filter { !$0.pocket.isEmpty }.map { $0.className + "|" + String(Int($0.pocket)!) }
        guard Set(assigned).count == assigned.count else { throw PocketError.message("同班袋号冲突。请在 Excel 中调整袋号，或留空待分配后重新导入。") }
        students = merged.map { var s = $0; if !s.pocket.isEmpty { s.pocket = String(format: "%02d", Int(s.pocket)!) }; return s }.sorted { $0.id < $1.id }
    }
    mutating func save(_ draft: Lesson) throws {
        guard !draft.className.isEmpty, !draft.term.trimmingCharacters(in: .whitespaces).isEmpty, !draft.period.trimmingCharacters(in: .whitespaces).isEmpty else { throw PocketError.message("班级、学期、课次不能为空。") }
        guard Set(draft.records.map(\.sid)).count == draft.records.count else { throw PocketError.message("课次内学号重复。") }
        var lesson = draft
        if let i = lessons.firstIndex(where: { $0.key == draft.key }) {
            let old = lessons[i]
            guard Set(old.records.map(\.sid)) == Set(draft.records.map(\.sid)) else { throw PocketError.message("该课次已有不同名单，请到历史页面修正，或使用新课次。") }
            lesson.id = old.id
            for j in lesson.records.indices {
                if let prior = old.records.first(where: { $0.sid == lesson.records[j].sid }) {
                    lesson.records[j].name = prior.name; lesson.records[j].pocket = prior.pocket
                }
            }
            changes.append(ChangeLog(lessonID: old.id, before: old.records, after: lesson.records))
            lessons[i] = lesson
        } else {
            changes.append(ChangeLog(lessonID: lesson.id, before: [], after: lesson.records))
            lessons.append(lesson)
        }
    }
}
