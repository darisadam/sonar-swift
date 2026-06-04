package io.sonarswift.cli.pipeline.builtin.steps;

import io.sonarswift.cli.pipeline.builtin.BuiltinStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TrufflehogStep implements BuiltinStep {

    @Override public String id() { return "trufflehog"; }
    @Override public String description() {
        return "Secret detection across filesystem / git history using TruffleHog.";
    }
    @Override public Params params() {
        return Params.of(List.of(),
                List.of("mode", "path", "onlyVerified", "branch", "since", "json", "fail"));
    }

    @Override
    public List<String> render(Map<String, Object> with) {
        List<String> cmd = new ArrayList<>();
        cmd.add("trufflehog");
        String mode = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "mode", "filesystem");
        cmd.add(mode);

        if ("filesystem".equals(mode)) {
            cmd.add(io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "path", "."));
        } else if ("git".equals(mode)) {
            cmd.add(io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "path", "file://."));
            String branch = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "branch", null);
            if (branch != null) { cmd.add("--branch"); cmd.add(branch); }
            String since = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "since", null);
            if (since != null) { cmd.add("--since-commit"); cmd.add(since); }
        }

        if (io.sonarswift.cli.pipeline.builtin.steps.Params.bool(with, "onlyVerified", true)) {
            cmd.add("--only-verified");
        }
        if (io.sonarswift.cli.pipeline.builtin.steps.Params.bool(with, "json", true)) {
            cmd.add("--json");
        }
        if (io.sonarswift.cli.pipeline.builtin.steps.Params.bool(with, "fail", true)) {
            cmd.add("--fail");
        }
        return cmd;
    }
}
