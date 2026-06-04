package io.sonarswift.cli.pipeline.builtin.steps;

import io.sonarswift.cli.pipeline.builtin.BuiltinStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DependencyCheckStep implements BuiltinStep {

    @Override public String id() { return "dependency-check"; }
    @Override public String description() {
        return "OWASP Dependency-Check scan. Honors --failOnCVSS threshold.";
    }
    @Override public Params params() {
        return Params.of(List.of(),
                List.of("project", "scan", "format", "outputDirectory", "failOnCVSS", "suppression", "nvdApiKey"));
    }

    @Override
    public List<String> render(Map<String, Object> with) {
        List<String> cmd = new ArrayList<>();
        cmd.add("dependency-check");

        cmd.add("--project");
        cmd.add(io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "project", "sonar-swift-target"));
        cmd.add("--scan");
        cmd.add(io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "scan", "."));

        String format = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "format", "JSON");
        cmd.add("--format"); cmd.add(format);

        String outputDirectory = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "outputDirectory",
                "build/dependency-check");
        cmd.add("--out"); cmd.add(outputDirectory);

        Object failOnCvss = with.get("failOnCVSS");
        cmd.add("--failOnCVSS");
        cmd.add(failOnCvss == null ? "7" : failOnCvss.toString());

        String suppression = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "suppression", null);
        if (suppression != null) { cmd.add("--suppression"); cmd.add(suppression); }
        String nvdKey = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "nvdApiKey", null);
        if (nvdKey != null) { cmd.add("--nvdApiKey"); cmd.add(nvdKey); }
        return cmd;
    }
}
