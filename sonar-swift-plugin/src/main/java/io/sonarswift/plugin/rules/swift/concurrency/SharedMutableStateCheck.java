package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;

/**
 * S1907 — `static var` declared at top-level or inside a non-actor type is a
 * data-race waiting to happen in concurrent code.
 *
 * <p>Detection: any `static var <name>` declaration where the enclosing scope
 * is NOT an `actor`. We skip `static let` because Swift guarantees the
 * initializer runs atomically and the binding is immutable.</p>
 */
public class SharedMutableStateCheck extends SwiftCheck {

    public SharedMutableStateCheck() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1907";
    }

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        for (int i = 0; i + 2 < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.kind() != Token.Kind.KEYWORD || !"static".equals(t.text())) continue;
            Token next = tokens.get(i + 1);
            if (next.kind() != Token.Kind.KEYWORD || !"var".equals(next.text())) continue;

            // Skip static var declared inside an actor — that's fine.
            if (insideActorBody(tokens, i)) continue;

            // Skip declarations marked nonisolated(unsafe) — the dev explicitly
            // told the compiler "I know what I'm doing"
            if (precededByNonisolatedUnsafe(tokens, i)) continue;

            reportIssue(ctx, file, t,
                    "`static var` outside an `actor` is unsafe under concurrency. Make it `let`, "
                    + "move it into an `actor`, or annotate `nonisolated(unsafe)` and document the "
                    + "synchronization scheme.");
        }
    }

    private boolean insideActorBody(List<Token> tokens, int idx) {
        // Walk back: find the closest enclosing declaration keyword
        // (`actor`, `class`, `struct`, `enum`, `extension`). If `actor`, return true.
        int depth = 0;
        for (int j = idx - 1; j >= 0; j--) {
            Token p = tokens.get(j);
            String text = p.text();
            if ("}".equals(text)) depth++;
            else if ("{".equals(text)) {
                if (depth == 0) {
                    // First unmatched `{` — look back for the declaration keyword
                    for (int k = j - 1; k >= 0; k--) {
                        Token d = tokens.get(k);
                        if (d.kind() != Token.Kind.KEYWORD) continue;
                        if ("actor".equals(d.text())) return true;
                        if ("class".equals(d.text())
                                || "struct".equals(d.text())
                                || "enum".equals(d.text())
                                || "extension".equals(d.text())) {
                            return false;
                        }
                    }
                    return false;
                }
                depth--;
            }
        }
        return false;
    }

    private boolean precededByNonisolatedUnsafe(List<Token> tokens, int staticIdx) {
        // Look back up to 6 tokens for `nonisolated(unsafe)`
        int start = Math.max(0, staticIdx - 6);
        for (int j = start; j < staticIdx; j++) {
            if ("nonisolated".equals(tokens.get(j).text())) {
                // followed by `(unsafe)`?
                if (j + 3 < tokens.size()
                        && "(".equals(tokens.get(j + 1).text())
                        && "unsafe".equals(tokens.get(j + 2).text())
                        && ")".equals(tokens.get(j + 3).text())) {
                    return true;
                }
            }
        }
        return false;
    }
}
