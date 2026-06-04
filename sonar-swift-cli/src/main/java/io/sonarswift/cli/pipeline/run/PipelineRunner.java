package io.sonarswift.cli.pipeline.run;

import io.sonarswift.cli.pipeline.builtin.BuiltinRegistry;
import io.sonarswift.cli.pipeline.builtin.BuiltinStep;
import io.sonarswift.cli.pipeline.expr.Expression;
import io.sonarswift.cli.pipeline.model.Pipeline;
import io.sonarswift.cli.pipeline.model.Stage;
import io.sonarswift.cli.pipeline.model.Step;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates execution of a {@link Pipeline}.
 *
 * <p>Algorithm:</p>
 * <ol>
 *   <li>Topologically sort stages by {@code needs:}. Cycle → error.</li>
 *   <li>For each stage (in topo order, sequentially): evaluate {@code when:};
 *       skip if false; otherwise run its steps either sequentially or in
 *       parallel (per {@code parallel:}).</li>
 *   <li>A failed stage marks its dependents as {@code SKIPPED}. Sibling
 *       stages with no dependency on the failed one still execute, unless
 *       the pipeline opts into {@code failFast: true}.</li>
 * </ol>
 *
 * <p>Per-step:</p>
 * <ol>
 *   <li>Merge env: pipeline env ← stage env ← step env ← parent process env</li>
 *   <li>Resolve {@code ${{ env.X }}} substitutions on every string param</li>
 *   <li>Render argv via the built-in registry or build {@code sh -c "<run>"}</li>
 *   <li>Start process; stream stdout/stderr to log file + reporter</li>
 *   <li>Apply per-step timeout</li>
 * </ol>
 */
public final class PipelineRunner {

    private final Pipeline pipeline;
    private final Path projectRoot;
    private final Path runDir;
    private final Reporter reporter;

    public PipelineRunner(Pipeline pipeline, Path projectRoot, Reporter reporter) throws IOException {
        this.pipeline = pipeline;
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.reporter = reporter;
        String runId = UUID.randomUUID().toString().substring(0, 8) + "-"
                + Instant.now().toEpochMilli();
        this.runDir = projectRoot.resolve(".sonar/ci").resolve(runId);
        Files.createDirectories(this.runDir);
    }

    public RunResult run() throws IOException {
        RunResult result = new RunResult();
        result.runId = runDir.getFileName().toString();
        result.pipelineName = pipeline.name;
        result.startedAt = Instant.now();

        List<Stage> ordered = topoOrder(pipeline.stages);
        Map<String, RunResult.StageResult> resultsById = new HashMap<>();
        Set<String> failedStages = new HashSet<>();

        reporter.pipelineStarted(pipeline, result.runId);

        for (Stage stage : ordered) {
            RunResult.StageResult sr = new RunResult.StageResult();
            sr.id = stage.id;
            sr.name = stage.displayName();
            sr.continueOnError = stage.continueOnError;
            sr.startedAt = Instant.now();
            result.stages.add(sr);
            resultsById.put(stage.id, sr);

            // Skip if any dependency failed (unless continueOnError on the failed one)
            boolean upstreamFailed = stage.needs.stream().anyMatch(failedStages::contains);
            Map<String, String> stageEnv = mergeEnv(pipeline.env, stage.env);
            Expression.Context exprCtx = Expression.context(stageEnv, failedStages.isEmpty());

            if (upstreamFailed) {
                sr.status = RunResult.Status.SKIPPED;
                sr.skippedReason = "upstream stage failed";
                sr.finishedAt = Instant.now();
                reporter.stageSkipped(stage, sr.skippedReason);
                continue;
            }
            if (!Expression.isTruthy(stage.when, exprCtx)) {
                sr.status = RunResult.Status.SKIPPED;
                sr.skippedReason = "when:" + stage.when;
                sr.finishedAt = Instant.now();
                reporter.stageSkipped(stage, sr.skippedReason);
                continue;
            }

            reporter.stageStarted(stage);
            boolean stageOk = stage.parallel
                    ? executeParallel(stage, stageEnv, sr)
                    : executeSequential(stage, stageEnv, sr);
            sr.finishedAt = Instant.now();
            sr.durationMs = Duration.between(sr.startedAt, sr.finishedAt).toMillis();

            if (!stageOk && !stage.continueOnError) {
                failedStages.add(stage.id);
                sr.status = RunResult.Status.FAIL;
                reporter.stageFinished(stage, sr);
                if (pipeline.failFast) {
                    break;
                }
            } else {
                sr.status = stageOk ? RunResult.Status.PASS : RunResult.Status.FAIL;
                reporter.stageFinished(stage, sr);
                // When continueOnError is true, do NOT add to failedStages —
                // dependents should still run as if this stage succeeded.
            }
        }

        result.finishedAt = Instant.now();
        result.durationMs = Duration.between(result.startedAt, result.finishedAt).toMillis();
        // Pipeline is FAIL only when a stage fails AND that stage isn't marked
        // continueOnError. Stages marked continueOnError still report FAIL on
        // themselves but don't doom the overall pipeline.
        result.status = result.stages.stream()
                .anyMatch(s -> s.status == RunResult.Status.FAIL && !s.continueOnError)
                ? RunResult.Status.FAIL : RunResult.Status.PASS;

        // Persist state JSON
        Path stateFile = runDir.resolve("state.json");
        Files.writeString(stateFile, RunStateJson.write(result));

        // Also keep a "latest" pointer
        Path latestDir = projectRoot.resolve(".sonar/ci/latest");
        Files.createDirectories(latestDir.getParent());
        Files.writeString(latestDir.getParent().resolve("latest.json"),
                RunStateJson.write(result));

        reporter.pipelineFinished(pipeline, result);
        return result;
    }

    private boolean executeSequential(Stage stage, Map<String, String> stageEnv,
                                      RunResult.StageResult sr) throws IOException {
        boolean ok = true;
        for (Step step : stage.steps) {
            RunResult.StepResult stepResult = runOne(stage, step, stageEnv);
            sr.steps.add(stepResult);
            if (stepResult.status == RunResult.Status.FAIL) {
                ok = false;
                if (!step.continueOnError && !stage.continueOnError) {
                    // Mark remaining steps as skipped
                    for (int j = sr.steps.size(); j < stage.steps.size(); j++) {
                        Step remaining = stage.steps.get(j);
                        RunResult.StepResult skip = new RunResult.StepResult();
                        skip.id = remaining.id;
                        skip.name = remaining.displayName();
                        skip.status = RunResult.Status.SKIPPED;
                        skip.skippedReason = "previous step failed";
                        sr.steps.add(skip);
                    }
                    break;
                }
            }
        }
        return ok;
    }

    private boolean executeParallel(Stage stage, Map<String, String> stageEnv,
                                    RunResult.StageResult sr) throws IOException {
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(stage.steps.size(), Runtime.getRuntime().availableProcessors()));
        List<Future<RunResult.StepResult>> futures = new ArrayList<>();
        for (Step step : stage.steps) {
            futures.add(pool.submit(() -> runOne(stage, step, stageEnv)));
        }
        boolean ok = true;
        for (Future<RunResult.StepResult> f : futures) {
            try {
                RunResult.StepResult r = f.get();
                sr.steps.add(r);
                if (r.status == RunResult.Status.FAIL) ok = false;
            } catch (Exception e) {
                ok = false;
                RunResult.StepResult err = new RunResult.StepResult();
                err.status = RunResult.Status.FAIL;
                err.outputTail = "Step failed to schedule: " + e.getMessage();
                sr.steps.add(err);
            }
        }
        pool.shutdown();
        try {
            pool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return ok;
    }

    private RunResult.StepResult runOne(Stage stage, Step step, Map<String, String> stageEnv) {
        RunResult.StepResult sr = new RunResult.StepResult();
        sr.id = step.id;
        sr.name = step.displayName();
        sr.uses = step.uses;
        sr.startedAt = Instant.now();

        Map<String, String> env = mergeEnv(stageEnv, step.env);
        env.putAll(System.getenv());                          // parent env wins for shells
        sr.env = new LinkedHashMap<>(env);

        Expression.Context exprCtx = Expression.context(env, true);
        if (!Expression.isTruthy(step.when, exprCtx)) {
            sr.status = RunResult.Status.SKIPPED;
            sr.skippedReason = "when:" + step.when;
            sr.finishedAt = Instant.now();
            reporter.stepSkipped(stage, step, sr.skippedReason);
            return sr;
        }

        List<String> argv;
        try {
            argv = renderArgv(step, exprCtx);
        } catch (IllegalArgumentException e) {
            sr.status = RunResult.Status.FAIL;
            sr.outputTail = "Step config error: " + e.getMessage();
            sr.finishedAt = Instant.now();
            reporter.stepFinished(stage, step, sr);
            return sr;
        }
        sr.command = String.join(" ", argv);

        Path stageDir = runDir.resolve(stage.id);
        try { Files.createDirectories(stageDir); } catch (IOException ignored) {}
        Path logFile = stageDir.resolve(step.id + ".log");
        sr.logPath = projectRoot.relativize(logFile).toString();

        reporter.stepStarted(stage, step, sr.command, logFile);

        Path workDir = step.workingDirectory == null || step.workingDirectory.isBlank()
                ? projectRoot
                : projectRoot.resolve(step.workingDirectory).normalize();

        try {
            ProcessBuilder pb = new ProcessBuilder(argv)
                    .directory(workDir.toFile())
                    .redirectErrorStream(true);
            pb.environment().putAll(env);

            Process p = pb.start();
            // Stream output to log file + reporter
            StringBuilder tail = new StringBuilder();
            try (InputStream is = p.getInputStream()) {
                byte[] buf = new byte[4096];
                try (var fos = Files.newOutputStream(logFile)) {
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                        if (tail.length() < 4096) {
                            tail.append(new String(buf, 0, Math.min(n, 4096 - tail.length()),
                                    StandardCharsets.UTF_8));
                        }
                        reporter.stepOutput(stage, step,
                                new String(buf, 0, n, StandardCharsets.UTF_8));
                    }
                }
            }
            boolean finished = p.waitFor(step.timeout, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                sr.status = RunResult.Status.FAIL;
                sr.outputTail = "Timed out after " + step.timeout + "s";
            } else {
                sr.exitCode = p.exitValue();
                sr.outputTail = tail.toString();
                sr.status = sr.exitCode == 0
                        ? RunResult.Status.PASS
                        : (step.continueOnError ? RunResult.Status.PASS : RunResult.Status.FAIL);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            sr.status = RunResult.Status.FAIL;
            sr.outputTail = "Exception: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        sr.finishedAt = Instant.now();
        sr.durationMs = Duration.between(sr.startedAt, sr.finishedAt).toMillis();
        reporter.stepFinished(stage, step, sr);
        return sr;
    }

    // ---- helpers ----

    private List<Stage> topoOrder(List<Stage> stages) {
        Map<String, Stage> byId = new LinkedHashMap<>();
        for (Stage s : stages) byId.put(s.id, s);

        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> outEdges = new HashMap<>();
        for (Stage s : stages) {
            inDegree.put(s.id, s.needs == null ? 0 : s.needs.size());
            outEdges.put(s.id, new ArrayList<>());
        }
        for (Stage s : stages) {
            if (s.needs == null) continue;
            for (String dep : s.needs) {
                outEdges.computeIfAbsent(dep, k -> new ArrayList<>()).add(s.id);
            }
        }

        List<Stage> out = new ArrayList<>();
        List<String> queue = new ArrayList<>();
        for (var e : inDegree.entrySet()) if (e.getValue() == 0) queue.add(e.getKey());
        while (!queue.isEmpty()) {
            String id = queue.remove(0);
            out.add(byId.get(id));
            for (String next : outEdges.getOrDefault(id, List.of())) {
                int v = inDegree.get(next) - 1;
                inDegree.put(next, v);
                if (v == 0) queue.add(next);
            }
        }
        if (out.size() != stages.size()) {
            throw new IllegalStateException("Pipeline contains a cycle in stage dependencies");
        }
        return out;
    }

    private Map<String, String> mergeEnv(Map<String, String> a, Map<String, String> b) {
        Map<String, String> out = new LinkedHashMap<>();
        if (a != null) out.putAll(a);
        if (b != null) out.putAll(b);
        return out;
    }

    private List<String> renderArgv(Step step, Expression.Context exprCtx) {
        List<String> argv = new ArrayList<>();
        if (step.uses != null && !step.uses.isBlank()) {
            BuiltinStep builtin = BuiltinRegistry.lookup(step.uses);
            if (builtin == null) {
                throw new IllegalArgumentException(
                        "Unknown built-in step `" + step.uses + "`. "
                        + "List builtins with: sonar-swift pipeline list --builtins");
            }
            Map<String, Object> resolved = resolveParams(step.with, exprCtx);
            argv.addAll(builtin.render(resolved));
        } else {
            // Literal shell command
            String resolved = Expression.substitute(step.run, exprCtx);
            argv.add("sh");
            argv.add("-c");
            argv.add(resolved);
        }
        // Substitute env placeholders in argv tokens too
        List<String> finalArgv = new ArrayList<>(argv.size());
        for (String a : argv) {
            finalArgv.add(Expression.substitute(a, exprCtx));
        }
        return finalArgv;
    }

    private Map<String, Object> resolveParams(Map<String, Object> with, Expression.Context exprCtx) {
        if (with == null) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : with.entrySet()) {
            out.put(e.getKey(), resolveValue(e.getValue(), exprCtx));
        }
        return out;
    }

    private Object resolveValue(Object v, Expression.Context exprCtx) {
        if (v == null) return null;
        if (v instanceof String s) return Expression.substitute(s, exprCtx);
        if (v instanceof List<?> l) {
            return l.stream().map(item -> resolveValue(item, exprCtx)).toList();
        }
        return v;
    }

    @SuppressWarnings("unused")
    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
