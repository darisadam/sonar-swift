package io.sonarswift.cli.pipeline.builtin.steps;

import io.sonarswift.cli.pipeline.builtin.BuiltinStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs Xcode unit / UI / integration tests with the same wrapper.
 *
 * <p>Choose what's tested via {@code onlyTesting} (or {@code skipTesting}).
 * Common patterns:</p>
 *
 * <ul>
 *   <li>Unit only: {@code onlyTesting: MyAppTests}</li>
 *   <li>UI only: {@code onlyTesting: MyAppUITests}</li>
 *   <li>Integration only: {@code onlyTesting: MyAppIntegrationTests}</li>
 * </ul>
 */
public class XcodeTestStep implements BuiltinStep {

    @Override public String id() { return "xcode-test"; }
    @Override public String description() {
        return "xcodebuild test … (unit / UI / integration). Supports -only-testing, parallel testing, xcresult capture.";
    }
    @Override public Params params() {
        return Params.of(List.of("scheme"),
                List.of("workspace", "project", "destination", "configuration",
                        "onlyTesting", "skipTesting", "xcresultPath",
                        "enableCodeCoverage", "parallelTesting", "testPlan",
                        "retryCount", "testTimeoutsEnabled", "quiet"));
    }

    @Override
    public List<String> render(Map<String, Object> with) {
        List<String> cmd = new ArrayList<>();
        cmd.add("xcodebuild");
        cmd.add("test");

        String workspace = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "workspace", null);
        String project = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "project", null);
        if (workspace != null) { cmd.add("-workspace"); cmd.add(workspace); }
        else if (project != null) { cmd.add("-project"); cmd.add(project); }

        cmd.add("-scheme");
        cmd.add(io.sonarswift.cli.pipeline.builtin.steps.Params.stringRequired(with, "scheme", id()));

        String destination = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "destination",
                "platform=iOS Simulator,name=iPhone 16,OS=latest");
        cmd.add("-destination"); cmd.add(destination);

        String configuration = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "configuration", null);
        if (configuration != null) { cmd.add("-configuration"); cmd.add(configuration); }

        String testPlan = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "testPlan", null);
        if (testPlan != null) { cmd.add("-testPlan"); cmd.add(testPlan); }

        if (io.sonarswift.cli.pipeline.builtin.steps.Params.bool(with, "enableCodeCoverage", true)) {
            cmd.add("-enableCodeCoverage"); cmd.add("YES");
        }
        if (io.sonarswift.cli.pipeline.builtin.steps.Params.bool(with, "parallelTesting", false)) {
            cmd.add("-parallel-testing-enabled"); cmd.add("YES");
        }

        for (String target : io.sonarswift.cli.pipeline.builtin.steps.Params.list(with, "onlyTesting")) {
            cmd.add("-only-testing:" + target);
        }
        for (String target : io.sonarswift.cli.pipeline.builtin.steps.Params.list(with, "skipTesting")) {
            cmd.add("-skip-testing:" + target);
        }

        Object retryCount = with.get("retryCount");
        if (retryCount != null) {
            cmd.add("-retry-tests-on-failure");
            cmd.add("-test-iterations"); cmd.add(retryCount.toString());
        }

        String xcresult = io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "xcresultPath", null);
        if (xcresult != null) {
            cmd.add("-resultBundlePath"); cmd.add(xcresult);
        }

        if (io.sonarswift.cli.pipeline.builtin.steps.Params.bool(with, "quiet", false)) {
            cmd.add("-quiet");
        }

        return cmd;
    }
}
