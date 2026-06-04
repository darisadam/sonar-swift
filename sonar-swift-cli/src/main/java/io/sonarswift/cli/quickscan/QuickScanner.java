package io.sonarswift.cli.quickscan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local-only pattern scanner for pre-commit hooks. Re-implements the
 * highest-signal rules from {@code sonar-swift-plugin} in pure Java so the CLI
 * doesn't need a running SonarQube to give useful feedback.
 *
 * <p>It is deliberately a <b>subset</b> of the plugin's rule set: only rules
 * whose detection is cheap and unambiguous on a single file. AST-aware checks
 * and cross-file semantics stay in the plugin.</p>
 *
 * <p>Format:</p>
 * <ul>
 *   <li>{@code compiler} — gcc-style {@code file:line:col: severity: message [rule]}</li>
 *   <li>{@code json} — JSON Lines for IDE tooling</li>
 * </ul>
 */
public final class QuickScanner {

    private QuickScanner() {}

    public static final class Options {
        public String format = "compiler";
        public boolean allChecks = false;
    }

    // ---- patterns ----
    // Each Rule pairs a key with a regex over a line of source. The patterns
    // mirror the same Java check class in the plugin; keep them in sync.

    private record Rule(
            String key, String severity, String message, Pattern pattern,
            boolean skipForTests) {}

    private static final List<Rule> RULES = List.of(
            new Rule("S1001", "BLOCKER", "Force-unwrap of optional (`!`) should be avoided",
                    // `\b` keeps us from matching mid-identifier (so `try!` doesn't also
                    // match as `ry!`); the negative lookahead lets `try!`, `as!`, `is!`
                    // be flagged by their dedicated rules (S1003/S1004) instead of here.
                    Pattern.compile("(?<![=!<>])\\b(?!(?:try|as|is)!)[A-Za-z_][A-Za-z_0-9]*!(?![=A-Za-z_0-9])"),
                    true),
            new Rule("S1003", "BLOCKER", "Force-cast (`as!`) should be avoided",
                    Pattern.compile("\\bas!\\s+"),
                    true),
            new Rule("S1004", "BLOCKER", "Force-try (`try!`) should be avoided",
                    Pattern.compile("\\btry!\\s+"),
                    true),
            new Rule("S1005", "MAJOR", "`print(...)` left in production code",
                    Pattern.compile("\\bprint\\s*\\("),
                    true),
            new Rule("S1007", "MINOR", "TODO / FIXME comment",
                    Pattern.compile("(?i)//\\s*(todo|fixme)"),
                    false),
            new Rule("S1800", "BLOCKER", "Hardcoded API key / secret",
                    Pattern.compile("(?i)(api[_-]?key|secret|password|token)\\s*=\\s*\"[A-Za-z0-9+/=_-]{16,}\""),
                    false),
            new Rule("S1802", "CRITICAL", "Insecure http:// URL",
                    Pattern.compile("\"http://[^\"]*\""),
                    false),
            new Rule("S1804", "CRITICAL", "Weak crypto: MD5",
                    Pattern.compile("\\b(Insecure\\.MD5|CC_MD5|MD5\\b)"),
                    false),

            // Concurrency
            new Rule("S1901", "MINOR", "@unchecked Sendable — review required",
                    Pattern.compile("@unchecked\\s+Sendable"),
                    false),
            new Rule("S1903", "CRITICAL", "UI API touched from likely off-main context (hint)",
                    Pattern.compile("(UIApplication|UIView|UIWindow|UIViewController)\\.(?!shared\\b)"),
                    true),
            new Rule("S1904", "MAJOR", "Task { ... } without [weak self]",
                    Pattern.compile("Task\\s*\\{\\s*$"),
                    false),
            new Rule("S1905", "MAJOR", "Task.detached opts out of actor isolation",
                    Pattern.compile("\\bTask\\.detached\\b"),
                    false),
            new Rule("S1907", "BLOCKER", "`static var` outside an actor",
                    Pattern.compile("(?<!nonisolated\\(unsafe\\)\\s)static\\s+var\\b"),
                    false),
            new Rule("S1908", "MINOR", "`nonisolated(unsafe)` — review required",
                    Pattern.compile("\\bnonisolated\\(unsafe\\)"),
                    false));

    /**
     * Scan the given files. Returns the count of BLOCKER findings (those that
     * justify failing the pre-commit hook).
     */
    public static int scan(List<String> paths, Options opts) {
        Set<Path> resolved = new LinkedHashSet<>();
        for (String p : paths) {
            Path resolvedPath = Paths.get(p);
            if (Files.isDirectory(resolvedPath)) {
                try {
                    Files.walk(resolvedPath)
                            .filter(f -> {
                                String n = f.getFileName().toString();
                                return n.endsWith(".swift") || n.endsWith(".m") || n.endsWith(".mm") || n.endsWith(".h");
                            })
                            .forEach(resolved::add);
                } catch (IOException ignored) {
                    // best-effort
                }
            } else if (Files.isRegularFile(resolvedPath)) {
                resolved.add(resolvedPath);
            }
        }

        int blockers = 0;
        for (Path file : resolved) {
            String src;
            try {
                src = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.err.println("quick-scan: failed to read " + file + ": " + e.getMessage());
                continue;
            }
            blockers += scanFile(file, src, opts);
        }
        return blockers;
    }

    private static int scanFile(Path file, String source, Options opts) {
        boolean isTest = isTestPath(file);
        int blockers = 0;

        // Skim comment lines so we can skip them (mostly). The S1007 rule lives
        // inside comments so we don't pre-strip them.
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            for (Rule r : RULES) {
                if (isTest && r.skipForTests()) continue;
                Matcher m = r.pattern().matcher(raw);
                if (!m.find()) continue;
                // Ignore matches inside string literals for some rules (S1005, S1007)
                // to keep false positives down. Conservative — skip the rule if the
                // match column is bracketed by quotes on the same line, except for
                // S1800/S1802 which deliberately match inside strings.
                if (!r.key().equals("S1800") && !r.key().equals("S1802")) {
                    if (isInsideString(raw, m.start())) continue;
                }
                emit(opts.format, file, i + 1, m.start() + 1, r);
                if ("BLOCKER".equals(r.severity())) blockers++;
            }
        }
        return blockers;
    }

    private static boolean isInsideString(String line, int col) {
        // Naive: count unescaped quotes before col; odd → inside.
        int quotes = 0;
        for (int j = 0; j < col && j < line.length(); j++) {
            char c = line.charAt(j);
            if (c == '"' && (j == 0 || line.charAt(j - 1) != '\\')) quotes++;
        }
        return quotes % 2 == 1;
    }

    private static void emit(String format, Path file, int line, int col, Rule r) {
        if ("json".equals(format)) {
            System.out.printf(
                    "{\"file\":\"%s\",\"line\":%d,\"col\":%d,\"severity\":\"%s\",\"rule\":\"%s\",\"message\":\"%s\"}%n",
                    escape(file.toString()), line, col, r.severity(), r.key(), escape(r.message()));
        } else {
            // gcc-style — editors auto-detect
            String level = switch (r.severity()) {
                case "BLOCKER", "CRITICAL" -> "error";
                case "MAJOR" -> "warning";
                default -> "note";
            };
            System.out.printf("%s:%d:%d: %s: %s [%s]%n",
                    file, line, col, level, r.message(), r.key());
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean isTestPath(Path file) {
        // Reasonable defaults: */Tests/* or *Tests.swift / *Test.swift
        String s = file.toString().replace('\\', '/');
        if (s.contains("/Tests/") || s.contains("/test/")) return true;
        String name = file.getFileName().toString();
        return name.endsWith("Tests.swift") || name.endsWith("Test.swift");
    }
}
