package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;
import java.util.Set;

/**
 * S1900 — Concurrent code crosses an actor boundary with a non-{@code Sendable}
 * type.
 *
 * <p>Heuristic on tokens: detect {@code class} declarations marked with neither
 * {@code Sendable} nor {@code @unchecked Sendable} that contain {@code var}
 * stored properties of reference-type appearance, when the file ALSO contains
 * concurrency primitives (`actor`, `Task`, `async`, `await`, `MainActor`).
 *
 * <p>Token-only detection is intentionally conservative — we err on missed
 * positives rather than false positives. The richer AST visitor in the parser
 * sidecar fires the same rule with broader coverage when the parser is
 * enabled.</p>
 */
public class SendableViolationCheck extends SwiftCheck {

    public SendableViolationCheck() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1900";
    }

    private static final Set<String> CONCURRENCY_MARKERS = Set.of(
            "actor", "Task", "async", "await", "MainActor", "TaskGroup", "AsyncSequence");

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        if (!fileUsesConcurrency(tokens)) return;

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.kind() != Token.Kind.KEYWORD || !"class".equals(t.text())) continue;

            // class declarations only — skip `class func` (legacy ObjC-style)
            if (i + 1 < tokens.size() && "func".equals(tokens.get(i + 1).text())) continue;

            // Look back: do we already see "final" or do we already see "Sendable"
            // somewhere up to ~15 tokens behind us? (covers attributes line)
            if (isAlreadyMarked(tokens, i)) continue;

            // Look forward to confirm: does this class have a mutable var stored
            // property? If not, no point flagging.
            if (!hasMutableStoredProperty(tokens, i)) continue;

            reportIssue(ctx, file, t,
                    "This `class` is used across concurrency boundaries but is not declared "
                    + "`Sendable`. Mark it `final` and conform to `Sendable`, or wrap it in an `actor`.");
        }
    }

    private boolean fileUsesConcurrency(List<Token> tokens) {
        for (Token t : tokens) {
            if (CONCURRENCY_MARKERS.contains(t.text())) return true;
        }
        return false;
    }

    private boolean isAlreadyMarked(List<Token> tokens, int classIdx) {
        int scanStart = Math.max(0, classIdx - 30);
        for (int j = scanStart; j < classIdx; j++) {
            String text = tokens.get(j).text();
            if ("Sendable".equals(text)) return true;
        }
        // Also scan the inheritance clause: `class Foo: Sendable {`
        // Find the `{` and scan tokens between classIdx and the brace.
        int brace = findOpenBrace(tokens, classIdx);
        if (brace < 0) return false;
        for (int j = classIdx; j < brace; j++) {
            if ("Sendable".equals(tokens.get(j).text())) return true;
        }
        return false;
    }

    private boolean hasMutableStoredProperty(List<Token> tokens, int classIdx) {
        int brace = findOpenBrace(tokens, classIdx);
        if (brace < 0) return false;
        int depth = 0;
        for (int j = brace; j < tokens.size(); j++) {
            String text = tokens.get(j).text();
            if ("{".equals(text)) depth++;
            else if ("}".equals(text)) {
                depth--;
                if (depth == 0) return false;
            } else if (depth == 1 && "var".equals(text)
                    && tokens.get(j).kind() == Token.Kind.KEYWORD) {
                return true;
            }
        }
        return false;
    }

    private int findOpenBrace(List<Token> tokens, int from) {
        for (int j = from; j < tokens.size(); j++) {
            if ("{".equals(tokens.get(j).text())) return j;
        }
        return -1;
    }
}
