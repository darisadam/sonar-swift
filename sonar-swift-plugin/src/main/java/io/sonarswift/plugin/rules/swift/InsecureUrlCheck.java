package io.sonarswift.plugin.rules.swift;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;
import java.util.regex.Pattern;

/**
 * S1802 — Cleartext {@code http://} URL where {@code https://} should be used.
 *
 * <p>Detects any string literal containing {@code http://} that isn't:</p>
 * <ul>
 *   <li>localhost / 127.0.0.1 / a private-range IP</li>
 *   <li>an XML namespace URI</li>
 *   <li>schema documentation reference</li>
 * </ul>
 */
public class InsecureUrlCheck extends SwiftCheck {

    private static final Pattern HTTP = Pattern.compile("http://[^\\s\"'<>]+");
    private static final Pattern LOCAL_OR_PRIVATE = Pattern.compile(
            "http://(localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0|10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.)");
    private static final Pattern XML_NS = Pattern.compile("http://(www\\.)?w3\\.org/");
    private static final Pattern XML_SCHEMA = Pattern.compile("http://(www\\.)?(apple\\.com/DTDs|java\\.sun\\.com)");

    public InsecureUrlCheck() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1802";
    }

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        for (Token t : tokens) {
            if (t.kind() != Token.Kind.STRING_LITERAL) continue;
            String text = t.text();

            var m = HTTP.matcher(text);
            while (m.find()) {
                String url = m.group();
                if (LOCAL_OR_PRIVATE.matcher(url).find()) continue;
                if (XML_NS.matcher(url).find()) continue;
                if (XML_SCHEMA.matcher(url).find()) continue;
                reportIssue(ctx, file, t,
                        "Cleartext URL `" + url.substring(0, Math.min(80, url.length())) + "` — use https://.");
                break; // one issue per literal is plenty
            }
        }
    }
}
