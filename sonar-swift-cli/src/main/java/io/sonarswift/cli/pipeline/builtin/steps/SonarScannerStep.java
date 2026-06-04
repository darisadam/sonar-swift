package io.sonarswift.cli.pipeline.builtin.steps;

import io.sonarswift.cli.pipeline.builtin.BuiltinStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SonarScannerStep implements BuiltinStep {

    @Override public String id() { return "sonar-scanner"; }
    @Override public String description() {
        return "Invoke sonar-scanner with the sonar-swift defaults plus any properties passed via `properties:`.";
    }
    @Override public Params params() {
        return Params.of(List.of(),
                List.of("hostUrl", "token", "projectKey", "projectName", "sources",
                        "swiftLintReportPaths", "xccovReportPaths", "xcresultPaths",
                        "slatherReportPaths", "coberturaReportPaths", "peripheryReportPath",
                        "exportSarifPath", "extra"));
    }

    @Override
    public List<String> render(Map<String, Object> with) {
        List<String> cmd = new ArrayList<>();
        cmd.add("sonar-scanner");

        addIfPresent(cmd, "sonar.host.url",
                io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "hostUrl", "http://localhost:9000"));
        addIfPresent(cmd, "sonar.token",
                io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "token", null));
        addIfPresent(cmd, "sonar.projectKey",
                io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "projectKey", null));
        addIfPresent(cmd, "sonar.projectName",
                io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "projectName", null));
        addIfPresent(cmd, "sonar.sources",
                io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "sources", null));

        addList(cmd, "sonar.swift.swiftlint.reportPaths",
                io.sonarswift.cli.pipeline.builtin.steps.Params.list(with, "swiftLintReportPaths"));
        addList(cmd, "sonar.swift.coverage.xccov.reportPaths",
                io.sonarswift.cli.pipeline.builtin.steps.Params.list(with, "xccovReportPaths"));
        addList(cmd, "sonar.swift.coverage.xcresultPaths",
                io.sonarswift.cli.pipeline.builtin.steps.Params.list(with, "xcresultPaths"));
        addList(cmd, "sonar.swift.coverage.slather.reportPaths",
                io.sonarswift.cli.pipeline.builtin.steps.Params.list(with, "slatherReportPaths"));
        addList(cmd, "sonar.swift.coverage.cobertura.reportPaths",
                io.sonarswift.cli.pipeline.builtin.steps.Params.list(with, "coberturaReportPaths"));
        addIfPresent(cmd, "sonar.swift.periphery.reportPath",
                io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "peripheryReportPath", null));
        addIfPresent(cmd, "sonar.swift.export.sarifPath",
                io.sonarswift.cli.pipeline.builtin.steps.Params.string(with, "exportSarifPath", null));

        // extra: free-form list of `-Dkey=value` pairs
        for (String extra : io.sonarswift.cli.pipeline.builtin.steps.Params.list(with, "extra")) {
            cmd.add(extra.startsWith("-D") ? extra : "-D" + extra);
        }
        return cmd;
    }

    private static void addIfPresent(List<String> cmd, String key, String value) {
        if (value != null && !value.isBlank()) {
            cmd.add("-D" + key + "=" + value);
        }
    }

    private static void addList(List<String> cmd, String key, List<String> values) {
        if (!values.isEmpty()) {
            cmd.add("-D" + key + "=" + String.join(",", values));
        }
    }
}
