package io.sonarswift.cli.pipeline.builtin.steps;

import io.sonarswift.cli.pipeline.builtin.BuiltinStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TrivyFsStep implements BuiltinStep {

    @Override public String id() { return "trivy-fs"; }
    @Override public String description() {
        return "Filesystem vulnerability scan using Trivy.";
    }
    @Override public Params params() {
        return Params.of(List.of(),
                List.of("path", "severity", "ignoreUnfixed", "exitCode", "format", "outputPath", "scanners"));
    }

    @Override
    public List<String> render(Map<String, Object> with) {
        List<String> cmd = new ArrayList<>();
        cmd.add("trivy"); cmd.add("fs");

        String severity = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "severity", "HIGH,CRITICAL");
        cmd.add("--severity"); cmd.add(severity);

        if (io.sonarswift.cli.pipeline.builtin.steps.Params.bool(with, "ignoreUnfixed", true)) {
            cmd.add("--ignore-unfixed");
        }
        Object exitCode = with.get("exitCode");
        cmd.add("--exit-code");
        cmd.add(exitCode == null ? "1" : exitCode.toString());

        List<String> scanners = io.sonarswift.cli.pipeline.builtin.steps.Params.list(with, "scanners");
        if (!scanners.isEmpty()) {
            cmd.add("--scanners"); cmd.add(String.join(",", scanners));
        }

        String format = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "format", null);
        if (format != null) { cmd.add("--format"); cmd.add(format); }
        String outputPath = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "outputPath", null);
        if (outputPath != null) { cmd.add("--output"); cmd.add(outputPath); }

        cmd.add(io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "path", "."));
        return cmd;
    }
}
