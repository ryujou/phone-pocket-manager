import Foundation
struct Lesson: Codable, Identifiable, Equatable, Sendable {
    var id: UUID = UUID()
    var className: String
    var term: String
    var day: String
    var period: String
    var photo: String
    var calibration: [Double] = []
    var records: [Record]
    var key: String { [className, term, day, period].joined(separator: "\u{1F}") }
}
