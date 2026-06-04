package io.sonarswift.plugin.rules.swift;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;
import java.util.regex.Pattern;

/** S1007 — TODO / FIXME / HACK / XXX markers in comments. */
public class TodoCommentCheck extends SwiftCheck {

    private static final Pattern MARKER =
            Pattern.compile("\\b(TODO|FIXME|HACK|XXX)\\b(?:\\(|:|\\s)", Pattern.CASE_INSENSITIVE);

    public TodoCommentCheck() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1007";
    }

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        for (Token t : tokens) {
            if (!t.isComment()) continue;
            var m = MARKER.matcher(t.text());
            if (m.find()) {
                reportIssue(ctx, file, t,
                        "Complete this " + m.group(1).toUpperCase() + " or convert it to a tracked ticket.");
            }
        }
    }
}
