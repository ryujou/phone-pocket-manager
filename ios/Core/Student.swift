import Foundation
struct Student: Codable, Identifiable, Equatable, Sendable {
    var sid: String
    var name: String
    var className: String
    var pocket: String
    var sourceClass: String
    var id: String { className + "|" + sid }
}
