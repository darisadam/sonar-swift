package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.parser.ast.SwiftAst;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;

/**
 * S1902 — Direct access to a `var` declared inside an `actor` outside of an
 * `await` expression.
 *
 * <p>Token-based detection: scans for `actor X { ... }` blocks and inspects how
 * the actor's stored properties are referenced from outside. AST mode (parser
 * sidecar) does this more accurately.</p>
 *
 * <p>Heuristic: if we see `someInstance.someProperty = ...` where `someInstance`
 * is the receiver of an `actor`-typed binding and the line is not preceded by
 * `await`, raise the issue.</p>
 */
public class ActorIsolationViolationCheck extends SwiftCheck {

    public ActorIsolationViolationCheck() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1902";
    }

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        // We scan only for the simple pattern `someActor.foo` immediately
        // followed by `=` (assignment), where the closest preceding non-whitespace
        // token sequence does not include `await`. This is intentionally narrow
        // — AST mode covers the broader cases.
        for (int i = 0; i + 3 < tokens.size(); i++) {
            Token t1 = tokens.get(i);
            Token t2 = tokens.get(i + 1);
            Token t3 = tokens.get(i + 2);
            Token t4 = tokens.get(i + 3);
            if (t1.kind() != Token.Kind.IDENT) continue;
            if (!".".equals(t2.text())) continue;
            if (t3.kind() != Token.Kind.IDENT) continue;
            if (!"=".equals(t4.text())) continue;

            // Lookback: was the assignment expression prefixed by `await`?
            if (precededByAwaitOnSameLine(tokens, i)) continue;

            // Only fire if we can find an `actor` declaration in this file —
            // not perfect, but avoids firing on completely sync code.
            if (!fileDeclaresAnActor(tokens)) continue;

            reportIssue(ctx, file, t2,
                    "Mutating an actor-isolated property without `await` causes a compile error in "
                    + "strict concurrency mode and a runtime data-race warning otherwise.");
        }
    }

    @Override
    public void visitAst(InputFile file, SwiftAst ast, SensorContext context) {
        // AST-mode check could traverse `MemberAccessExpr` whose base resolves
        // to an `actor` type. Wiring deferred until ADR-0007 (actor type
        // resolution).
    }

    private boolean precededByAwaitOnSameLine(List<Token> tokens, int idx) {
        int line = tokens.get(idx).line();
        for (int j = idx - 1; j >= 0; j--) {
            Token p = tokens.get(j);
            if (p.line() != line) return false;
            if (p.kind() == Token.Kind.KEYWORD && "await".equals(p.text())) return true;
        }
        return false;
    }

    private boolean fileDeclaresAnActor(List<Token> tokens) {
        for (Token t : tokens) {
            if (t.kind() == Token.Kind.KEYWORD && "actor".equals(t.text())) return true;
        }
        return false;
    }
}
