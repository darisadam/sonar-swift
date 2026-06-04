package io.sonarswift.cli.dashboard;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lightweight HTTP dashboard for sonar-swift. Bundled in the CLI jar so users
 * get it without installing anything extra.
 *
 * <h2>Pages</h2>
 * <ul>
 *   <li>{@code /} — overview: latest scan, SonarQube link, quick actions</li>
 *   <li>{@code /scans} — local scan history (reads .sonar/ci/local-run.json)</li>
 *   <li>{@code /leaks} — memory leak reports (.sonar/leaks/*.json)</li>
 *   <li>{@code /precommit} — recent pre-commit runs</li>
 *   <li>{@code /settings} — show resolved config</li>
 * </ul>
 *
 * <h2>API</h2>
 * <ul>
 *   <li>POST {@code /api/scan} — kick off a scan (proxies to sonar-scanner)</li>
 *   <li>GET  {@code /api/projects} — list SonarQube projects (proxies SQ API)</li>
 *   <li>GET  {@code /api/health} — own health check + SonarQube reachability</li>
 *   <li>GET  {@code /api/leaks/latest} — latest leak report JSON</li>
 *   <li>GET  {@code /api/ci/runs} — last 20 local CI runs</li>
 *   <li>POST {@code /api/leaks/budget/accept} — accept the current measurement as new budget</li>
 * </ul>
 *
 * <h2>Design notes</h2>
 * <p>Uses only {@code com.sun.net.httpserver} (built into the JDK), no external
 * web framework — keeps the jar small and reduces transitive-dependency
 * surface area in the supply chain.</p>
 *
 * <p>HTML assets are read from the classpath under {@code dashboard/} so the
 * console works whether the jar is run directly or extracted.</p>
 *
 * <p>HTTP server is bound to {@code 127.0.0.1} by default to keep the
 * console off the network. {@code --bind 0.0.0.0} for docker / VM scenarios.</p>
 */
public final class DashboardServer {

    private final Config config;
    private final HttpClient http;
    private HttpServer server;

    public DashboardServer(Config config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void start() throws IOException {
        InetSocketAddress address = new InetSocketAddress(config.bindHost, config.port);
        server = HttpServer.create(address, 0);

        // static pages
        server.createContext("/", new StaticHandler(this, "index"));
        server.createContext("/scans", new StaticHandler(this, "scans"));
        server.createContext("/leaks", new StaticHandler(this, "leaks"));
        server.createContext("/precommit", new StaticHandler(this, "precommit"));
        server.createContext("/settings", new StaticHandler(this, "settings"));
        server.createContext("/pipeline", new StaticHandler(this, "pipeline"));

        // assets
        server.createContext("/assets/", new AssetHandler());

        // API endpoints
        server.createContext("/api/health", this::handleHealth);
        server.createContext("/api/projects", this::handleProjects);
        server.createContext("/api/scan", this::handleScan);
        server.createContext("/api/leaks/latest", this::handleLeakLatest);
        server.createContext("/api/leaks/history", this::handleLeakHistory);
        server.createContext("/api/leaks/budget/accept", this::handleLeakBudgetAccept);
        server.createContext("/api/ci/runs", this::handleCiRuns);
        server.createContext("/api/precommit/runs", this::handlePrecommitRuns);
        server.createContext("/api/config", this::handleConfig);
        server.createContext("/api/pipeline/latest", this::handlePipelineLatest);
        server.createContext("/api/pipeline/runs", this::handlePipelineRuns);
        server.createContext("/api/pipeline/run/", this::handlePipelineRun);
        server.createContext("/api/pipeline/trigger", this::handlePipelineTrigger);

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    public String baseUrl() {
        return "http://" + config.bindHost + ":" + config.port;
    }

    // ---------- handlers ----------

    private void handleHealth(HttpExchange ex) throws IOException {
        Map<String, Object> out = new HashMap<>();
        out.put("status", "up");
        out.put("now", Instant.now().toString());
        out.put("sonarqube", checkSonarHealth());
        writeJson(ex, 200, out);
    }

    private Map<String, Object> checkSonarHealth() {
        Map<String, Object> sq = new HashMap<>();
        try {
            HttpResponse<String> r = http.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(config.sonarUrl + "/api/system/status"))
                            .timeout(Duration.ofSeconds(3))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            sq.put("reachable", r.statusCode() == 200);
            sq.put("body", r.body());
        } catch (Exception e) {
            sq.put("reachable", false);
            sq.put("error", e.getMessage());
        }
        return sq;
    }

    private void handleProjects(HttpExchange ex) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.sonarUrl + "/api/projects/search"))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            writeRaw(ex, r.statusCode(), "application/json", r.body());
        } catch (Exception e) {
            writeJson(ex, 502, Map.of("error", e.getMessage()));
        }
    }

    private void handleScan(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "POST required"));
            return;
        }
        Map<String, String> body = readForm(ex);
        String project = body.getOrDefault("project", config.workspace.toString());
        ProcessBuilder pb = new ProcessBuilder(
                config.scannerBinary, "scan", project)
                .redirectErrorStream(true)
                .directory(config.workspace.toFile());
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        try (InputStream is = p.getInputStream()) {
            sb.append(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
        try {
            int exit = p.waitFor();
            writeJson(ex, exit == 0 ? 200 : 500,
                    Map.of("exit", exit, "output", sb.toString()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writeJson(ex, 500, Map.of("error", "interrupted"));
        }
    }

    private void handleLeakLatest(HttpExchange ex) throws IOException {
        Path latest = config.workspace.resolve(".sonar/leaks/latest.json");
        writeFileOrEmpty(ex, latest);
    }

    private void handleLeakHistory(HttpExchange ex) throws IOException {
        Path dir = config.workspace.resolve(".sonar/leaks");
        List<Map<String, Object>> entries = listJsonReports(dir, 50);
        writeJson(ex, 200, Map.of("entries", entries));
    }

    private void handleLeakBudgetAccept(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "POST required"));
            return;
        }
        Path latest = config.workspace.resolve(".sonar/leaks/latest.json");
        Path budget = config.workspace.resolve(".sonar/leaks/budget.json");
        if (!Files.exists(latest)) {
            writeJson(ex, 404, Map.of("error", "no latest leak report to accept"));
            return;
        }
        Files.createDirectories(budget.getParent());
        Files.copy(latest, budget, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        writeJson(ex, 200, Map.of("ok", true, "budget", budget.toString()));
    }

    private void handleCiRuns(HttpExchange ex) throws IOException {
        Path latest = config.workspace.resolve(".sonar/ci/local-run.json");
        writeFileOrEmpty(ex, latest);
    }

    private void handlePrecommitRuns(HttpExchange ex) throws IOException {
        Path dir = config.workspace.resolve(".sonar/precommit");
        writeJson(ex, 200, Map.of("entries", listJsonReports(dir, 20)));
    }

    private void handlePipelineLatest(HttpExchange ex) throws IOException {
        Path latest = config.workspace.resolve(".sonar/ci/latest.json");
        writeFileOrEmpty(ex, latest);
    }

    private void handlePipelineRuns(HttpExchange ex) throws IOException {
        // Each pipeline run lives in its own sub-directory of .sonar/ci/ with a state.json inside.
        Path ciRoot = config.workspace.resolve(".sonar/ci");
        if (!Files.isDirectory(ciRoot)) {
            writeJson(ex, 200, Map.of("entries", List.of()));
            return;
        }
        try (Stream<Path> stream = Files.list(ciRoot)) {
            List<Map<String, Object>> entries = stream
                    .filter(Files::isDirectory)
                    .filter(d -> Files.exists(d.resolve("state.json")))
                    .sorted((a, b) -> {
                        try { return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a)); }
                        catch (IOException e) { return 0; }
                    })
                    .limit(20)
                    .map(d -> {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("runId", d.getFileName().toString());
                        try {
                            entry.put("mtime", Files.getLastModifiedTime(d).toString());
                            String state = Files.readString(d.resolve("state.json"));
                            entry.put("state", state);
                        } catch (IOException e) {
                            entry.put("error", e.getMessage());
                        }
                        return entry;
                    })
                    .collect(Collectors.toList());
            writeJson(ex, 200, Map.of("entries", entries));
        }
    }

    private void handlePipelineRun(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        // /api/pipeline/run/<runId>[/step/<stage>/<step>]
        String prefix = "/api/pipeline/run/";
        if (!path.startsWith(prefix)) {
            writeJson(ex, 404, Map.of("error", "not found"));
            return;
        }
        String rest = path.substring(prefix.length());
        String[] parts = rest.split("/");
        if (parts.length == 0 || parts[0].isBlank()) {
            writeJson(ex, 400, Map.of("error", "missing runId"));
            return;
        }
        String runId = parts[0];
        Path runDir = config.workspace.resolve(".sonar/ci").resolve(runId);

        if (parts.length >= 4 && "step".equals(parts[1])) {
            // /step/<stage>/<step> — return the step's log file
            Path log = runDir.resolve(parts[2]).resolve(parts[3] + ".log");
            if (!Files.exists(log)) {
                writeJson(ex, 404, Map.of("error", "log not found"));
                return;
            }
            byte[] bytes = Files.readAllBytes(log);
            ex.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
            return;
        }

        // Otherwise: return the run's state.json
        Path stateFile = runDir.resolve("state.json");
        if (!Files.exists(stateFile)) {
            writeJson(ex, 404, Map.of("error", "no such run"));
            return;
        }
        byte[] bytes = Files.readAllBytes(stateFile);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handlePipelineTrigger(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            writeJson(ex, 405, Map.of("error", "POST required"));
            return;
        }
        // Spawns the CLI's pipeline run subcommand in the background.
        Map<String, String> body = readForm(ex);
        String stage = body.get("stage");
        List<String> cmd = new ArrayList<>(List.of(
                config.scannerBinary, "pipeline", "run", "--root", config.workspace.toString()));
        if (stage != null && !stage.isBlank()) {
            cmd.add("--stage"); cmd.add(stage);
        }
        try {
            new ProcessBuilder(cmd).start();
            writeJson(ex, 202, Map.of("ok", true, "started", cmd));
        } catch (IOException e) {
            writeJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleConfig(HttpExchange ex) throws IOException {
        writeJson(ex, 200, Map.of(
                "sonarUrl", config.sonarUrl,
                "workspace", config.workspace.toString(),
                "bindHost", config.bindHost,
                "port", config.port,
                "scannerBinary", config.scannerBinary));
    }

    private List<Map<String, Object>> listJsonReports(Path dir, int limit) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                        } catch (IOException e) { return 0; }
                    })
                    .limit(limit)
                    .map(p -> {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("path", p.getFileName().toString());
                        try {
                            entry.put("size", Files.size(p));
                            entry.put("mtime", Files.getLastModifiedTime(p).toString());
                            entry.put("preview", previewLines(p, 5));
                        } catch (IOException e) {
                            entry.put("error", e.getMessage());
                        }
                        return entry;
                    })
                    .collect(Collectors.toList());
        }
    }

    private String previewLines(Path file, int n) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .limit(n)
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "(unable to preview: " + e.getMessage() + ")";
        }
    }

    // ---------- output helpers ----------

    private static void writeJson(HttpExchange ex, int status, Map<String, Object> body) throws IOException {
        writeRaw(ex, status, "application/json; charset=utf-8", JsonWriter.write(body));
    }

    private static void writeRaw(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", contentType);
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void writeFileOrEmpty(HttpExchange ex, Path file) throws IOException {
        if (!Files.exists(file)) {
            writeJson(ex, 200, Map.of("entries", List.of()));
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> readForm(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> out = new HashMap<>();
        if (body.isBlank()) return out;
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            out.put(k, v);
        }
        return out;
    }

    // ---------- handlers for static files ----------

    private static final class StaticHandler implements HttpHandler {
        private final DashboardServer server;
        private final String page;

        StaticHandler(DashboardServer server, String page) {
            this.server = server;
            this.page = page;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            // /scans (and similar) should match exactly; "/" must also match exactly,
            // else we'll shadow the more specific contexts.
            if (page.equals("index") && !"/".equals(path)) {
                writeRaw(ex, 404, "text/plain", "404");
                return;
            }
            String resource = "dashboard/" + page + ".html";
            try (InputStream is = DashboardServer.class.getClassLoader().getResourceAsStream(resource)) {
                if (is == null) {
                    writeRaw(ex, 404, "text/plain", "404 — missing " + resource);
                    return;
                }
                String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                html = html.replace("{{baseUrl}}", server.baseUrl())
                            .replace("{{sonarUrl}}", server.config.sonarUrl);
                writeRaw(ex, 200, "text/html; charset=utf-8", html);
            }
        }
    }

    private static final class AssetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            // path is /assets/foo.css — map to classpath dashboard/assets/foo.css
            String resource = "dashboard" + path;
            try (InputStream is = DashboardServer.class.getClassLoader().getResourceAsStream(resource)) {
                if (is == null) {
                    writeRaw(ex, 404, "text/plain", "404");
                    return;
                }
                String contentType = guessContentType(path);
                byte[] bytes = is.readAllBytes();
                ex.getResponseHeaders().add("Content-Type", contentType);
                ex.getResponseHeaders().add("Cache-Control", "public, max-age=300");
                ex.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }

        private String guessContentType(String path) {
            if (path.endsWith(".css")) return "text/css; charset=utf-8";
            if (path.endsWith(".js"))  return "application/javascript; charset=utf-8";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".png")) return "image/png";
            return "application/octet-stream";
        }
    }

    // ---------- config ----------

    /** Mutable dashboard configuration. Populated by {@link io.sonarswift.cli.Main}. */
    public static final class Config {
        public String bindHost = "127.0.0.1";
        public int port = 8080;
        public String sonarUrl = Optional.ofNullable(System.getenv("SONAR_HOST_URL")).orElse("http://localhost:9000");
        public Path workspace = Paths.get(".").toAbsolutePath().normalize();
        public String scannerBinary = "sonar-swift";
    }
}
