package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;

/**
 * S1905 — `Task.detached { ... }` opts out of the enclosing actor's isolation.
 *
 * <p>This is rarely what you want — detached tasks lose priority, executor and
 * actor inheritance. The recommended replacement is plain `Task { ... }` (which
 * inherits) unless detachment is intentional for a known reason (long-running
 * background work, escaping a `@MainActor` constraint deliberately).</p>
 *
 * <p>Reported as a `CODE_SMELL` at MAJOR severity, not a bug — `Task.detached`
 * is legitimate sometimes; the rule's job is to make the choice explicit.</p>
 */
public class DetachedTaskMisuseCheck extends SwiftCheck {

    public DetachedTaskMisuseCheck() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1905";
    }

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        for (int i = 0; i + 2 < tokens.size(); i++) {
            Token a = tokens.get(i);
            Token b = tokens.get(i + 1);
            Token c = tokens.get(i + 2);
            if (a.kind() != Token.Kind.IDENT || !"Task".equals(a.text())) continue;
            if (!".".equals(b.text())) continue;
            if (c.kind() != Token.Kind.IDENT || !"detached".equals(c.text())) continue;

            reportIssue(ctx, file, c,
                    "`Task.detached` opts out of the enclosing actor's isolation and priority. "
                    + "Use plain `Task { … }` to inherit, or document why detachment is intended.");
        }
    }
}
