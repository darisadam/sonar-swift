package io.sonarswift.cli.pipeline.builtin.steps;

import io.sonarswift.cli.pipeline.builtin.BuiltinStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GitleaksStep implements BuiltinStep {

    @Override public String id() { return "gitleaks"; }
    @Override public String description() {
        return "Scan the git history (or working tree) for committed secrets using gitleaks.";
    }
    @Override public Params params() {
        return Params.of(List.of(),
                List.of("mode", "config", "redact", "verbose", "source", "reportPath", "reportFormat"));
    }

    @Override
    public List<String> render(Map<String, Object> with) {
        List<String> cmd = new ArrayList<>();
        cmd.add("gitleaks");
        String mode = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "mode", "detect");
        cmd.add(mode);
        cmd.add("--no-banner");
        if (io.sonarswift.cli.pipeline.builtin.steps.Params.bool(with, "redact", true)) cmd.add("--redact");
        if (io.sonarswift.cli.pipeline.builtin.steps.Params.bool(with, "verbose", false)) cmd.add("--verbose");

        String config = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "config", null);
        if (config != null) { cmd.add("--config"); cmd.add(config); }

        String source = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "source", null);
        if (source != null) { cmd.add("--source"); cmd.add(source); }

        String reportPath = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "reportPath",
                "build/gitleaks-report.json");
        String reportFormat = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "reportFormat", "json");
        cmd.add("--report-path"); cmd.add(reportPath);
        cmd.add("--report-format"); cmd.add(reportFormat);
        return cmd;
    }
}
