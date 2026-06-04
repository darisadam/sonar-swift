package io.sonarswift.cli;

import io.sonarswift.cli.dashboard.DashboardServer;
import io.sonarswift.cli.pipeline.PipelineCommand;
import io.sonarswift.cli.quickscan.QuickScanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Entry point for the sonar-swift CLI.
 *
 * <p>Subcommands:</p>
 * <pre>
 * sonar-swift scan [path]            run a full SonarQube scan
 * sonar-swift quick-scan FILES…      lightweight native checks; used by pre-commit
 * sonar-swift dashboard [opts]       start the local control dashboard
 * sonar-swift validate-config        validate sonar-project.properties
 * sonar-swift help | --version
 * </pre>
 *
 * <p>This is intentionally small. The heavy lifting is done by SonarScanner and
 * the plugin JAR installed on the server.</p>
 */
public final class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length == 0 || matchesAny(args[0], "help", "-h", "--help")) {
            printHelp();
            return;
        }

        switch (args[0]) {
            case "scan" -> scan(tail(args));
            case "quick-scan" -> quickScan(tail(args));
            case "dashboard" -> dashboard(tail(args));
            case "pipeline" -> System.exit(PipelineCommand.dispatch(tail(args)));
            case "validate-config" -> validateConfig();
            case "--version", "-v" -> System.out.println("sonar-swift-cli 0.2.0-SNAPSHOT");
            default -> {
                System.err.println("Unknown subcommand: " + args[0]);
                printHelp();
                System.exit(2);
            }
        }
    }

    // ---------- scan ----------

    private static void scan(String[] extraArgs) throws IOException, InterruptedException {
        Path dir = extraArgs.length > 0 && !extraArgs[0].startsWith("-")
                ? Path.of(extraArgs[0])
                : Path.of(".");
        if (!Files.isDirectory(dir)) {
            System.err.println("Not a directory: " + dir);
            System.exit(2);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("sonar-scanner");

        // Inject convenience defaults from the file system
        Path props = dir.resolve("sonar-project.properties");
        if (!Files.exists(props)) {
            System.out.println("(no sonar-project.properties — using defaults)");
            cmd.add("-Dsonar.sources=" + (Files.exists(dir.resolve("Sources")) ? "Sources" : "."));
        }

        // Auto-detect common report paths
        for (var p : List.of(
                "build/coverage.json",
                "build/coverage.xml",
                "swiftlint.json",
                "periphery.json")) {
            File f = dir.resolve(p).toFile();
            if (f.exists()) {
                String key = switch (p) {
                    case "build/coverage.json" -> "sonar.swift.coverage.xccov.reportPaths";
                    case "build/coverage.xml" -> "sonar.swift.coverage.slather.reportPaths";
                    case "swiftlint.json" -> "sonar.swift.swiftlint.reportPaths";
                    case "periphery.json" -> "sonar.swift.periphery.reportPath";
                    default -> null;
                };
                if (key != null) cmd.add("-D" + key + "=" + f.getAbsolutePath());
            }
        }

        // Skip first arg if it was the path
        int start = extraArgs.length > 0 && !extraArgs[0].startsWith("-") ? 1 : 0;
        for (int i = start; i < extraArgs.length; i++) cmd.add(extraArgs[i]);

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .inheritIO()
                .directory(dir.toFile());
        Process p = pb.start();
        System.exit(p.waitFor());
    }

    // ---------- quick-scan (pre-commit) ----------

    private static void quickScan(String[] args) {
        QuickScanner.Options opts = new QuickScanner.Options();
        List<String> files = new ArrayList<>();
        for (String a : args) {
            if (a.startsWith("--format=")) opts.format = a.substring("--format=".length());
            else if ("--all-checks".equals(a)) opts.allChecks = true;
            else if (a.startsWith("--")) {
                System.err.println("Unknown flag: " + a);
                System.exit(2);
            } else {
                files.add(a);
            }
        }
        if (files.isEmpty()) {
            System.err.println("usage: sonar-swift quick-scan FILE...");
            System.exit(2);
        }
        int findings = QuickScanner.scan(files, opts);
        // Exit 1 on blocker findings, 0 otherwise. The runner script (used by
        // pre-commit) honors this code to decide whether to block the commit.
        System.exit(findings > 0 ? 1 : 0);
    }

    // ---------- dashboard ----------

    private static void dashboard(String[] args) throws IOException {
        DashboardServer.Config cfg = new DashboardServer.Config();
        // env first
        if (System.getenv("SONAR_SWIFT_BIND") != null) cfg.bindHost = System.getenv("SONAR_SWIFT_BIND");
        if (System.getenv("SONAR_SWIFT_PORT") != null) cfg.port = Integer.parseInt(System.getenv("SONAR_SWIFT_PORT"));

        // then CLI flags override
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--bind" -> cfg.bindHost = args[++i];
                case "--port" -> cfg.port = Integer.parseInt(args[++i]);
                case "--sonar-url" -> cfg.sonarUrl = args[++i];
                case "--workspace" -> cfg.workspace = Path.of(args[++i]).toAbsolutePath().normalize();
                case "--scanner-binary" -> cfg.scannerBinary = args[++i];
                case "--help", "-h" -> {
                    System.out.println("sonar-swift dashboard [--bind HOST] [--port N] [--sonar-url URL] [--workspace DIR]");
                    return;
                }
                default -> {
                    System.err.println("Unknown flag: " + args[i]);
                    System.exit(2);
                }
            }
        }

        DashboardServer server = new DashboardServer(cfg);
        server.start();
        System.out.println("sonar-swift dashboard listening on " + server.baseUrl());
        System.out.println("Workspace: " + cfg.workspace);
        System.out.println("SonarQube: " + cfg.sonarUrl);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "dashboard-shutdown"));

        // Park the main thread — the HttpServer runs on its own executor.
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------- validate ----------

    private static void validateConfig() {
        Path props = Path.of("sonar-project.properties");
        if (!Files.exists(props)) {
            System.err.println("sonar-project.properties not found");
            System.exit(1);
        }
        // Minimal validation: required keys
        try {
            String content = Files.readString(props);
            List<String> required = List.of("sonar.projectKey", "sonar.sources");
            boolean ok = true;
            for (String key : required) {
                if (!content.contains(key + "=") && !content.contains(key + " =")) {
                    System.err.println("Missing required property: " + key);
                    ok = false;
                }
            }
            System.out.println(ok ? "Config looks good." : "Fix the issues above.");
            if (!ok) System.exit(1);
        } catch (IOException e) {
            System.err.println("Failed to read properties: " + e.getMessage());
            System.exit(1);
        }
    }

    // ---------- helpers ----------

    private static boolean matchesAny(String s, String... opts) {
        return Arrays.asList(opts).contains(s);
    }

    private static String[] tail(String[] args) {
        String[] out = new String[args.length - 1];
        System.arraycopy(args, 1, out, 0, out.length);
        return out;
    }

    private static void printHelp() {
        System.out.println("""
                sonar-swift — convenience CLI for the sonar-swift SonarQube plugin.

                Usage:
                  sonar-swift scan [path]            full SonarQube scan
                  sonar-swift quick-scan FILES…      lightweight scan for pre-commit
                                                     flags: --format=compiler|json --all-checks
                  sonar-swift dashboard [flags]      start the local control console
                                                     flags: --bind HOST --port N --sonar-url URL
                                                            --workspace DIR --scanner-binary PATH
                  sonar-swift pipeline init          scaffold .sonar-swift/pipeline.yml
                  sonar-swift pipeline run           execute the local CI/CD pipeline
                  sonar-swift pipeline validate      check the pipeline.yml is well-formed
                  sonar-swift pipeline list          list pipeline stages or built-in steps
                  sonar-swift validate-config        check sonar-project.properties for missing keys
                  sonar-swift help                   show this help
                  sonar-swift --version              show CLI version

                Environment:
                  SONAR_HOST_URL                     defaults to http://localhost:9000
                  SONAR_TOKEN                        SonarQube auth token
                  SONAR_SWIFT_BIND                   dashboard bind host (default 127.0.0.1)
                  SONAR_SWIFT_PORT                   dashboard port (default 8080)

                Auto-detected files (no flag needed if present at scan time):
                  build/coverage.json                xccov JSON  → sonar.swift.coverage.xccov.reportPaths
                  build/coverage.xml                 Slather XML → sonar.swift.coverage.slather.reportPaths
                  swiftlint.json                     SwiftLint   → sonar.swift.swiftlint.reportPaths
                  periphery.json                     Periphery   → sonar.swift.periphery.reportPath
                """);
    }

    private Main() {}
}
