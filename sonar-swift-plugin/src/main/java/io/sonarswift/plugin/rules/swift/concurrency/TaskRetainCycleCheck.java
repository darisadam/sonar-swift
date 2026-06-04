package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;

/**
 * S1904 — `Task { ... }` body captures `self` strongly without a weak
 * capture list, which prevents the enclosing reference type from
 * being deallocated until the task finishes.
 *
 * <p>Heuristic on tokens: a `Task {` opener whose closure has no `[weak`
 * capture list and that references `self` somewhere within the closure body.
 * The closure body is matched by brace-balanced scanning from the opening
 * `{` to the matching `}`.</p>
 */
public class TaskRetainCycleCheck extends SwiftCheck {

    public TaskRetainCycleCheck() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1904";
    }

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        for (int i = 0; i + 2 < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.kind() != Token.Kind.IDENT || !"Task".equals(t.text())) continue;
            // Skip if used as a type, e.g. `let t: Task<…,…>`
            if (i + 1 < tokens.size() && "<".equals(tokens.get(i + 1).text())) continue;

            // The next token must be `{` (trailing-closure form) or `(`
            int closureStart = -1;
            if (i + 1 < tokens.size() && "{".equals(tokens.get(i + 1).text())) {
                closureStart = i + 1;
            } else if (i + 1 < tokens.size() && "(".equals(tokens.get(i + 1).text())) {
                int parenEnd = matchParen(tokens, i + 1);
                if (parenEnd > 0 && parenEnd + 1 < tokens.size()
                        && "{".equals(tokens.get(parenEnd + 1).text())) {
                    closureStart = parenEnd + 1;
                }
            }
            if (closureStart < 0) continue;

            int closureEnd = matchBrace(tokens, closureStart);
            if (closureEnd < 0) continue;

            // Read capture list, if any
            boolean hasWeakCapture = hasWeakSelfCapture(tokens, closureStart, closureEnd);
            if (hasWeakCapture) continue;

            // Check for any unqualified `self` reference inside the closure
            if (referencesSelfStrongly(tokens, closureStart, closureEnd)) {
                reportIssue(ctx, file, t,
                        "`Task { ... }` captures `self` strongly. Use `[weak self]` and guard the "
                        + "captured reference unless the surrounding instance's lifetime is "
                        + "intentionally extended.");
            }
        }
    }

    private boolean hasWeakSelfCapture(List<Token> tokens, int braceOpen, int braceClose) {
        if (braceOpen + 1 >= tokens.size()) return false;
        if (!"[".equals(tokens.get(braceOpen + 1).text())) return false;
        // Walk the capture list `[ ... ]`
        for (int j = braceOpen + 1; j < braceClose; j++) {
            String text = tokens.get(j).text();
            if ("]".equals(text)) return false;
            if ("weak".equals(text)) {
                if (j + 1 < braceClose && "self".equals(tokens.get(j + 1).text())) {
                    return true;
                }
            }
            if ("unowned".equals(text)) {
                // unowned counts as not retained for our purposes
                if (j + 1 < braceClose && "self".equals(tokens.get(j + 1).text())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean referencesSelfStrongly(List<Token> tokens, int braceOpen, int braceClose) {
        for (int j = braceOpen + 1; j < braceClose; j++) {
            Token p = tokens.get(j);
            if (p.kind() == Token.Kind.KEYWORD && "self".equals(p.text())) return true;
            if (p.kind() == Token.Kind.IDENT && "self".equals(p.text())) return true;
        }
        return false;
    }

    private int matchBrace(List<Token> tokens, int openIdx) {
        int depth = 0;
        for (int j = openIdx; j < tokens.size(); j++) {
            String text = tokens.get(j).text();
            if ("{".equals(text)) depth++;
            else if ("}".equals(text)) {
                depth--;
                if (depth == 0) return j;
            }
        }
        return -1;
    }

    private int matchParen(List<Token> tokens, int openIdx) {
        int depth = 0;
        for (int j = openIdx; j < tokens.size(); j++) {
            String text = tokens.get(j).text();
            if ("(".equals(text)) depth++;
            else if (")".equals(text)) {
                depth--;
                if (depth == 0) return j;
            }
        }
        return -1;
    }
}
