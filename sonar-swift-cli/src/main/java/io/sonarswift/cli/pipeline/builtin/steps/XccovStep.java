package io.sonarswift.cli.pipeline.builtin.steps;

import io.sonarswift.cli.pipeline.builtin.BuiltinStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XccovStep implements BuiltinStep {

    @Override public String id() { return "xccov"; }
    @Override public String description() {
        return "Extract coverage JSON from an .xcresult bundle with xcrun xccov view.";
    }
    @Override public Params params() {
        return Params.of(List.of("xcresultPath"), List.of("outputPath"));
    }

    @Override
    public List<String> render(Map<String, Object> with) {
        String xcresult = io.sonarswift.cli.pipeline.builtin.steps.Params.stringRequired(with, "xcresultPath", id());
        String output = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "outputPath", "build/coverage.json");
        // The xcrun xccov view command prints to stdout — we use the literal `run:` form
        // would be more natural for shell redirection, but we want it as a built-in.
        // Solution: invoke `sh -c "xcrun xccov view ... > out"` via PipelineRunner.
        // Here we render the redirection-included shell.
        List<String> cmd = new ArrayList<>();
        cmd.add("sh"); cmd.add("-c");
        cmd.add("xcrun xccov view --report --json " + shellQuote(xcresult)
                + " > " + shellQuote(output));
        return cmd;
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
