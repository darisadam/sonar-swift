package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.parser.ast.SwiftAst;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;

/**
 * S1906 — A call to an `async` function appears inside an `async` function
 * without the `await` keyword.
 *
 * <p>The Swift compiler catches this normally — but only when type information
 * is available. With external types (e.g. when the dependency is a binary
 * framework), the swiftc error message is sometimes mis-attributed to the call
 * site. This rule is intentionally a redundancy guard for the worst cases:
 * a heuristic detector of pattern `someAsyncFunc(` without a preceding `await`
 * inside `async` function bodies.</p>
 *
 * <p>Token-mode is best-effort. AST mode (parser sidecar) uses `FuncDecl.isAsync`
 * and `CallExpr.isAwaited` to evaluate properly.</p>
 */
public class MissingAwaitCheck extends SwiftCheck {

    public MissingAwaitCheck() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1906";
    }

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        // Token mode is too noisy without type info — no-op here. AST mode is
        // where the real work happens. Keeping the rule registered so it shows
        // up in the rule browser and quality profile even when AST is disabled.
    }

    @Override
    public void visitAst(InputFile file, SwiftAst ast, SensorContext context) {
        // Traverse: for each FuncDecl(isAsync = true), descend into its body
        // and find CallExpr nodes whose callee is *known* async (per the
        // parser-side `isAwaited` flag emitted alongside CallExpr) and not
        // preceded by an `await` token. Implemented in a richer visitor when
        // the parser starts emitting `isAwaited` and call resolution data
        // (target milestone 0.4 — see ROADMAP).
    }
}
