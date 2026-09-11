import Foundation
import Testing
@testable import PocketCore

struct CoreTests {
    func student(_ sid:String="00101",_ name:String="张三",_ pocket:String="01",_ cls:String="示例一班") -> Student { Student(sid:sid,name:name,className:cls,pocket:pocket,sourceClass:cls) }
    func lesson(_ state:SubmissionState) -> Lesson { Lesson(className:"示例一班",term:"测试学期",day:"2026-09-01",period:"第1节",photo:"",records:[Record(sid:"00101",name:"张三",pocket:"01",prediction:.review,score:0.5,state:state)]) }
    @Test func duplicateSaveAndHistoricalSnapshot() throws {
        var db=Archive(); try db.importStudents([student()]); try db.save(lesson(.missing)); let id=db.lessons[0].id
        try db.importStudents([student("00101","李四","02")]); var changed=lesson(.submitted); changed.records[0].name="李四"; changed.records[0].pocket="02"; try db.save(changed)
        #expect(db.lessons.count==1); #expect(db.lessons[0].id==id); #expect(db.lessons[0].records[0].name=="张三"); #expect(db.lessons[0].records[0].pocket=="01"); #expect(db.lessons[0].records[0].state == .submitted); #expect(db.changes.count==2)
    }
    @Test func conflictsAreAtomicAndClassesIndependent() throws {
        var db=Archive(); try db.importStudents([student()]); let old=db.students
        #expect(throws:(any Error).self) { try db.importStudents([student("00102","李四","1")]) }; #expect(db.students==old)
        try db.importStudents([student("00102","李四","01","示例二班")]); #expect(db.students.count==2)
        #expect(throws:(any Error).self) { try db.importStudents([student(),student()]) }
    }
    @Test func xlsxRoundTripAndLeadingZeros() throws {
        let data=Workbook.create([("名单",[["学号/工号","姓名","班级","袋号"],["00101","张三","示例一班","01"],["00102","李四","示例一班",""]])])
        let rows=try Workbook.students(data)
        #expect(rows[0].sid=="00101"); #expect(rows[0].pocket=="01"); #expect(rows[1].pocket.isEmpty)
    }
    @Test func exportCorrectionsAndLiteralFormulaText() throws {
        var db=Archive(); try db.importStudents([student()]); try db.save(lesson(.missing)); var corrected=lesson(.submitted); corrected.records[0].note="=1+1"; try db.save(corrected)
        let files=try ZipContainer.read(Workbook.export(students:db.students,lessons:db.lessons)); let summary=SheetXML(); try summary.parse(files["xl/worksheets/sheet1.xml"]!)
        #expect(summary.rows[1][1]=="00101"); #expect(summary.rows[1][3]=="1"); #expect(summary.rows[1][4]=="0")
        let detail=SheetXML(); try detail.parse(files["xl/worksheets/sheet2.xml"]!); #expect(detail.rows[1].last=="=1+1")
    }
    @Test func corruptionAndBadZipFail() throws {
        #expect(throws:(any Error).self) { try ZipContainer.read(Data("invalid".utf8)) }
        var data=ZipContainer.write([("a",Data("text".utf8))]); data[31] ^= 1
        #expect(throws:(any Error).self) { try ZipContainer.read(data) }
    }
    @Test func persistAndSpaceIsolation() throws {
        let root=URL.temporaryDirectory.appending(path:UUID().uuidString); defer { try? FileManager.default.removeItem(at:root) }
        let demo=ArchiveFile(url:root.appending(path:"demo/records.json")),official=ArchiveFile(url:root.appending(path:"official/records.json"))
        var db=Archive(); try db.importStudents([student()]); try db.save(lesson(.review)); try demo.save(db)
        #expect(try demo.load().lessons.count==1); #expect(try official.load().lessons.isEmpty)
        try Data("broken".utf8).write(to:demo.url); #expect(throws:(any Error).self) { try demo.load() }
    }
    @Test func suppliedMockCompressedWorkbook() throws {
        let base=URL(fileURLWithPath:#filePath).deletingLastPathComponent().deletingLastPathComponent()
        let rows=try Workbook.students(Data(contentsOf:base.appending(path:"Resources/roster.xlsx")))
        #expect(rows.count==6); #expect(rows[0].name=="张三"); #expect(rows[0].pocket=="01")
    }
}
