import Foundation
struct ChangeLog: Codable, Sendable {
    var date: Date = Date()
    var lessonID: UUID
    var before: [Record]
    var after: [Record]
}
