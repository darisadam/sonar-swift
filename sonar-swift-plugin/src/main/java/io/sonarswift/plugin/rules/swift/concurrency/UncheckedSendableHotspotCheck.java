package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;

/**
 * S1901 — {@code @unchecked Sendable} is a manual safety claim and should be
 * reviewed.
 *
 * <p>Reported as a security hotspot (per {@code S1901.json}). Each occurrence
 * needs a human to confirm the type really is thread-safe — either it's truly
 * immutable, or it owns its own synchronization (lock, serial dispatch queue,
 * actor wrapping). The compiler can't check.</p>
 */
public class UncheckedSendableHotspotCheck extends SwiftCheck {

    public UncheckedSendableHotspotCheck() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1901";
    }

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        for (int i = 0; i + 2 < tokens.size(); i++) {
            Token at = tokens.get(i);
            // Match either `@unchecked Sendable` or `: @unchecked Sendable`
            if (!"@unchecked".equals(at.text())) continue;
            Token next = tokens.get(i + 1);
            if (!"Sendable".equals(next.text())) continue;

            reportIssue(ctx, file, at,
                    "`@unchecked Sendable` declares thread-safety manually. Confirm the type is truly "
                    + "immutable or has internal synchronization, otherwise data races will go unnoticed.");
        }
    }
}
