package io.sonarswift.plugin.rules;

import io.sonarswift.plugin.export.IssueTap;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.parser.ast.SwiftAst;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rule.RuleKey;

import java.util.List;

/**
 * Base class for every Swift check the plugin ships.
 *
 * <p>Checks operate on either:</p>
 * <ul>
 *   <li>The token stream (cheap, available always), or</li>
 *   <li>The SwiftSyntax AST (richer, available when the native parser is enabled)</li>
 * </ul>
 *
 * <p>Override {@link #visitTokens} for token-based checks (force-unwrap,
 * TODO/FIXME, MD5 calls, hardcoded patterns).</p>
 *
 * <p>Override {@link #visitAst} for structural checks (function-too-long,
 * nesting depth, retain cycles, missing weak-self).</p>
 *
 * <p>Default implementations do nothing — checks override only what they need.</p>
 *
 * <p>The base {@code reportIssue} methods automatically:</p>
 * <ul>
 *   <li>Skip suppressed lines (per {@link SuppressionRegistry})</li>
 *   <li>Mirror the finding to {@link IssueTap} for SARIF export</li>
 * </ul>
 */
public abstract class SwiftCheck {

    /** The repository this check's rule belongs to, used to construct the {@link RuleKey}. */
    protected final String repositoryKey;

    /**
     * Suppression registry for the current file, set by the sensor before
     * dispatching to {@link #visitTokens} or {@link #visitAst}. May be {@code null}
     * in unit-test fixtures.
     */
    private SuppressionRegistry suppressions;

    protected SwiftCheck(String repositoryKey) {
        this.repositoryKey = repositoryKey;
    }

    /** Rule key string — derived from {@link Rule @Rule} or set by subclass. */
    public abstract String ruleKey();

    /** Severity placeholder used by the SARIF mirror. Override per check if needed. */
    protected String severity() {
        return "MAJOR";
    }

    /** Sensor wiring — passes per-file suppressions in before walking the check. */
    public final void setSuppressions(SuppressionRegistry s) {
        this.suppressions = s;
    }

    /** Walk the token stream. Override for token-level checks. */
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext context) {
        // default: no-op
    }

    /** Walk the AST. Override for AST-level checks. Called only when AST is available. */
    public void visitAst(InputFile file, SwiftAst ast, SensorContext context) {
        // default: no-op
    }

    /** Helper: report an issue on a token's location. */
    protected void reportIssue(SensorContext ctx, InputFile file, Token at, String message) {
        if (suppressions != null && suppressions.isSuppressed(at.line(), ruleKey())) return;

        NewIssue issue = ctx.newIssue().forRule(RuleKey.of(repositoryKey, ruleKey()));
        NewIssueLocation loc = issue.newLocation()
                .on(file)
                .at(file.newRange(at.line(), at.col() - 1, at.endLine(), at.endCol() - 1))
                .message(message);
        issue.at(loc).save();

        IssueTap.record(new IssueTap.Finding(
                ruleKey(), severity(), "CODE_SMELL", message,
                file.uri().getPath(), at.line(), at.endLine()));
    }

    /** Helper: report an issue on a line range. */
    protected void reportIssue(SensorContext ctx, InputFile file, int startLine, int endLine, String message) {
        if (suppressions != null && suppressions.isSuppressed(startLine, ruleKey())) return;

        NewIssue issue = ctx.newIssue().forRule(RuleKey.of(repositoryKey, ruleKey()));
        NewIssueLocation loc = issue.newLocation()
                .on(file)
                .at(file.selectLine(startLine))
                .message(message);
        issue.at(loc).save();

        IssueTap.record(new IssueTap.Finding(
                ruleKey(), severity(), "CODE_SMELL", message,
                file.uri().getPath(), startLine, endLine));
    }
}
