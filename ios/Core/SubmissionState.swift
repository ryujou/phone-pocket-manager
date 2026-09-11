import Foundation
enum SubmissionState: String, Codable, CaseIterable, Identifiable, Sendable {
    case submitted = "已交", missing = "未交", leave = "请假", absent = "缺勤", exempt = "免交", review = "待复核"
    var id: String { rawValue }
}
