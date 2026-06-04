package io.sonarswift.cli.pipeline.builtin.steps;

import io.sonarswift.cli.pipeline.builtin.BuiltinStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SemgrepStep implements BuiltinStep {

    @Override public String id() { return "semgrep"; }
    @Override public String description() {
        return "SAST scan using Semgrep rule packs (auto / p/security / custom).";
    }
    @Override public Params params() {
        return Params.of(List.of(),
                List.of("config", "severity", "paths", "exclude", "outputPath", "format", "errorOnFindings"));
    }

    @Override
    public List<String> render(Map<String, Object> with) {
        List<String> cmd = new ArrayList<>();
        cmd.add("semgrep");
        cmd.add("scan");

        String config = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "config", "auto");
        cmd.add("--config"); cmd.add(config);

        String severity = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "severity", null);
        if (severity != null) { cmd.add("--severity"); cmd.add(severity); }

        for (String e : io.sonarswift.cli.pipeline.builtin.steps.Params.list(with, "exclude")) {
            cmd.add("--exclude"); cmd.add(e);
        }

        String outputPath = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "outputPath", null);
        String format = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "format", null);
        if (outputPath != null) {
            cmd.add("--output"); cmd.add(outputPath);
        }
        if (format != null) {
            cmd.add("--" + format);
        }
        if (io.sonarswift.cli.pipeline.builtin.steps.Params.bool(with, "errorOnFindings", true)) {
            cmd.add("--error");
        }

        List<String> paths = io.sonarswift.cli.pipeline.builtin.steps.Params.list(with, "paths");
        if (paths.isEmpty()) paths = List.of(".");
        cmd.addAll(paths);
        return cmd;
    }
}
