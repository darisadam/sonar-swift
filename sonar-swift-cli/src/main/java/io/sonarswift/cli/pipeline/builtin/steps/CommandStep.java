package io.sonarswift.cli.pipeline.builtin.steps;

import io.sonarswift.cli.pipeline.builtin.BuiltinStep;

import java.util.List;
import java.util.Map;

/**
 * Built-in equivalent of the top-level {@code run:} field. Lets a step say
 * {@code uses: command} explicitly when the {@code run:} form would be confusing.
 */
public class CommandStep implements BuiltinStep {

    @Override public String id() { return "command"; }
    @Override public String description() {
        return "Explicit literal command. Same effect as the top-level `run:` form.";
    }
    @Override public Params params() {
        return Params.of(List.of("run"), List.of());
    }

    @Override
    public List<String> render(Map<String, Object> with) {
        String run = io.sonarswift.cli.pipeline.builtin.steps.Params.stringRequired(with, "run", id());
        return List.of("sh", "-c", run);
    }
}
