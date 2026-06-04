package io.sonarswift.cli.pipeline.builtin.steps;

import io.sonarswift.cli.pipeline.builtin.BuiltinStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SwiftLintStep implements BuiltinStep {

    @Override public String id() { return "swiftlint"; }
    @Override public String description() {
        return "Run SwiftLint with the project's .swiftlint.yml. Supports --strict and a reporter override.";
    }
    @Override public Params params() {
        return Params.of(List.of(), List.of("config", "strict", "reporter", "paths", "mode"));
    }

    @Override
    public List<String> render(Map<String, Object> with) {
        List<String> cmd = new ArrayList<>();
        cmd.add("swiftlint");
        // mode: lint | analyze | fix
        String mode = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "mode", "lint");
        cmd.add(mode);

        String config = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "config", null);
        if (config != null) { cmd.add("--config"); cmd.add(config); }

        String reporter = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "reporter", "emoji");
        cmd.add("--reporter"); cmd.add(reporter);

        if (io.sonarswift.cli.pipeline.builtin.steps.Params.bool(with, "strict", false)) {
            cmd.add("--strict");
        }
        cmd.add("--quiet");

        for (String p : io.sonarswift.cli.pipeline.builtin.steps.Params.list(with, "paths")) {
            cmd.add(p);
        }
        return cmd;
    }
}
