package io.sonarswift.cli.pipeline.builtin.steps;

import io.sonarswift.cli.pipeline.builtin.BuiltinStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScriptStep implements BuiltinStep {

    @Override public String id() { return "script"; }
    @Override public String description() {
        return "Invoke a script file (relative to project root). Optionally with arguments.";
    }
    @Override public Params params() {
        return Params.of(List.of("path"), List.of("args"));
    }

    @Override
    public List<String> render(Map<String, Object> with) {
        List<String> cmd = new ArrayList<>();
        cmd.add(io.sonarswift.cli.pipeline.builtin.steps.Params.stringRequired(with, "path", id()));
        cmd.addAll(io.sonarswift.cli.pipeline.builtin.steps.Params.list(with, "args"));
        return cmd;
    }
}
