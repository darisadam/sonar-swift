package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;

/**
 * S1908 — `nonisolated(unsafe)` is a compile-time escape hatch that opts a
 * declaration out of strict concurrency checking. Each use should be reviewed
 * by a human; the type is now responsible for its own synchronization.
 */
public class NonisolatedUnsafeHotspotCheck extends SwiftCheck {

    public NonisolatedUnsafeHotspotCheck() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1908";
    }

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        for (int i = 0; i + 3 < tokens.size(); i++) {
            if (!"nonisolated".equals(tokens.get(i).text())) continue;
            if (!"(".equals(tokens.get(i + 1).text())) continue;
            if (!"unsafe".equals(tokens.get(i + 2).text())) continue;
            if (!")".equals(tokens.get(i + 3).text())) continue;
            reportIssue(ctx, file, tokens.get(i),
                    "`nonisolated(unsafe)` opts out of concurrency checking. Confirm the synchronization "
                    + "scheme — otherwise this is a latent data race.");
        }
    }
}
