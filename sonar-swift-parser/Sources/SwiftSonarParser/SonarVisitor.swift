import Foundation
import SwiftSyntax

/// Walks a SwiftSyntax tree and emits a compact JSON representation that
/// matches the Java side's `SwiftAst` POJO hierarchy. We deliberately do NOT
/// emit the entire tree — only nodes our checks care about.
final class SonarVisitor: SyntaxVisitor {

    private let source: String

    /// Each entry is a dict matching one of the `SwiftAst` polymorphic types
    /// on the Java side. The Java client uses Jackson with `@JsonTypeInfo(name)`
    /// to materialize.
    private(set) var nodes: [[String: Any]] = []

    /// Token stream for CPD + LOC. Each token: `{kind, text, line, col, endLine, endCol}`.
    private(set) var tokens: [[String: Any]] = []

    init(viewMode: SyntaxTreeViewMode, source: String) {
        self.source = source
        super.init(viewMode: viewMode)
    }

    // ---- declarations ----

    override func visit(_ node: ClassDeclSyntax) -> SyntaxVisitorContinueKind {
        let inheritance = node.inheritanceClause?.inheritedTypes.map { "\($0.type)" } ?? []
        let modifiers = node.modifiers.map { $0.name.text }
        let attributes = node.attributes.map { Self.normalizeAttribute("\($0)") }
        nodes.append([
            "kind": "ClassDecl",
            "name": node.name.text,
            "modifiers": modifiers,
            "inheritance": inheritance,
            "attributes": attributes,
            "isFinal": modifiers.contains("final"),
            "isSendable": inheritance.contains(where: { $0.contains("Sendable") }),
            "isUncheckedSendable": attributes.contains(where: { $0.contains("unchecked") })
                || inheritance.contains(where: { $0.contains("@unchecked") && $0.contains("Sendable") }),
            "actorIsolation": Self.actorIsolation(from: attributes) as Any,
            "range": Range.of(node).toDict()
        ])
        return .visitChildren
    }

    override func visit(_ node: StructDeclSyntax) -> SyntaxVisitorContinueKind {
        let inheritance = node.inheritanceClause?.inheritedTypes.map { "\($0.type)" } ?? []
        let modifiers = node.modifiers.map { $0.name.text }
        let attributes = node.attributes.map { Self.normalizeAttribute("\($0)") }
        nodes.append([
            "kind": "StructDecl",
            "name": node.name.text,
            "modifiers": modifiers,
            "inheritance": inheritance,
            "attributes": attributes,
            "isSendable": inheritance.contains(where: { $0.contains("Sendable") }),
            "actorIsolation": Self.actorIsolation(from: attributes) as Any,
            "range": Range.of(node).toDict()
        ])
        return .visitChildren
    }

    override func visit(_ node: EnumDeclSyntax) -> SyntaxVisitorContinueKind {
        nodes.append([
            "kind": "EnumDecl",
            "name": node.name.text,
            "modifiers": node.modifiers.map { $0.name.text },
            "range": Range.of(node).toDict()
        ])
        return .visitChildren
    }

    override func visit(_ node: ProtocolDeclSyntax) -> SyntaxVisitorContinueKind {
        nodes.append([
            "kind": "ProtocolDecl",
            "name": node.name.text,
            "modifiers": node.modifiers.map { $0.name.text },
            "range": Range.of(node).toDict()
        ])
        return .visitChildren
    }

    override func visit(_ node: ActorDeclSyntax) -> SyntaxVisitorContinueKind {
        nodes.append([
            "kind": "ActorDecl",
            "name": node.name.text,
            "modifiers": node.modifiers.map { $0.name.text },
            "range": Range.of(node).toDict()
        ])
        return .visitChildren
    }

    override func visit(_ node: ExtensionDeclSyntax) -> SyntaxVisitorContinueKind {
        nodes.append([
            "kind": "ExtensionDecl",
            "extendedType": "\(node.extendedType)",
            "range": Range.of(node).toDict()
        ])
        return .visitChildren
    }

    override func visit(_ node: FunctionDeclSyntax) -> SyntaxVisitorContinueKind {
        let params = node.signature.parameterClause.parameters.map { $0.firstName.text }
        let returnType = node.signature.returnClause.map { "\($0.type)" } ?? ""
        let modifiers = node.modifiers.map { $0.name.text }
        let attributes = node.attributes.map { Self.normalizeAttribute("\($0)") }
        nodes.append([
            "kind": "FuncDecl",
            "name": node.name.text,
            "modifiers": modifiers,
            "attributes": attributes,
            "paramNames": params,
            "returnType": returnType,
            "isAsync": node.signature.effectSpecifiers?.asyncSpecifier != nil,
            "isThrows": node.signature.effectSpecifiers?.throwsClause != nil,
            "actorIsolation": Self.actorIsolation(modifiers: modifiers, attributes: attributes) as Any,
            "range": Range.of(node).toDict()
        ])
        return .visitChildren
    }

    override func visit(_ node: InitializerDeclSyntax) -> SyntaxVisitorContinueKind {
        nodes.append([
            "kind": "InitDecl",
            "modifiers": node.modifiers.map { $0.name.text },
            "paramNames": node.signature.parameterClause.parameters.map { $0.firstName.text },
            "isFailable": node.optionalMark != nil,
            "range": Range.of(node).toDict()
        ])
        return .visitChildren
    }

    // ---- expressions of interest ----

    override func visit(_ node: ForceUnwrapExprSyntax) -> SyntaxVisitorContinueKind {
        nodes.append([
            "kind": "ForceUnwrapExpr",
            "range": Range.of(node).toDict()
        ])
        return .visitChildren
    }

    override func visit(_ node: AsExprSyntax) -> SyntaxVisitorContinueKind {
        if node.questionOrExclamationMark?.tokenKind == .exclamationMark {
            nodes.append([
                "kind": "ForceCastExpr",
                "range": Range.of(node).toDict()
            ])
        }
        return .visitChildren
    }

    override func visit(_ node: TryExprSyntax) -> SyntaxVisitorContinueKind {
        if node.questionOrExclamationMark?.tokenKind == .exclamationMark {
            nodes.append([
                "kind": "ForceTryExpr",
                "range": Range.of(node).toDict()
            ])
        }
        return .visitChildren
    }

    override func visit(_ node: FunctionCallExprSyntax) -> SyntaxVisitorContinueKind {
        let callee = "\(node.calledExpression)".trimmingCharacters(in: .whitespacesAndNewlines)

        // Special case: Task { ... } / Task(priority:) { ... } / Task.detached { ... }
        if callee == "Task" || callee.hasPrefix("Task.detached") {
            let isDetached = callee.hasPrefix("Task.detached")
            let trailing = node.trailingClosure
            let captures = trailing?.signature?.capture?.items.map { "\($0)" } ?? []
            let capturesSelfStrongly: Bool = {
                guard let trailing else { return false }
                let body = "\(trailing.statements)"
                let hasSelf = body.contains("self")
                let hasWeakOrUnowned = captures.contains(where: {
                    $0.contains("weak self") || $0.contains("unowned self")
                })
                return hasSelf && !hasWeakOrUnowned
            }()
            nodes.append([
                "kind": "TaskExpr",
                "isDetached": isDetached,
                "captureList": captures,
                "capturesSelfStrongly": capturesSelfStrongly,
                "range": Range.of(node).toDict()
            ])
            return .visitChildren
        }

        // Detect `try await` / `await` callsites by walking up to two siblings.
        // SwiftSyntax represents `await someFunc()` as AwaitExprSyntax wrapping
        // the call. From the call's perspective we look at our parent.
        var isAwaited = false
        var isTryAwaited = false
        var parent = node.parent
        // Skip transparent wrappers (e.g. CodeBlockItemSyntax)
        while let p = parent {
            if p.is(AwaitExprSyntax.self) { isAwaited = true; break }
            if p.is(TryExprSyntax.self) {
                isTryAwaited = true
                parent = p.parent
                continue
            }
            // Stop searching at statement boundary
            if p.is(CodeBlockItemSyntax.self) || p.is(StmtSyntax.self) { break }
            parent = p.parent
        }
        nodes.append([
            "kind": "CallExpr",
            "calleeText": callee,
            "isAwaited": isAwaited,
            "isTryAwaited": isTryAwaited,
            "range": Range.of(node).toDict()
        ])
        return .visitChildren
    }

    override func visit(_ node: ClosureExprSyntax) -> SyntaxVisitorContinueKind {
        let captures = node.signature?.capture?.items.map { "\($0)" } ?? []
        let paramNames = node.signature?.parameterClause.flatMap {
            switch $0 {
            case .simpleInput(let s):
                return s.map { $0.name.text }
            case .parameterClause(let p):
                return p.parameters.map { $0.firstName.text }
            }
        } ?? []
        nodes.append([
            "kind": "ClosureExpr",
            "captureList": captures,
            "paramNames": paramNames,
            "isEscaping": false,
            "range": Range.of(node).toDict()
        ])
        return .visitChildren
    }

    // ---- concurrency helpers ----

    /// Reduce an attribute syntax to a comparable text form.
    /// E.g. `@MainActor` -> `@MainActor`, `@objc(SomeName)` -> `@objc`,
    /// `@available(...)` -> `@available`.
    static func normalizeAttribute(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let paren = trimmed.firstIndex(of: "(") else { return trimmed }
        return String(trimmed[..<paren])
    }

    /// Map attributes to an actor-isolation string. Returns `nil` if unconstrained.
    static func actorIsolation(from attributes: [String]) -> String? {
        if attributes.contains("@MainActor") { return "MainActor" }
        if let globalActor = attributes.first(where: {
            $0.hasPrefix("@") && $0.hasSuffix("Actor") && $0 != "@MainActor"
        }) {
            return String(globalActor.dropFirst())
        }
        return nil
    }

    /// Same but also takes modifiers (for `nonisolated`).
    static func actorIsolation(modifiers: [String], attributes: [String]) -> String? {
        if modifiers.contains(where: { $0 == "nonisolated" }) { return "nonisolated" }
        return actorIsolation(from: attributes)
    }

    // ---- token stream ----

    override func visit(_ token: TokenSyntax) -> SyntaxVisitorContinueKind {
        let (line, col, endLine, endCol) = Range.of(token).tuple()
        let kind: String = {
            switch token.tokenKind {
            case .identifier(let n) where !n.isEmpty: return "IDENT"
            case .integerLiteral: return "INT"
            case .floatLiteral: return "FLOAT"
            case .stringQuote, .stringSegment: return "STR_PART"
            case .keyword: return "KEYWORD"
            default: return "OTHER"
            }
        }()
        tokens.append([
            "kind": kind,
            "text": token.text,
            "line": line, "col": col,
            "endLine": endLine, "endCol": endCol
        ])
        return .visitChildren
    }
}

// ---- source-range helper ----

struct Range {
    let startLine: Int
    let startCol: Int
    let endLine: Int
    let endCol: Int

    static func of(_ node: some SyntaxProtocol) -> Range {
        let conv = SourceLocationConverter(fileName: "<>", tree: node.root)
        let start = conv.location(for: node.positionAfterSkippingLeadingTrivia)
        let end = conv.location(for: node.endPositionBeforeTrailingTrivia)
        return Range(
            startLine: start.line,
            startCol: start.column,
            endLine: end.line,
            endCol: end.column)
    }

    static func full(source: String) -> Range {
        let lines = source.components(separatedBy: "\n")
        return Range(startLine: 1, startCol: 1,
                     endLine: max(1, lines.count),
                     endCol: max(1, (lines.last?.count ?? 0) + 1))
    }

    func toDict() -> [String: Any] {
        return [
            "startLine": startLine, "startCol": startCol,
            "endLine": endLine, "endCol": endCol
        ]
    }

    func tuple() -> (Int, Int, Int, Int) {
        return (startLine, startCol, endLine, endCol)
    }
}
