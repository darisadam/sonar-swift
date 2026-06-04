package io.sonarswift.plugin.rules.swift;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;

/** S1003 — Force-cast {@code expr as! Type}. */
public class ForceCastCheck extends SwiftCheck {

    public ForceCastCheck() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1003";
    }

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        for (int i = 0; i < tokens.size() - 1; i++) {
            Token a = tokens.get(i);
            Token b = tokens.get(i + 1);
            if (a.kind() == Token.Kind.KEYWORD
                    && "as".equals(a.text())
                    && b.kind() == Token.Kind.OPERATOR
                    && "!".equals(b.text())
                    && a.endLine() == b.line()
                    && a.endCol() == b.col()) {
                reportIssue(ctx, file, a,
                        "Avoid force-casting — use conditional cast `as?` and handle the optional.");
            }
        }
    }
}
