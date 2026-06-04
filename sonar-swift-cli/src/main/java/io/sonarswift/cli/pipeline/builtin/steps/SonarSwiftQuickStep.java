package io.sonarswift.cli.pipeline.builtin.steps;

import io.sonarswift.cli.pipeline.builtin.BuiltinStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SonarSwiftQuickStep implements BuiltinStep {

    @Override public String id() { return "sonar-swift-quick"; }
    @Override public String description() {
        return "Lightweight native scan via `sonar-swift quick-scan` — same checks pre-commit uses.";
    }
    @Override public Params params() {
        return Params.of(List.of(), List.of("paths", "format", "allChecks"));
    }

    @Override
    public List<String> render(Map<String, Object> with) {
        List<String> cmd = new ArrayList<>();
        // We assume `sonar-swift` is on PATH (installed) OR the dashboard run
        // wires in the right jar via the env. The pipeline runner doesn't
        // self-invoke the CLI directly — it shells out so users can override.
        cmd.add("sonar-swift");
        cmd.add("quick-scan");
        String format = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "format", "compiler");
        cmd.add("--format=" + format);
        if (io.sonarswift.cli.pipeline.builtin.steps.Params.bool(with, "allChecks", false)) {
            cmd.add("--all-checks");
        }
        List<String> paths = io.sonarswift.cli.pipeline.builtin.steps.Params.list(with, "paths");
        if (paths.isEmpty()) paths = List.of(".");
        cmd.addAll(paths);
        return cmd;
    }
}
