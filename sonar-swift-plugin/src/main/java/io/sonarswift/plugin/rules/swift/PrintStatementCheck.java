package io.sonarswift.plugin.rules.swift;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;
import java.util.Set;

/**
 * S1005 — {@code print(...)} / {@code debugPrint(...)} / {@code dump(...)}
 * calls left in production code. Doesn't fire inside test files.
 */
public class PrintStatementCheck extends SwiftCheck {

    private static final Set<String> CALLEES = Set.of("print", "debugPrint", "dump");

    public PrintStatementCheck() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1005";
    }

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        // Don't fire inside test sources
        String path = file.uri().toString().toLowerCase();
        if (path.contains("/tests/") || path.endsWith("tests.swift") || path.endsWith("test.swift")) {
            return;
        }

        for (int i = 0; i < tokens.size() - 1; i++) {
            Token a = tokens.get(i);
            Token b = tokens.get(i + 1);
            if (a.kind() == Token.Kind.IDENT
                    && CALLEES.contains(a.text())
                    && b.kind() == Token.Kind.PUNCT
                    && "(".equals(b.text())
                    && a.endLine() == b.line()
                    && a.endCol() == b.col()) {
                // Make sure it's not a method call on an object (skip if preceded by `.`)
                if (i > 0) {
                    Token prev = tokens.get(i - 1);
                    if (prev.kind() == Token.Kind.OPERATOR && ".".equals(prev.text())) {
                        continue;
                    }
                }
                reportIssue(ctx, file, a,
                        "Remove this `" + a.text() + "` call — use a proper logger (`os.Logger`, `OSLog`) instead.");
            }
        }
    }
}
