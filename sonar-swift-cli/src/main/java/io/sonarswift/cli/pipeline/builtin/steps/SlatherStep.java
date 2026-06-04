package io.sonarswift.cli.pipeline.builtin.steps;

import io.sonarswift.cli.pipeline.builtin.BuiltinStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SlatherStep implements BuiltinStep {

    @Override public String id() { return "slather"; }
    @Override public String description() {
        return "Generate Cobertura coverage XML via Slather.";
    }
    @Override public Params params() {
        return Params.of(List.of("project", "scheme"),
                List.of("workspace", "outputDirectory", "binary", "configuration", "buildDirectory"));
    }

    @Override
    public List<String> render(Map<String, Object> with) {
        List<String> cmd = new ArrayList<>();
        cmd.add("slather"); cmd.add("coverage"); cmd.add("--cobertura-xml");

        String workspace = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "workspace", null);
        if (workspace != null) { cmd.add("--workspace"); cmd.add(workspace); }

        cmd.add("--scheme");
        cmd.add(io.sonarswift.cli.pipeline.builtin.steps.Params.stringRequired(with, "scheme", id()));

        String outputDir = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "outputDirectory", "build/slather");
        cmd.add("--output-directory"); cmd.add(outputDir);

        String binary = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "binary", null);
        if (binary != null) { cmd.add("--binary-file"); cmd.add(binary); }

        String configuration = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "configuration", null);
        if (configuration != null) { cmd.add("--configuration"); cmd.add(configuration); }

        String buildDirectory = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "buildDirectory", null);
        if (buildDirectory != null) { cmd.add("--build-directory"); cmd.add(buildDirectory); }

        cmd.add(io.sonarswift.cli.pipeline.builtin.steps.Params.stringRequired(with, "project", id()));
        return cmd;
    }
}
