package io.sonarswift.plugin.parser;

/**
 * Lightweight source token. Produced by SwiftTokenizer / ClangTokenizer
 * for use by token-based checks (force-unwrap, print, TODO, MD5, …) and
 * by metric calculators that don't need a full AST.
 *
 * @param kind  what kind of token this is
 * @param text  the verbatim source text of the token
 * @param line  1-based line number where the token starts
 * @param col   1-based column where the token starts
 * @param endLine end line (same as line unless multi-line)
 * @param endCol end column
 */
public record Token(
        Kind kind,
        String text,
        int line,
        int col,
        int endLine,
        int endCol) {

    public enum Kind {
        /** A reserved word such as {@code func}, {@code class}, {@code if}. */
        KEYWORD,
        /** Identifier (variable, type, function name). */
        IDENT,
        /** Integer literal. */
        INT_LITERAL,
        /** Floating-point literal. */
        FLOAT_LITERAL,
        /** String literal (single or multi-line). */
        STRING_LITERAL,
        /** Character literal (Objective-C). */
        CHAR_LITERAL,
        /** {@code true} / {@code false} / {@code YES} / {@code NO}. */
        BOOL_LITERAL,
        /** {@code nil} or {@code NULL}. */
        NIL_LITERAL,
        /** Punctuation (parens, braces, commas, …). */
        PUNCT,
        /** Operator (`+`, `-`, `??`, `!`, …). */
        OPERATOR,
        /** Single-line comment ({@code //…}). */
        LINE_COMMENT,
        /** Block comment ({@code /* … *\/}). */
        BLOCK_COMMENT,
        /** Doc comment ({@code ///…} or {@code /** … *\/}). */
        DOC_COMMENT,
        /** Preprocessor directive (ObjC {@code #import}, Swift {@code #if}). */
        DIRECTIVE,
        /** Attribute / annotation: {@code @available}, {@code @objc}, … */
        ATTRIBUTE,
        /** Unknown / other. */
        OTHER
    }

    /** True if this token represents source code (not comment / directive). */
    public boolean isCode() {
        return switch (kind) {
            case LINE_COMMENT, BLOCK_COMMENT, DOC_COMMENT -> false;
            default -> true;
        };
    }

    public boolean isComment() {
        return kind == Kind.LINE_COMMENT
                || kind == Kind.BLOCK_COMMENT
                || kind == Kind.DOC_COMMENT;
    }
}
