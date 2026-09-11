import Foundation
enum PocketError: LocalizedError { case message(String)
    var errorDescription: String? { if case .message(let text) = self { text } else { nil } }
}
