package io.sonarswift.plugin.rules.swift;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;

/**
 * S1001 — Force-unwrap of an optional.
 *
 * <p>Detects {@code foo!} pattern. Heuristic: a unary {@code !} operator that
 * appears immediately (no whitespace, no other operator chars) after an
 * identifier, a {@code ?}, a {@code ]}, or a {@code )}. Avoids false positives
 * on:</p>
 * <ul>
 *   <li>Type declarations like {@code var x: Foo!} — preceded by a type token</li>
 *   <li>Binary not-equal {@code !=}</li>
 *   <li>{@code !} as logical-not prefix ({@code !condition}) — the {@code !}
 *       comes before the operand and is followed by an identifier</li>
 * </ul>
 */
public class ForceUnwrapCheck extends SwiftCheck {

    public ForceUnwrapCheck() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1001";
    }

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.kind() != Token.Kind.OPERATOR || !"!".equals(t.text())) continue;

            // Must immediately follow an identifier / ) / ] / ?
            if (i == 0) continue;
            Token prev = tokens.get(i - 1);
            if (prev.endLine() != t.line() || prev.endCol() != t.col()) continue; // not glued

            boolean preceededByValueLike =
                    prev.kind() == Token.Kind.IDENT
                            || (prev.kind() == Token.Kind.PUNCT
                                && (")".equals(prev.text()) || "]".equals(prev.text())))
                            || (prev.kind() == Token.Kind.OPERATOR && "?".equals(prev.text()));
            if (!preceededByValueLike) continue;

            // Skip type-declaration sites: `var x: Foo!` — look back for ":" before identifier
            if (i >= 2 && prev.kind() == Token.Kind.IDENT) {
                Token preprev = tokens.get(i - 2);
                if (preprev.kind() == Token.Kind.PUNCT && ":".equals(preprev.text())) {
                    continue; // implicitly-unwrapped declaration, a different rule (S1002)
                }
            }

            // Suppress `is!`, `as!`, `try!` — those are dedicated rules
            if (prev.kind() == Token.Kind.KEYWORD
                    && ("as".equals(prev.text()) || "try".equals(prev.text()) || "is".equals(prev.text()))) {
                continue;
            }

            reportIssue(ctx, file, t,
                    "Avoid force-unwrapping optionals — use `if let`, `guard let`, or `??` instead.");
        }
    }
}
