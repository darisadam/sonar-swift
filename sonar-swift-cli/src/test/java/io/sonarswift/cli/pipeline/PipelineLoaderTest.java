package io.sonarswift.cli.pipeline;

import io.sonarswift.cli.pipeline.model.Pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineLoaderTest {

    @Test
    void loads_a_well_formed_pipeline() throws IOException {
        Pipeline p = PipelineLoader.parseYaml("""
                name: tiny
                env: { A: '1' }
                stages:
                  - id: first
                    steps:
                      - uses: command
                        with: { run: echo hi }
                  - id: second
                    needs: [first]
                    steps:
                      - uses: command
                        with: { run: echo two }
                """);
        assertThat(p.name).isEqualTo("tiny");
        assertThat(p.env).containsEntry("A", "1");
        assertThat(p.stages).hasSize(2);
        assertThat(p.stages.get(0).steps.get(0).uses).isEqualTo("command");
        // Default step id is backfilled from uses
        assertThat(p.stages.get(0).steps.get(0).id).isEqualTo("command");
        assertThat(p.stages.get(1).needs).containsExactly("first");
    }

    @Test
    void unknown_fields_are_ignored() throws IOException {
        // Future-proofing: a new field added by a newer version of the schema
        // mustn't break parsing in older runners.
        Pipeline p = PipelineLoader.parseYaml("""
                name: x
                somethingNew: true
                stages:
                  - id: a
                    extraStageField: hello
                    steps:
                      - uses: command
                        with: { run: echo }
                """);
        assertThat(p.name).isEqualTo("x");
        assertThat(p.stages).hasSize(1);
    }

    @Test
    void rejects_duplicate_stage_ids() {
        assertThatThrownBy(() -> PipelineLoader.parseYaml("""
                name: x
                stages:
                  - id: same
                    steps: [{ uses: command, with: { run: echo } }]
                  - id: same
                    steps: [{ uses: command, with: { run: echo } }]
                """)).hasMessageContaining("duplicate stage id");
    }

    @Test
    void rejects_stage_without_steps() {
        assertThatThrownBy(() -> PipelineLoader.parseYaml("""
                name: x
                stages:
                  - id: empty
                """)).hasMessageContaining("no `steps`");
    }

    @Test
    void rejects_step_with_both_uses_and_run() {
        assertThatThrownBy(() -> PipelineLoader.parseYaml("""
                name: x
                stages:
                  - id: a
                    steps:
                      - uses: command
                        run: echo
                """)).hasMessageContaining("exactly one of `uses:` or `run:`");
    }

    @Test
    void rejects_step_with_neither_uses_nor_run() {
        assertThatThrownBy(() -> PipelineLoader.parseYaml("""
                name: x
                stages:
                  - id: a
                    steps:
                      - name: 'nothing here'
                """)).hasMessageContaining("exactly one of `uses:` or `run:`");
    }

    @Test
    void rejects_needs_pointing_at_unknown_stage() {
        assertThatThrownBy(() -> PipelineLoader.parseYaml("""
                name: x
                stages:
                  - id: a
                    needs: [does-not-exist]
                    steps: [{ uses: command, with: { run: echo } }]
                """)).hasMessageContaining("unknown stage");
    }

    @Test
    void rejects_self_dependency() {
        assertThatThrownBy(() -> PipelineLoader.parseYaml("""
                name: x
                stages:
                  - id: loop
                    needs: [loop]
                    steps: [{ uses: command, with: { run: echo } }]
                """)).hasMessageContaining("depends on itself");
    }

    @Test
    void backfills_timeout_when_zero() throws IOException {
        Pipeline p = PipelineLoader.parseYaml("""
                name: x
                stages:
                  - id: a
                    steps:
                      - uses: command
                        with: { run: echo }
                """);
        assertThat(p.stages.get(0).steps.get(0).timeout).isEqualTo(1800);
    }

    @Test
    void find_default_returns_existing_file(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve(".sonar-swift"));
        Path target = tmp.resolve(".sonar-swift/pipeline.yml");
        Files.writeString(target, "name: x\nstages: [{ id: a, steps: [{ uses: command, with: { run: echo } }] }]\n");
        assertThat(PipelineLoader.findDefault(tmp)).isEqualTo(target);
    }

    @Test
    void find_default_returns_null_when_no_file(@TempDir Path tmp) {
        assertThat(PipelineLoader.findDefault(tmp)).isNull();
    }
}
