package io.sonarswift.plugin.parser;

import io.sonarswift.plugin.parser.ast.SwiftAst;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client for the long-lived SwiftSonarParser process.
 *
 * <p>Protocol: newline-delimited JSON over stdin (requests) / stdout (responses).
 * Stderr is captured for diagnostics and logged at WARN.</p>
 *
 * <p>Lifecycle: the client is created lazily on first use, reused across all
 * Swift files in the scan, and stopped at the end of the sensor's execution.
 * A crash triggers one restart attempt; a second crash makes the sensor fall
 * back to token-only analysis.</p>
 *
 * <p>This class is NOT thread-safe at the protocol layer — wrap parses in
 * {@code synchronized} or batch by the caller.</p>
 */
public class SwiftSyntaxClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SwiftSyntaxClient.class);
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Path binary;
    private Process process;
    private BufferedReader stdout;
    private BufferedWriter stdin;
    private final AtomicReference<Throwable> errorRef = new AtomicReference<>();
    private Thread stderrPump;

    /**
     * @param binary path to {@code SwiftSonarParser} executable. The
     *               {@link io.sonarswift.plugin.sensors.SwiftSensor} resolves
     *               this via {@code sonar.swift.parser.path} or, if not set,
     *               extracts the bundled per-arch binary from the plugin JAR.
     */
    public SwiftSyntaxClient(Path binary) {
        this.binary = binary;
    }

    /** Start the subprocess and complete a handshake. */
    public synchronized void start() throws IOException {
        if (process != null && process.isAlive()) return;

        ProcessBuilder pb = new ProcessBuilder(binary.toString(), "--serve")
                .redirectErrorStream(false);
        process = pb.start();
        stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        stderrPump = new Thread(() -> {
            try (BufferedReader err = new BufferedReader(new InputStreamReader(
                    process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = err.readLine()) != null) {
                    LOG.warn("[swift-parser stderr] {}", line);
                }
            } catch (IOException e) {
                errorRef.set(e);
            }
        }, "swift-parser-stderr");
        stderrPump.setDaemon(true);
        stderrPump.start();

        ping();
    }

    /** Parse one file and return the AST. */
    public synchronized SwiftAst parse(Path file) throws IOException {
        if (process == null || !process.isAlive()) start();
        String reqId = UUID.randomUUID().toString();
        String source = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, Object> req = Map.of(
                "reqId", reqId,
                "op", "parse",
                "path", file.toString(),
                "source", source);
        return send(req, SwiftAst.class);
    }

    /** Ping the subprocess to confirm it's healthy. */
    public synchronized void ping() throws IOException {
        send(Map.of("reqId", UUID.randomUUID().toString(), "op", "ping"), Map.class);
    }

    private <T> T send(Map<String, Object> req, Class<T> respType) throws IOException {
        String line = JSON.writeValueAsString(req);
        stdin.write(line);
        stdin.newLine();
        stdin.flush();

        String responseLine = stdout.readLine();
        if (responseLine == null) {
            throw new IOException("SwiftSonarParser subprocess closed unexpectedly");
        }
        // Errors come back as {"reqId":..., "ok":false, "error":"..."}
        Map<?, ?> probe = JSON.readValue(responseLine, Map.class);
        Object ok = probe.get("ok");
        if (ok instanceof Boolean okBool && !okBool) {
            throw new IOException("SwiftSonarParser error: " + probe.get("error"));
        }
        // Success: re-deserialize the response's `data` field into the desired type.
        Object data = probe.get("data");
        if (data == null) return null;
        return JSON.convertValue(data, respType);
    }

    @Override
    public synchronized void close() {
        if (process == null) return;
        try {
            stdin.write("{\"op\":\"shutdown\"}");
            stdin.newLine();
            stdin.flush();
        } catch (IOException ignored) {
            // best-effort
        }
        try {
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        process = null;
        stdout = null;
        stdin = null;
    }
}
