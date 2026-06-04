package io.sonarswift.plugin.rules;

import io.sonarswift.plugin.parser.Token;

import org.sonar.api.batch.fs.InputFile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Honors source-level suppressions so checks don't double-report things the
 * author already acknowledged.
 *
 * <p>Recognised syntaxes:</p>
 * <ul>
 *   <li>{@code // sonar-swift:disable SXXXX} — disables one rule for the rest of the file</li>
 *   <li>{@code // sonar-swift:disable-next-line SXXXX} — disables for the following source line</li>
 *   <li>{@code // sonar-swift:disable-this-line SXXXX} — disables for this source line</li>
 *   <li>{@code // sonar-swift:disable-line SXXXX} — alias for disable-this-line</li>
 *   <li>{@code // swiftlint:disable XXX} — honored for the equivalent sonar rule (best-effort mapping)</li>
 * </ul>
 *
 * <p>Tokens carry their text including comment trivia in our tokenizer; we
 * parse the comment lines while scanning, build a per-file map of
 * {@code line → suppressedRuleKeys}, and check entries before emitting issues.</p>
 */
public final class SuppressionRegistry {

    private final Map<Integer, Set<String>> perLine = new HashMap<>();
    /** Rules suppressed file-wide. */
    private final Set<String> fileWide = new HashSet<>();
    /** Last line of the source file we built the registry for. */
    private final int fileLineCount;

    public SuppressionRegistry(InputFile file, List<Token> tokens) {
        this.fileLineCount = file.lines();
        build(tokens);
    }

    /** Returns true if the rule should NOT report at this line. */
    public boolean isSuppressed(int line, String ruleKey) {
        if (fileWide.contains(ruleKey) || fileWide.contains("ALL")) return true;
        Set<String> set = perLine.get(line);
        return set != null && (set.contains(ruleKey) || set.contains("ALL"));
    }

    private void build(List<Token> tokens) {
        for (Token t : tokens) {
            if (!t.isComment()) continue;
            String text = t.text();

            // sonar-swift:disable RULE | sonar-swift:disable-next-line RULE | ...
            int idx = text.indexOf("sonar-swift:disable");
            int swlIdx = text.indexOf("swiftlint:disable");
            if (idx < 0 && swlIdx < 0) continue;
            String key = idx >= 0 ? "sonar-swift" : "swiftlint";
            int start = idx >= 0 ? idx : swlIdx;
            String tail = text.substring(start);

            String[] parts = tail.split("\\s+");
            if (parts.length < 1) continue;
            String directive = parts[0];

            Set<String> ruleKeys = new HashSet<>();
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].replaceAll("[^A-Za-z0-9_:-]", "");
                if (!part.isBlank()) {
                    ruleKeys.add(key.equals("swiftlint") ? mapSwiftLintRule(part) : part);
                }
            }
            if (ruleKeys.isEmpty()) ruleKeys.add("ALL");

            int line = t.line();
            switch (directive) {
                case "sonar-swift:disable",
                     "swiftlint:disable" -> fileWide.addAll(ruleKeys);
                case "sonar-swift:disable-next-line",
                     "swiftlint:disable-next-line" -> {
                    if (line + 1 <= fileLineCount) {
                        perLine.computeIfAbsent(line + 1, k -> new HashSet<>()).addAll(ruleKeys);
                    }
                }
                case "sonar-swift:disable-this-line",
                     "sonar-swift:disable-line",
                     "swiftlint:disable-this-line" ->
                    perLine.computeIfAbsent(line, k -> new HashSet<>()).addAll(ruleKeys);
                default -> { /* unknown directive — ignore */ }
            }
        }
    }

    /**
     * Best-effort mapping from SwiftLint rule names to our SonarQube rule keys.
     * Returns the input unchanged if no mapping is known — the entry then
     * silently has no effect, which is the safe default.
     */
    private static String mapSwiftLintRule(String swiftlintRule) {
        return switch (swiftlintRule) {
            case "force_unwrapping" -> "S1001";
            case "force_cast" -> "S1003";
            case "force_try" -> "S1004";
            case "print" -> "S1005";
            case "todo" -> "S1007";
            case "no_extension_access_modifier" -> "S1010";
            case "weak_delegate" -> "S1904";
            case "unhandled_throwing_task" -> "S1906";
            default -> swiftlintRule;
        };
    }
}
