package io.sonarswift.plugin.external;

import io.sonarswift.plugin.SwiftPluginConstants;

import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.config.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Locates, optionally installs, and invokes the {@code swiftlint} executable,
 * then writes its JSON output to a temp file the {@link SwiftLintSensor} can
 * parse via the normal report-import path.
 *
 * <p>Triggered by {@code sonar.swift.swiftlint.autorun=true}. The plugin
 * deliberately stays a thin wrapper:</p>
 *
 * <ol>
 *   <li>Resolve a SwiftLint binary — explicit path → PATH → common install
 *       locations → optional auto-install (brew / mint, only if explicitly
 *       enabled via {@code sonar.swift.swiftlint.autoInstall=true}).</li>
 *   <li>Resolve a config file — explicit path → repo root {@code .swiftlint.yml}.</li>
 *   <li>Spawn SwiftLint with {@code --reporter json --quiet}; capture stdout
 *       and write it to {@code .sonar/swiftlint/swiftlint-autorun.json}.</li>
 *   <li>Return the path so the sensor reads it like any other report.</li>
 * </ol>
 *
 * <p>The runner is intentionally tolerant of missing tooling: if SwiftLint is
 * not on PATH and auto-install is off, it logs a clear hint and returns
 * empty so the scan continues. We never fail the scan just because an external
 * tool wasn't installed on this machine.</p>
 */
public final class SwiftLintRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SwiftLintRunner.class);

    /** Standard places we look for swiftlint when {@code which} fails. */
    private static final List<String> CANDIDATE_PATHS = List.of(
            "/opt/homebrew/bin/swiftlint",     // Apple Silicon Homebrew
            "/usr/local/bin/swiftlint",         // Intel Homebrew
            "/usr/bin/swiftlint",
            System.getProperty("user.home") + "/.mint/bin/swiftlint",
            System.getProperty("user.home") + "/.local/bin/swiftlint");

    private static final long DEFAULT_TIMEOUT_SECONDS = 300L;

    private final Configuration config;
    private final FileSystem fileSystem;

    public SwiftLintRunner(Configuration config, FileSystem fileSystem) {
        this.config = config;
        this.fileSystem = fileSystem;
    }

    /**
     * If autorun is enabled, runs SwiftLint and returns the report path. If
     * autorun is disabled or SwiftLint cannot be located, returns
     * {@link Optional#empty()}.
     */
    public Optional<Path> runIfRequested(SensorContext context) {
        boolean autorun = config.getBoolean(SwiftPluginConstants.PROP_SWIFTLINT_AUTORUN)
                .orElse(false);
        if (!autorun) {
            LOG.debug("SwiftLint autorun disabled");
            return Optional.empty();
        }

        Optional<Path> binary = locateBinary();
        if (binary.isEmpty()) {
            if (config.getBoolean(SwiftPluginConstants.PROP_SWIFTLINT_AUTO_INSTALL).orElse(false)) {
                LOG.info("SwiftLint not found — attempting auto-install");
                binary = autoInstall();
            }
        }
        if (binary.isEmpty()) {
            LOG.warn(
                "SwiftLint autorun requested but `swiftlint` was not found. "
                + "Install it (`brew install swiftlint`) or disable autorun.");
            return Optional.empty();
        }

        Path outFile = reportOutputPath(context);
        try {
            Files.createDirectories(outFile.getParent());
            int exit = invokeSwiftLint(binary.get(), outFile, context);
            if (exit != 0 && exit != 2) {
                // 0 = clean, 2 = violations found (still useful)
                LOG.warn("SwiftLint exited with non-standard code {} — report may be incomplete", exit);
            }
            if (!Files.exists(outFile) || Files.size(outFile) == 0) {
                LOG.warn("SwiftLint produced no output at {}", outFile);
                return Optional.empty();
            }
            LOG.info("SwiftLint autorun complete: {}", outFile);
            return Optional.of(outFile);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warn("SwiftLint invocation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ---- binary discovery ----

    private Optional<Path> locateBinary() {
        // 1) Explicit override
        Optional<String> explicit = config.get(SwiftPluginConstants.PROP_SWIFTLINT_PATH);
        if (explicit.isPresent()) {
            Path p = Paths.get(explicit.get());
            if (Files.isExecutable(p)) {
                return Optional.of(p);
            }
            LOG.warn("Configured swiftlint path is not executable: {}", p);
        }

        // 2) PATH lookup via `which`
        Optional<Path> onPath = runWhich();
        if (onPath.isPresent()) {
            return onPath;
        }

        // 3) Conventional install dirs
        for (String candidate : CANDIDATE_PATHS) {
            Path p = Paths.get(candidate);
            if (Files.isExecutable(p)) {
                LOG.debug("Found swiftlint at conventional path: {}", p);
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private Optional<Path> runWhich() {
        String which = isWindows() ? "where" : "which";
        try {
            Process p = new ProcessBuilder(which, "swiftlint")
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return Optional.empty();
            }
            if (p.exitValue() != 0 || out.isBlank()) {
                return Optional.empty();
            }
            return Stream.of(out.split("\\R"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(Paths::get)
                    .filter(Files::isExecutable)
                    .findFirst();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    private Optional<Path> autoInstall() {
        // Auto-install only via brew on macOS — pulling in Mint or Homebrew
        // Linuxbrew without explicit consent would be invasive.
        if (!isMac() || !commandExists("brew")) {
            LOG.warn("Auto-install only supported via Homebrew on macOS. Install swiftlint manually.");
            return Optional.empty();
        }
        try {
            LOG.info("Running: brew install swiftlint");
            ProcessBuilder pb = new ProcessBuilder("brew", "install", "swiftlint")
                    .inheritIO();
            Process p = pb.start();
            boolean ok = p.waitFor(600, TimeUnit.SECONDS);
            if (!ok) {
                p.destroyForcibly();
                LOG.warn("brew install swiftlint timed out");
                return Optional.empty();
            }
            if (p.exitValue() == 0) {
                return locateBinary();
            }
            LOG.warn("brew install swiftlint exited {}", p.exitValue());
            return Optional.empty();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warn("Auto-install failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ---- invocation ----

    private int invokeSwiftLint(Path binary, Path outFile, SensorContext context)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(binary.toString());
        cmd.add("lint");
        cmd.add("--reporter");
        cmd.add("json");
        cmd.add("--quiet");

        // Config file — explicit > project root > Homebrew default
        Optional<String> cfgOpt = config.get(SwiftPluginConstants.PROP_SWIFTLINT_CONFIG);
        Path projectConfig = fileSystem.baseDir().toPath().resolve(".swiftlint.yml");
        if (cfgOpt.isPresent()) {
            cmd.add("--config");
            cmd.add(cfgOpt.get());
        } else if (Files.exists(projectConfig)) {
            cmd.add("--config");
            cmd.add(projectConfig.toString());
        }

        // Strict mode escalates warnings to errors at the SwiftLint level.
        if (config.getBoolean(SwiftPluginConstants.PROP_SWIFTLINT_STRICT).orElse(false)) {
            cmd.add("--strict");
        }

        // Restrict to scanned files when we have a small enough set; lets us
        // re-run only on incremental scans without re-scanning the whole tree.
        // We deliberately do NOT pass file paths by default — SwiftLint's own
        // included/excluded config is usually richer than what we'd compute.
        boolean limitToScanned = config.getBoolean(SwiftPluginConstants.PROP_SWIFTLINT_LIMIT_TO_SCANNED)
                .orElse(false);
        if (limitToScanned) {
            fileSystem.inputFiles(
                    fileSystem.predicates().hasLanguage(SwiftPluginConstants.SWIFT_LANGUAGE_KEY))
                    .forEach(f -> cmd.add(f.uri().getPath()));
        }

        LOG.info("Running SwiftLint: {}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(fileSystem.baseDir())
                .redirectErrorStream(false)
                .redirectOutput(outFile.toFile());

        // Suppress noisy SwiftLint stderr unless DEBUG
        if (LOG.isDebugEnabled()) {
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        } else {
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        }

        Process p = pb.start();
        if (!p.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("SwiftLint timed out after " + DEFAULT_TIMEOUT_SECONDS + "s");
        }
        return p.exitValue();
    }

    // ---- helpers ----

    private Path reportOutputPath(SensorContext context) {
        return fileSystem.workDir().toPath()
                .resolve("swiftlint")
                .resolve("swiftlint-autorun.json");
    }

    static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    static boolean commandExists(String cmd) {
        String which = isWindows() ? "where" : "which";
        try {
            Process p = new ProcessBuilder(which, cmd).redirectErrorStream(true).start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
