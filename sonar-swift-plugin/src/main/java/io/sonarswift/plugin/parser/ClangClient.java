package io.sonarswift.plugin.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Spawns {@code clang -Xclang -ast-dump=json -fsyntax-only} per Objective-C
 * file and parses the JSON output into a {@link JsonNode} tree.
 *
 * <p>We deliberately don't bind a strongly-typed AST (clang's AST schema is
 * not formally stable). Checks navigate the {@link JsonNode} tree with
 * defensive accessors.</p>
 */
public class ClangClient {

    private static final Logger LOG = LoggerFactory.getLogger(ClangClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path clangBinary;
    private final List<String> extraArgs;

    public ClangClient(Path clangBinary, List<String> extraArgs) {
        this.clangBinary = clangBinary;
        this.extraArgs = List.copyOf(extraArgs);
    }

    /**
     * Run clang on the given file and return the parsed AST root.
     * Returns {@code null} if clang failed (we log and continue —
     * one bad ObjC file shouldn't break the whole scan).
     */
    public JsonNode parse(Path file) {
        List<String> cmd = new ArrayList<>();
        cmd.add(clangBinary.toString());
        cmd.add("-Xclang");
        cmd.add("-ast-dump=json");
        cmd.add("-fsyntax-only");
        cmd.addAll(extraArgs);
        cmd.add(file.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(false);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            LOG.warn("Failed to launch clang for {}: {}", file, e.getMessage());
            return null;
        }
        try {
            JsonNode root = JSON.readTree(p.getInputStream());
            // Drain stderr separately to avoid blocking
            try (var err = p.getErrorStream()) {
                err.readAllBytes();
            }
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                LOG.warn("clang AST dump timed out for {}", file);
                return null;
            }
            if (p.exitValue() != 0) {
                LOG.debug("clang exited with code {} for {} (file may have errors)", p.exitValue(), file);
                // Still return what we got — clang dumps a partial AST on errors.
            }
            return root;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            LOG.warn("clang parse failed for {}: {}", file, e.getMessage());
            return null;
        }
    }

    /** Resolve the clang binary path: {@code xcrun --find clang} → {@code /usr/bin/clang}. */
    public static Path resolveClang(String configured) {
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        // Try xcrun
        try {
            Process p = new ProcessBuilder("xcrun", "--find", "clang").start();
            if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                String out = new String(p.getInputStream().readAllBytes()).trim();
                if (!out.isEmpty() && Files.isExecutable(Path.of(out))) {
                    return Path.of(out);
                }
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        // Fallback
        Path fallback = Path.of("/usr/bin/clang");
        if (Files.isExecutable(fallback)) return fallback;
        return null;
    }
}
