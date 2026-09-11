import Foundation
struct PocketAnalysis: Sendable {
    var image: Data
    var corners: [CGPoint]
    var rows: [Int]
    var states: [SubmissionState]
    var scores: [Double]
    var clipped: Bool
}
