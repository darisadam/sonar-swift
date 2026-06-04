import Foundation
import SwiftParser
import SwiftSyntax

/// Entry point. Two modes:
///
/// * `--serve` — NDJSON request/response loop on stdin/stdout. The Java
///   plugin uses this for batched scanning.
/// * `parse <file>` — one-shot parse, prints the AST JSON, exits.
///   Useful for ad-hoc CLI usage.
///
/// The file is named `main.swift`, so top-level code at the bottom acts as
/// the entry point (we deliberately do NOT use `@main` — that's incompatible
/// with a file called `main.swift`).
enum SwiftSonarParserCLI {
    static func run() {
        let args = CommandLine.arguments
        if args.count >= 2 && args[1] == "--serve" {
            serve()
        } else if args.count >= 3 && args[1] == "parse" {
            oneshot(path: args[2])
        } else {
            FileHandle.standardError.write("Usage: SwiftSonarParser --serve | parse <file>\n".data(using: .utf8)!)
            exit(2)
        }
    }

    // ---- NDJSON server loop ----

    static func serve() {
        let stdin = FileHandle.standardInput
        let stdout = FileHandle.standardOutput

        while let line = readLine(strippingNewline: true) {
            guard !line.isEmpty else { continue }
            let response = handle(line: line)
            if let data = response.data(using: .utf8) {
                stdout.write(data)
            }
            if let nl = "\n".data(using: .utf8) {
                stdout.write(nl)
            }
        }
        _ = stdin   // keep referenced
    }

    static func handle(line: String) -> String {
        do {
            let req = try JSONDecoder().decode(Request.self, from: line.data(using: .utf8)!)
            switch req.op {
            case "ping":
                return success(reqId: req.reqId, data: ["pong": true])
            case "shutdown":
                exit(0)
            case "parse":
                let source = req.source ?? (try? String(contentsOfFile: req.path ?? "")) ?? ""
                let ast = parseToJson(source: source, path: req.path ?? "<inline>")
                Metrics.recordParse()
                return success(reqId: req.reqId, data: ast)
            case "metrics":
                return success(reqId: req.reqId, data: Metrics.snapshot())
            default:
                return failure(reqId: req.reqId, error: "unknown op: \(req.op)")
            }
        } catch {
            return "{\"ok\":false,\"error\":\"\(error.localizedDescription)\"}"
        }
    }

    // ---- one-shot mode ----

    static func oneshot(path: String) {
        guard let source = try? String(contentsOfFile: path) else {
            FileHandle.standardError.write("Cannot read \(path)\n".data(using: .utf8)!)
            exit(1)
        }
        let ast = parseToJson(source: source, path: path)
        let out = success(reqId: "1", data: ast)
        print(out)
    }

    // ---- parsing ----

    static func parseToJson(source: String, path: String) -> [String: Any] {
        let tree = Parser.parse(source: source)
        let visitor = SonarVisitor(viewMode: .all, source: source)
        visitor.walk(tree)
        return [
            "kind": "SourceFile",
            "path": path,
            "range": Range.full(source: source).toDict(),
            "children": visitor.nodes,
            "tokens": visitor.tokens
        ]
    }

    // ---- JSON helpers ----

    static func success(reqId: String, data: Any) -> String {
        let envelope: [String: Any] = ["reqId": reqId, "ok": true, "data": data]
        return (try? jsonString(envelope)) ?? "{\"ok\":false,\"error\":\"serialization\"}"
    }

    static func failure(reqId: String, error: String) -> String {
        let envelope: [String: Any] = ["reqId": reqId, "ok": false, "error": error]
        return (try? jsonString(envelope)) ?? "{\"ok\":false,\"error\":\"serialization\"}"
    }

    static func jsonString(_ obj: Any) throws -> String {
        let data = try JSONSerialization.data(
            withJSONObject: obj,
            options: [.fragmentsAllowed, .sortedKeys])
        return String(data: data, encoding: .utf8) ?? ""
    }
}

struct Request: Decodable {
    let reqId: String
    let op: String
    let path: String?
    let source: String?
}

// ---- top-level entry ----
// Because the file is `main.swift`, this top-level statement IS the entry.
SwiftSonarParserCLI.run()
