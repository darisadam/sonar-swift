package io.sonarswift.cli.pipeline.builtin.steps;

import io.sonarswift.cli.pipeline.builtin.BuiltinStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SwiftFormatStep implements BuiltinStep {

    @Override public String id() { return "swift-format"; }
    @Override public String description() {
        return "Run Apple's swift-format in lint or format mode against the supplied paths.";
    }
    @Override public Params params() {
        return Params.of(List.of(), List.of("mode", "configuration", "paths", "strict", "recursive"));
    }

    @Override
    public List<String> render(Map<String, Object> with) {
        List<String> cmd = new ArrayList<>();
        cmd.add("swift-format");
        String mode = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "mode", "lint");
        cmd.add(mode);

        if (io.sonarswift.cli.pipeline.builtin.steps.Params.bool(with, "recursive", true)) {
            cmd.add("--recursive");
        }
        String config = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "configuration", null);
        if (config != null) { cmd.add("--configuration"); cmd.add(config); }
        if (io.sonarswift.cli.pipeline.builtin.steps.Params.bool(with, "strict", false)) {
            cmd.add("--strict");
        }
        List<String> paths = io.sonarswift.cli.pipeline.builtin.steps.Params.list(with, "paths");
        if (paths.isEmpty()) paths = List.of("Sources");
        cmd.addAll(paths);
        return cmd;
    }
}
