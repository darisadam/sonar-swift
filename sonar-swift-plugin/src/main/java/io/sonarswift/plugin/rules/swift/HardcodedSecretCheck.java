package io.sonarswift.plugin.rules.swift;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * S1800 — Hardcoded API key / token / credential in source.
 *
 * <p>Two detection paths:</p>
 * <ol>
 *   <li><b>Catalog patterns</b> — anchor matches for known token formats
 *       (AWS, GitHub PAT, Slack, JWT, …).</li>
 *   <li><b>Heuristic</b> — a string literal of meaningful length assigned to
 *       an identifier whose name suggests a secret, with Shannon entropy
 *       above a threshold.</li>
 * </ol>
 *
 * <p>Suppress single occurrences with {@code // sonar-secret:keep}.</p>
 */
public class HardcodedSecretCheck extends SwiftCheck {

    private static final Map<String, Pattern> CATALOG = Map.of(
            "AWS access key", Pattern.compile("AKIA[0-9A-Z]{16}"),
            "GitHub PAT", Pattern.compile("ghp_[0-9a-zA-Z]{36}"),
            "GitHub fine-grained PAT", Pattern.compile("github_pat_[0-9a-zA-Z_]{82}"),
            "Slack bot token", Pattern.compile("xoxb-[0-9]+-[0-9]+-[0-9a-zA-Z]+"),
            "Slack user token", Pattern.compile("xoxp-[0-9-]+"),
            "Google API key", Pattern.compile("AIza[0-9A-Za-z\\-_]{35}"),
            "Stripe live key", Pattern.compile("sk_live_[0-9a-zA-Z]{24,}"),
            "JWT", Pattern.compile("eyJ[a-zA-Z0-9_-]{10,}\\.eyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}")
    );

    private static final Pattern SECRET_IDENT =
            Pattern.compile("(?i).*(api[_-]?key|secret|token|password|pwd|auth|access[_-]?key).*");

    public HardcodedSecretCheck() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1800";
    }

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        // Build a set of lines that have a suppression marker
        var suppressedLines = new java.util.HashSet<Integer>();
        for (Token t : tokens) {
            if (t.isComment() && t.text().contains("sonar-secret:keep")) {
                suppressedLines.add(t.line());
            }
        }

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.kind() != Token.Kind.STRING_LITERAL) continue;
            if (suppressedLines.contains(t.line())) continue;

            String content = stripQuotes(t.text());
            if (content.length() < 16) continue;

            // 1. Catalog match — high confidence
            for (var e : CATALOG.entrySet()) {
                if (e.getValue().matcher(content).find()) {
                    reportIssue(ctx, file, t,
                            "Hardcoded " + e.getKey() + " detected — remove and load from a secret store.");
                    return;
                }
            }

            // 2. Heuristic — entropy + name
            String identName = findEnclosingIdent(tokens, i);
            if (identName != null && SECRET_IDENT.matcher(identName).matches()) {
                if (shannonEntropy(content) >= 4.5) {
                    reportIssue(ctx, file, t,
                            "Possible hardcoded secret in identifier `" + identName + "` — store it outside source.");
                }
            }
        }
    }

    private static String stripQuotes(String s) {
        if (s.startsWith("\"\"\"") && s.endsWith("\"\"\"")) return s.substring(3, s.length() - 3);
        if (s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s;
    }

    /**
     * Looks backwards for {@code let foo = "..."} or {@code static let foo = "..."} pattern,
     * returns the identifier name.
     */
    private static String findEnclosingIdent(List<Token> tokens, int stringIdx) {
        // Pattern: KEYWORD(let|var) IDENT (= | : Type =) "..."
        // Walk back, ignore whitespace tokens (we don't have any)
        for (int j = stringIdx - 1; j >= Math.max(0, stringIdx - 6); j--) {
            Token p = tokens.get(j);
            if (p.kind() == Token.Kind.IDENT) {
                return p.text();
            }
        }
        return null;
    }

    private static double shannonEntropy(String s) {
        int[] freq = new int[256];
        int len = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 256) { freq[c]++; len++; }
        }
        if (len == 0) return 0;
        double e = 0;
        for (int f : freq) {
            if (f == 0) continue;
            double p = (double) f / len;
            e -= p * (Math.log(p) / Math.log(2));
        }
        return e;
    }
}
