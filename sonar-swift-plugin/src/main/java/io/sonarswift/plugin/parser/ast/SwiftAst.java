package io.sonarswift.plugin.parser.ast;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Lightweight AST representation. Mirrors the JSON emitted by the
 * SwiftSonarParser companion process. Not a full SwiftSyntax tree —
 * only the nodes our checks actually traverse.
 *
 * <p>Adding a new node type:</p>
 * <ol>
 *   <li>Add a record/class below, annotated for Jackson polymorphism</li>
 *   <li>Add it to {@code @JsonSubTypes}</li>
 *   <li>Emit matching JSON from {@code SwiftSonarParser/Parser.swift}</li>
 *   <li>Bump the parser-protocol version constant</li>
 * </ol>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SwiftAst.SourceFile.class, name = "SourceFile"),
        @JsonSubTypes.Type(value = SwiftAst.ClassDecl.class, name = "ClassDecl"),
        @JsonSubTypes.Type(value = SwiftAst.StructDecl.class, name = "StructDecl"),
        @JsonSubTypes.Type(value = SwiftAst.EnumDecl.class, name = "EnumDecl"),
        @JsonSubTypes.Type(value = SwiftAst.ProtocolDecl.class, name = "ProtocolDecl"),
        @JsonSubTypes.Type(value = SwiftAst.ActorDecl.class, name = "ActorDecl"),
        @JsonSubTypes.Type(value = SwiftAst.ExtensionDecl.class, name = "ExtensionDecl"),
        @JsonSubTypes.Type(value = SwiftAst.FuncDecl.class, name = "FuncDecl"),
        @JsonSubTypes.Type(value = SwiftAst.InitDecl.class, name = "InitDecl"),
        @JsonSubTypes.Type(value = SwiftAst.VarDecl.class, name = "VarDecl"),
        @JsonSubTypes.Type(value = SwiftAst.IfStmt.class, name = "IfStmt"),
        @JsonSubTypes.Type(value = SwiftAst.ForStmt.class, name = "ForStmt"),
        @JsonSubTypes.Type(value = SwiftAst.WhileStmt.class, name = "WhileStmt"),
        @JsonSubTypes.Type(value = SwiftAst.SwitchStmt.class, name = "SwitchStmt"),
        @JsonSubTypes.Type(value = SwiftAst.GuardStmt.class, name = "GuardStmt"),
        @JsonSubTypes.Type(value = SwiftAst.DoCatchStmt.class, name = "DoCatchStmt"),
        @JsonSubTypes.Type(value = SwiftAst.ClosureExpr.class, name = "ClosureExpr"),
        @JsonSubTypes.Type(value = SwiftAst.ForceUnwrapExpr.class, name = "ForceUnwrapExpr"),
        @JsonSubTypes.Type(value = SwiftAst.ForceCastExpr.class, name = "ForceCastExpr"),
        @JsonSubTypes.Type(value = SwiftAst.ForceTryExpr.class, name = "ForceTryExpr"),
        @JsonSubTypes.Type(value = SwiftAst.CallExpr.class, name = "CallExpr"),
        @JsonSubTypes.Type(value = SwiftAst.TaskExpr.class, name = "TaskExpr"),
        @JsonSubTypes.Type(value = SwiftAst.MemberAccessExpr.class, name = "MemberAccessExpr"),
        @JsonSubTypes.Type(value = SwiftAst.StringLiteralExpr.class, name = "StringLiteralExpr"),
        @JsonSubTypes.Type(value = SwiftAst.OtherNode.class, name = "Other"),
})
public abstract class SwiftAst {

    /** Source range — 1-based line/col, end exclusive. */
    public Range range;

    public List<SwiftAst> children = List.of();

    public record Range(
            @JsonProperty("startLine") int startLine,
            @JsonProperty("startCol") int startCol,
            @JsonProperty("endLine") int endLine,
            @JsonProperty("endCol") int endCol) {}

    // ---- declarations ----

    public static final class SourceFile extends SwiftAst {
        public String path;
    }

    public static final class ClassDecl extends SwiftAst {
        public String name;
        public List<String> modifiers = List.of();
        public List<String> inheritance = List.of();
        public List<String> attributes = List.of();   // @MainActor, @objc, ...
        public boolean isFinal;
        public boolean isSendable;
        public boolean isUncheckedSendable;
        public String actorIsolation;                  // null | "MainActor" | "<CustomGlobalActor>"
    }

    public static final class StructDecl extends SwiftAst {
        public String name;
        public List<String> modifiers = List.of();
        public List<String> inheritance = List.of();
        public List<String> attributes = List.of();
        public boolean isSendable;
        public String actorIsolation;
    }

    public static final class EnumDecl extends SwiftAst {
        public String name;
        public List<String> modifiers = List.of();
        public List<String> cases = List.of();
    }

    public static final class ProtocolDecl extends SwiftAst {
        public String name;
        public List<String> modifiers = List.of();
    }

    public static final class ActorDecl extends SwiftAst {
        public String name;
        public List<String> modifiers = List.of();
    }

    public static final class ExtensionDecl extends SwiftAst {
        public String extendedType;
        public List<String> conformsTo = List.of();
    }

    public static final class FuncDecl extends SwiftAst {
        public String name;
        public List<String> modifiers = List.of();
        public List<String> attributes = List.of();
        public List<String> paramNames = List.of();
        public String returnType;
        public boolean isAsync;
        public boolean isThrows;
        public String actorIsolation;   // null | "MainActor" | "<CustomGlobalActor>" | "nonisolated" | "nonisolatedUnsafe"
    }

    public static final class InitDecl extends SwiftAst {
        public List<String> modifiers = List.of();
        public List<String> paramNames = List.of();
        public boolean isFailable;
    }

    public static final class VarDecl extends SwiftAst {
        public String name;
        public String typeAnnotation;
        public List<String> modifiers = List.of();
        public boolean isLet;
        public boolean hasInitializer;
    }

    // ---- statements ----

    public static final class IfStmt extends SwiftAst {}
    public static final class ForStmt extends SwiftAst {}
    public static final class WhileStmt extends SwiftAst {}
    public static final class SwitchStmt extends SwiftAst { public int caseCount; }
    public static final class GuardStmt extends SwiftAst {}
    public static final class DoCatchStmt extends SwiftAst { public int catchCount; }

    // ---- expressions ----

    public static final class ClosureExpr extends SwiftAst {
        public List<String> captureList = List.of();   // raw text per capture: "weak self", "unowned x"
        public List<String> paramNames = List.of();
        public boolean isEscaping;                      // best-effort: declared @escaping
    }

    public static final class ForceUnwrapExpr extends SwiftAst {}
    public static final class ForceCastExpr extends SwiftAst {}
    public static final class ForceTryExpr extends SwiftAst {}

    public static final class CallExpr extends SwiftAst {
        public String calleeText;       // e.g. "print", "CC_MD5", "Insecure.MD5.hash"
        public List<String> argLabels = List.of();
        public List<String> argText = List.of();
        public boolean isAwaited;       // call site is preceded by `await`
        public boolean isTryAwaited;    // try await
    }

    /** Task initializer captured separately to support retain-cycle and detached-task checks. */
    public static final class TaskExpr extends SwiftAst {
        public boolean isDetached;
        public List<String> captureList = List.of();
        public boolean capturesSelfStrongly;
    }

    public static final class MemberAccessExpr extends SwiftAst {
        public String baseText;
        public String memberName;
    }

    public static final class StringLiteralExpr extends SwiftAst {
        public String value;            // resolved string (without quotes)
        public boolean isMultiline;
        public boolean hasInterpolation;
    }

    /** Catch-all for nodes the parser emits but checks don't deeply inspect. */
    public static final class OtherNode extends SwiftAst {
        public String kindHint;
    }
}
