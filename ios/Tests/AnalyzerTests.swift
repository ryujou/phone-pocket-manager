import Foundation
import Testing
@testable import PocketCore
struct AnalyzerTests {
    @Test func geometricFixtureMatchesKnownPockets() throws {
        let root=URL(fileURLWithPath:#filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let result=try PhoneAnalyzer.analyze(Data(contentsOf:root.appending(path:"desktop/samples/sample_1.jpg")))
        #expect(result.states.count==54)
        #expect(result.states[0] == .submitted)
        #expect(result.states[7] == .missing)
        #expect(result.states[12] == .missing)
        #expect(result.states[49] == .submitted)
        #expect(!result.clipped)
    }
    @Test func clippedPhotoRequiresReview() throws {
        let root=URL(fileURLWithPath:#filePath).deletingLastPathComponent().deletingLastPathComponent()
        let result=try PhoneAnalyzer.analyze(Data(contentsOf:root.appending(path:"Resources/example_edited.jpg")),corners:[CGPoint(x:0,y:0),CGPoint(x:1,y:0),CGPoint(x:1,y:1),CGPoint(x:0,y:1)])
        #expect(result.states.allSatisfy { $0 == .review })
    }
}
