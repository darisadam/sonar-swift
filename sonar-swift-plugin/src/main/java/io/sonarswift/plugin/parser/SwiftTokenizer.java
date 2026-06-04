package io.sonarswift.plugin.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A simple regex-driven Swift tokenizer. Good enough to power metrics
 * (LOC, complexity), CPD, and many token-based checks. The native
 * SwiftSyntax-backed AST is used for deeper checks.
 *
 * <p>Not a full Swift parser. Specifically does not handle:</p>
 * <ul>
 *   <li>String interpolation expressions (treats whole {@code "...\(x)..."} as one string)</li>
 *   <li>Multi-line strings opening on same line as closing tokens (rare)</li>
 *   <li>Custom operators introduced via {@code operator} declarations</li>
 * </ul>
 *
 * <p>These limitations affect a tiny minority of files. For everything else,
 * output is correct enough for token-based analysis.</p>
 */
public final class SwiftTokenizer {

    private SwiftTokenizer() {}

    private static final Set<String> KEYWORDS = Set.of(
            // declarations
            "associatedtype", "class", "deinit", "enum", "extension", "fileprivate",
            "func", "import", "init", "inout", "internal", "let", "open", "operator",
            "private", "protocol", "public", "rethrows", "static", "struct", "subscript",
            "typealias", "var", "actor", "macro",
            // statements
            "break", "case", "continue", "default", "defer", "do", "else", "fallthrough",
            "for", "guard", "if", "in", "repeat", "return", "switch", "throw", "where",
            "while",
            // expressions and types
            "Any", "as", "catch", "false", "is", "nil", "self", "Self", "super", "throws",
            "true", "try", "Type", "Protocol",
            // concurrency
            "async", "await", "isolated", "nonisolated",
            // pattern matching / new keywords
            "some", "any", "consume", "consuming", "discard", "borrowing"
    );

    public static List<Token> tokenize(String source) {
        List<Token> out = new ArrayList<>();
        int len = source.length();
        int line = 1;
        int col = 1;

        for (int i = 0; i < len;) {
            char c = source.charAt(i);

            if (c == '\n') { i++; line++; col = 1; continue; }
            if (Character.isWhitespace(c)) { i++; col++; continue; }

            // Comments
            if (c == '/' && i + 1 < len) {
                char nxt = source.charAt(i + 1);
                if (nxt == '/') {
                    int start = i;
                    boolean doc = i + 2 < len && source.charAt(i + 2) == '/';
                    while (i < len && source.charAt(i) != '\n') i++;
                    out.add(new Token(
                            doc ? Token.Kind.DOC_COMMENT : Token.Kind.LINE_COMMENT,
                            source.substring(start, i),
                            line, col,
                            line, col + (i - start)));
                    col += (i - start);
                    continue;
                }
                if (nxt == '*') {
                    int startLine = line, startCol = col;
                    int start = i;
                    boolean doc = i + 2 < len && source.charAt(i + 2) == '*';
                    i += 2; col += 2;
                    while (i + 1 < len && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                        if (source.charAt(i) == '\n') { line++; col = 1; } else col++;
                        i++;
                    }
                    if (i + 1 < len) { i += 2; col += 2; }
                    out.add(new Token(
                            doc ? Token.Kind.DOC_COMMENT : Token.Kind.BLOCK_COMMENT,
                            source.substring(start, i),
                            startLine, startCol, line, col));
                    continue;
                }
            }

            // String literals
            if (c == '"') {
                int startLine = line, startCol = col;
                int start = i;
                // Multi-line: """
                if (i + 2 < len && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                    i += 3; col += 3;
                    while (i + 2 < len && !(source.charAt(i) == '"'
                            && source.charAt(i + 1) == '"'
                            && source.charAt(i + 2) == '"')) {
                        if (source.charAt(i) == '\n') { line++; col = 1; } else col++;
                        i++;
                    }
                    if (i + 2 < len) { i += 3; col += 3; }
                } else {
                    i++; col++;
                    while (i < len && source.charAt(i) != '"' && source.charAt(i) != '\n') {
                        if (source.charAt(i) == '\\' && i + 1 < len) { i += 2; col += 2; }
                        else { i++; col++; }
                    }
                    if (i < len && source.charAt(i) == '"') { i++; col++; }
                }
                out.add(new Token(
                        Token.Kind.STRING_LITERAL,
                        source.substring(start, i),
                        startLine, startCol, line, col));
                continue;
            }

            // Directives starting with #
            if (c == '#' && i + 1 < len && (Character.isLetter(source.charAt(i + 1)) || source.charAt(i + 1) == '_')) {
                int start = i;
                int startCol = col;
                i++; col++;
                while (i < len && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_')) {
                    i++; col++;
                }
                out.add(new Token(Token.Kind.DIRECTIVE,
                        source.substring(start, i), line, startCol, line, col));
                continue;
            }

            // Attributes @available, @objc, ...
            if (c == '@' && i + 1 < len && (Character.isLetter(source.charAt(i + 1)) || source.charAt(i + 1) == '_')) {
                int start = i;
                int startCol = col;
                i++; col++;
                while (i < len && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_')) {
                    i++; col++;
                }
                out.add(new Token(Token.Kind.ATTRIBUTE,
                        source.substring(start, i), line, startCol, line, col));
                continue;
            }

            // Identifiers + keywords
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                int startCol = col;
                while (i < len && (Character.isLetterOrDigit(source.charAt(i))
                        || source.charAt(i) == '_')) {
                    i++; col++;
                }
                String text = source.substring(start, i);
                Token.Kind k;
                if ("true".equals(text) || "false".equals(text)) k = Token.Kind.BOOL_LITERAL;
                else if ("nil".equals(text)) k = Token.Kind.NIL_LITERAL;
                else if (KEYWORDS.contains(text)) k = Token.Kind.KEYWORD;
                else k = Token.Kind.IDENT;
                out.add(new Token(k, text, line, startCol, line, col));
                continue;
            }

            // Numbers
            if (Character.isDigit(c)) {
                int start = i;
                int startCol = col;
                boolean isFloat = false;
                while (i < len && (Character.isLetterOrDigit(source.charAt(i))
                        || source.charAt(i) == '_'
                        || source.charAt(i) == '.'
                        || source.charAt(i) == 'x' || source.charAt(i) == 'X'
                        || source.charAt(i) == 'o' || source.charAt(i) == 'O'
                        || source.charAt(i) == 'b' || source.charAt(i) == 'B')) {
                    if (source.charAt(i) == '.') isFloat = true;
                    i++; col++;
                }
                out.add(new Token(
                        isFloat ? Token.Kind.FLOAT_LITERAL : Token.Kind.INT_LITERAL,
                        source.substring(start, i),
                        line, startCol, line, col));
                continue;
            }

            // Punctuation
            if ("{}[]()<>,;:?".indexOf(c) >= 0) {
                out.add(new Token(Token.Kind.PUNCT, String.valueOf(c), line, col, line, col + 1));
                i++; col++;
                continue;
            }

            // Operators: greedily consume run of operator characters
            if ("+-*/%=&|^~!<>?.".indexOf(c) >= 0) {
                int start = i;
                int startCol = col;
                while (i < len && "+-*/%=&|^~!<>?.".indexOf(source.charAt(i)) >= 0) {
                    i++; col++;
                }
                out.add(new Token(Token.Kind.OPERATOR,
                        source.substring(start, i), line, startCol, line, col));
                continue;
            }

            // unknown — skip
            out.add(new Token(Token.Kind.OTHER, String.valueOf(c), line, col, line, col + 1));
            i++; col++;
        }
        return out;
    }
}
