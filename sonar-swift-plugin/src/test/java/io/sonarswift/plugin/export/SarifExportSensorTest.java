package io.sonarswift.plugin.export;

import io.sonarswift.plugin.SwiftPluginConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.internal.MapSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SarifExportSensorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeEach
    void resetTap() {
        IssueTap.clear();
    }

    @AfterEach
    void cleanTap() {
        IssueTap.clear();
    }

    @Test
    void writes_sarif_with_findings_recorded_in_the_tap(@TempDir Path tmp) throws IOException {
        Path projectRoot = tmp.resolve("project");
        Files.createDirectories(projectRoot.resolve("Sources"));
        Path src = projectRoot.resolve("Sources/Foo.swift");
        Files.writeString(src, "let x = optional!\n");
        Path out = tmp.resolve("out").resolve("sonar-swift.sarif");

        IssueTap.record(new IssueTap.Finding(
                "S1001", "CRITICAL", "BUG", "force unwrap", src.toString(), 1, 1));
        IssueTap.record(new IssueTap.Finding(
                "S1001", "CRITICAL", "BUG", "force unwrap again", src.toString(), 5, 5));
        IssueTap.record(new IssueTap.Finding(
                "S1904", "MAJOR", "BUG", "task self", src.toString(), 12, 14));

        SensorContextTester context = SensorContextTester.create(projectRoot);
        context.setSettings(new MapSettings()
                .setProperty(SwiftPluginConstants.PROP_SARIF_EXPORT_PATH, out.toString()));

        new SarifExportSensor().execute(context);

        assertThat(out).exists();
        JsonNode root = JSON.readTree(out.toFile());

        assertThat(root.path("version").asText()).isEqualTo("2.1.0");
        JsonNode run = root.path("runs").get(0);
        assertThat(run.path("tool").path("driver").path("name").asText())
                .isEqualTo("sonar-swift");

        // Two unique rules
        JsonNode rules = run.path("tool").path("driver").path("rules");
        assertThat(rules.size()).isEqualTo(2);
        assertThat(rules.get(0).path("id").asText()).isEqualTo("S1001");
        assertThat(rules.get(1).path("id").asText()).isEqualTo("S1904");

        // Three results — S1001 used twice, S1904 once
        JsonNode results = run.path("results");
        assertThat(results.size()).isEqualTo(3);
        assertThat(results.get(0).path("ruleId").asText()).isEqualTo("S1001");
        assertThat(results.get(0).path("level").asText()).isEqualTo("error"); // CRITICAL
        assertThat(results.get(2).path("level").asText()).isEqualTo("warning"); // MAJOR
    }

    @Test
    void is_noop_when_export_path_is_unset(@TempDir Path tmp) throws IOException {
        Path projectRoot = tmp.resolve("project");
        Files.createDirectories(projectRoot);
        Path out = tmp.resolve("out.sarif");

        IssueTap.record(new IssueTap.Finding(
                "S1001", "CRITICAL", "BUG", "x", projectRoot.resolve("a.swift").toString(), 1, 1));

        SensorContextTester context = SensorContextTester.create(projectRoot);
        // no property — sensor's onlyWhenConfiguration would normally short-circuit;
        // execute() itself also guards, so calling it directly returns without writing.
        new SarifExportSensor().execute(context);

        assertThat(out).doesNotExist();
    }
}
