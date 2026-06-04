import XCTest
@testable import SampleApp

final class LoginTests: XCTestCase {

    func test_summarize_outputs_displayname_when_present() {
        let formatter = ProfileFormatter()
        let p = Profile(id: "1", displayName: "Dora", avatarURL: nil)
        XCTAssertEqual(formatter.summarize(p), "Dora · no-avatar")
    }
}
