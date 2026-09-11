import Foundation
struct Record: Codable, Identifiable, Equatable, Sendable {
    var sid: String
    var name: String
    var pocket: String
    var prediction: SubmissionState
    var score: Double
    var state: SubmissionState
    var note: String = ""
    var id: String { sid }
}
