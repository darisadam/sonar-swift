package io.sonarswift.cli.pipeline.run;

import io.sonarswift.cli.pipeline.PipelineLoader;
import io.sonarswift.cli.pipeline.model.Pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineRunnerTest {

    private Reporter silentReporter() {
        return new Reporter(new PrintStream(new ByteArrayOutputStream()), false, false);
    }

    @Test
    void runs_sequential_stages_in_topo_order(@TempDir Path tmp) throws IOException {
        Path projectRoot = tmp.resolve("project");
        Files.createDirectories(projectRoot);

        Pipeline p = PipelineLoader.parseYaml("""
                name: order
                stages:
                  - id: a
                    steps: [{ uses: command, with: { run: 'echo a-ran' } }]
                  - id: c
                    needs: [b]
                    steps: [{ uses: command, with: { run: 'echo c-ran' } }]
                  - id: b
                    needs: [a]
                    steps: [{ uses: command, with: { run: 'echo b-ran' } }]
                """);
        PipelineRunner runner = new PipelineRunner(p, projectRoot, silentReporter());
        RunResult result = runner.run();

        assertThat(result.status).isEqualTo(RunResult.Status.PASS);
        // Order in the result reflects topological order (a, b, c).
        List<String> ids = result.stages.stream().map(s -> s.id).toList();
        assertThat(ids).containsExactly("a", "b", "c");
        assertThat(result.stages).allSatisfy(s ->
                assertThat(s.status).isEqualTo(RunResult.Status.PASS));
    }

    @Test
    void failed_stage_skips_dependents_but_not_independents(@TempDir Path tmp) throws IOException {
        Path projectRoot = tmp.resolve("project");
        Files.createDirectories(projectRoot);

        Pipeline p = PipelineLoader.parseYaml("""
                name: skip-on-fail
                stages:
                  - id: a
                    steps: [{ uses: command, with: { run: 'exit 1' } }]
                  - id: b
                    needs: [a]
                    steps: [{ uses: command, with: { run: 'echo b-ran' } }]
                  - id: c
                    steps: [{ uses: command, with: { run: 'echo c-ran' } }]
                """);
        RunResult result = new PipelineRunner(p, projectRoot, silentReporter()).run();

        assertThat(result.status).isEqualTo(RunResult.Status.FAIL);
        RunResult.StageResult a = stage(result, "a");
        RunResult.StageResult b = stage(result, "b");
        RunResult.StageResult c = stage(result, "c");
        assertThat(a.status).isEqualTo(RunResult.Status.FAIL);
        assertThat(b.status).isEqualTo(RunResult.Status.SKIPPED);
        assertThat(b.skippedReason).contains("upstream");
        assertThat(c.status).isEqualTo(RunResult.Status.PASS);
    }

    @Test
    void when_expression_skips_stage(@TempDir Path tmp) throws IOException {
        Path projectRoot = tmp.resolve("project");
        Files.createDirectories(projectRoot);

        Pipeline p = PipelineLoader.parseYaml("""
                name: skip-conditional
                env: { RUN_IT: 'false' }
                stages:
                  - id: a
                    when: ${{ env.RUN_IT != 'false' }}
                    steps: [{ uses: command, with: { run: 'echo should-not-run' } }]
                  - id: b
                    steps: [{ uses: command, with: { run: 'echo ran' } }]
                """);
        RunResult result = new PipelineRunner(p, projectRoot, silentReporter()).run();

        RunResult.StageResult a = stage(result, "a");
        assertThat(a.status).isEqualTo(RunResult.Status.SKIPPED);
        assertThat(a.skippedReason).contains("when:");
        assertThat(stage(result, "b").status).isEqualTo(RunResult.Status.PASS);
    }

    @Test
    void continue_on_error_keeps_pipeline_green(@TempDir Path tmp) throws IOException {
        Path projectRoot = tmp.resolve("project");
        Files.createDirectories(projectRoot);

        Pipeline p = PipelineLoader.parseYaml("""
                name: cont
                stages:
                  - id: maybe-fail
                    continueOnError: true
                    steps: [{ uses: command, with: { run: 'exit 1' } }]
                  - id: after
                    needs: [maybe-fail]
                    steps: [{ uses: command, with: { run: 'echo after' } }]
                """);
        RunResult result = new PipelineRunner(p, projectRoot, silentReporter()).run();

        assertThat(result.status).isEqualTo(RunResult.Status.PASS);
        assertThat(stage(result, "maybe-fail").status).isEqualTo(RunResult.Status.FAIL);
        assertThat(stage(result, "after").status).isEqualTo(RunResult.Status.PASS);
    }

    @Test
    void env_is_substituted_into_run_commands(@TempDir Path tmp) throws IOException {
        Path projectRoot = tmp.resolve("project");
        Files.createDirectories(projectRoot);
        Path marker = projectRoot.resolve("marker.txt");

        Pipeline p = PipelineLoader.parseYaml("""
                name: env-sub
                env: { GREETING: 'sub-worked' }
                stages:
                  - id: a
                    steps:
                      - uses: command
                        with: { run: 'echo ${{ env.GREETING }} > marker.txt' }
                """);
        RunResult result = new PipelineRunner(p, projectRoot, silentReporter()).run();

        assertThat(result.status).isEqualTo(RunResult.Status.PASS);
        assertThat(Files.readString(marker, StandardCharsets.UTF_8)).contains("sub-worked");
    }

    @Test
    void parallel_stage_runs_steps_concurrently(@TempDir Path tmp) throws IOException {
        Path projectRoot = tmp.resolve("project");
        Files.createDirectories(projectRoot);

        // Each step sleeps ~300ms. In parallel, the stage should still finish
        // in well under 1s (3 * 300 = 900ms if sequential).
        Pipeline p = PipelineLoader.parseYaml("""
                name: parallel
                stages:
                  - id: many
                    parallel: true
                    steps:
                      - uses: command
                        with: { run: 'sleep 0.3' }
                      - uses: command
                        with: { run: 'sleep 0.3' }
                      - uses: command
                        with: { run: 'sleep 0.3' }
                """);
        long start = System.currentTimeMillis();
        RunResult result = new PipelineRunner(p, projectRoot, silentReporter()).run();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result.status).isEqualTo(RunResult.Status.PASS);
        // Bound generously to avoid CI flake: parallel must be at least 1.5x faster than serial.
        assertThat(elapsed).isLessThan(700);
    }

    @Test
    void cycle_in_needs_is_rejected(@TempDir Path tmp) throws IOException {
        Path projectRoot = tmp.resolve("project");
        Files.createDirectories(projectRoot);

        Pipeline p = PipelineLoader.parseYaml("""
                name: cycle
                stages:
                  - id: a
                    needs: [b]
                    steps: [{ uses: command, with: { run: 'echo a' } }]
                  - id: b
                    needs: [a]
                    steps: [{ uses: command, with: { run: 'echo b' } }]
                """);
        // The loader allows mutual deps to slip through (we only check self-cycles
        // explicitly). The runner's topo sort catches the cycle.
        PipelineRunner runner = new PipelineRunner(p, projectRoot, silentReporter());
        org.assertj.core.api.Assertions.assertThatThrownBy(runner::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void state_json_is_written(@TempDir Path tmp) throws IOException {
        Path projectRoot = tmp.resolve("project");
        Files.createDirectories(projectRoot);

        Pipeline p = PipelineLoader.parseYaml("""
                name: state-test
                stages:
                  - id: a
                    steps: [{ uses: command, with: { run: 'echo a' } }]
                """);
        RunResult result = new PipelineRunner(p, projectRoot, silentReporter()).run();

        Path runDir = projectRoot.resolve(".sonar/ci").resolve(result.runId);
        Path stateFile = runDir.resolve("state.json");
        assertThat(stateFile).exists();
        String json = Files.readString(stateFile);
        assertThat(json).contains("\"pipelineName\" : \"state-test\"");
        assertThat(json).contains("\"status\" : \"PASS\"");
    }

    private RunResult.StageResult stage(RunResult result, String id) {
        return result.stages.stream()
                .filter(s -> s.id.equals(id))
                .findFirst().orElseThrow();
    }
}
