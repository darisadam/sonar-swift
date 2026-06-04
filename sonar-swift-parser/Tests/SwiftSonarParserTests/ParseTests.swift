import XCTest
@testable import SwiftSonarParser

final class ParseTests: XCTestCase {

    func test_parses_an_empty_file() throws {
        let result = SwiftSonarParserCLI.parseToJson(source: "", path: "test.swift")
        XCTAssertEqual(result["kind"] as? String, "SourceFile")
        XCTAssertEqual(result["path"] as? String, "test.swift")
    }

    func test_finds_class_declaration() throws {
        let src = """
        public class Foo {
            func bar() {}
        }
        """
        let result = SwiftSonarParserCLI.parseToJson(source: src, path: "Foo.swift")
        let children = result["children"] as! [[String: Any]]
        XCTAssertTrue(children.contains { ($0["kind"] as? String) == "ClassDecl" })
        XCTAssertTrue(children.contains { ($0["kind"] as? String) == "FuncDecl" })
    }

    func test_finds_force_unwrap() throws {
        let src = "let x = optional!"
        let result = SwiftSonarParserCLI.parseToJson(source: src, path: "T.swift")
        let children = result["children"] as! [[String: Any]]
        XCTAssertTrue(children.contains { ($0["kind"] as? String) == "ForceUnwrapExpr" },
                      "expected ForceUnwrapExpr in \(children.map { $0["kind"] ?? "?" })")
    }
}
