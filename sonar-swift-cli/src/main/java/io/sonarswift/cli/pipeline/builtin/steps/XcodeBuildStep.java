package io.sonarswift.cli.pipeline.builtin.steps;

import io.sonarswift.cli.pipeline.builtin.BuiltinStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XcodeBuildStep implements BuiltinStep {

    @Override public String id() { return "xcode-build"; }
    @Override public String description() {
        return "xcodebuild build … with the given scheme, workspace/project and destination.";
    }
    @Override public Params params() {
        return Params.of(List.of("scheme"),
                List.of("workspace", "project", "destination", "configuration", "derivedDataPath", "sdk", "quiet"));
    }

    @Override
    public List<String> render(Map<String, Object> with) {
        List<String> cmd = new ArrayList<>();
        cmd.add("xcodebuild");
        cmd.add("build");

        String workspace = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "workspace", null);
        String project = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "project", null);
        if (workspace != null) { cmd.add("-workspace"); cmd.add(workspace); }
        else if (project != null) { cmd.add("-project"); cmd.add(project); }

        cmd.add("-scheme");
        cmd.add(io.sonarswift.cli.pipeline.builtin.steps.Params.stringRequired(with, "scheme", id()));

        String destination = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "destination", "generic/platform=iOS Simulator");
        cmd.add("-destination"); cmd.add(destination);

        String configuration = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "configuration", null);
        if (configuration != null) { cmd.add("-configuration"); cmd.add(configuration); }

        String sdk = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "sdk", null);
        if (sdk != null) { cmd.add("-sdk"); cmd.add(sdk); }

        String ddp = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "derivedDataPath", null);
        if (ddp != null) { cmd.add("-derivedDataPath"); cmd.add(ddp); }

        if (io.sonarswift.cli.pipeline.builtin.steps.Params.bool(with, "quiet", false)) {
            cmd.add("-quiet");
        }
        return cmd;
    }
}
