package io.sonarswift.cli.pipeline.builtin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuiltinRegistryTest {

    @Test
    void registry_exposes_all_expected_builtins() {
        List<String> ids = BuiltinRegistry.all().stream().map(BuiltinStep::id).toList();
        assertThat(ids).contains(
                "swiftlint", "swift-format",
                "xcode-build", "xcode-test",
                "xccov", "slather",
                "gitleaks", "trufflehog",
                "semgrep", "trivy-fs", "dependency-check",
                "sonar-scanner", "sonar-swift-quick",
                "command", "script");
    }

    @Test
    void swiftlint_renders_strict_with_reporter_and_paths() {
        BuiltinStep s = BuiltinRegistry.lookup("swiftlint");
        List<String> cmd = s.render(Map.of(
                "strict", true,
                "reporter", "github-actions-logging",
                "paths", List.of("Sources", "Tests")));
        assertThat(cmd).startsWith("swiftlint", "lint");
        assertThat(cmd).contains("--strict", "--reporter", "github-actions-logging", "Sources", "Tests");
    }

    @Test
    void xcode_test_handles_only_testing_and_xcresult() {
        BuiltinStep s = BuiltinRegistry.lookup("xcode-test");
        List<String> cmd = s.render(Map.of(
                "workspace", "MyApp.xcworkspace",
                "scheme", "MyApp",
                "destination", "platform=iOS Simulator,name=iPhone 16",
                "onlyTesting", List.of("MyAppTests", "MyAppUITests"),
                "xcresultPath", "build/r.xcresult"));
        assertThat(cmd).startsWith("xcodebuild", "test");
        assertThat(cmd).contains("-workspace", "MyApp.xcworkspace");
        assertThat(cmd).contains("-scheme", "MyApp");
        assertThat(cmd).contains("-only-testing:MyAppTests", "-only-testing:MyAppUITests");
        assertThat(cmd).contains("-resultBundlePath", "build/r.xcresult");
    }

    @Test
    void xcode_build_requires_scheme() {
        BuiltinStep s = BuiltinRegistry.lookup("xcode-build");
        assertThatThrownBy(() -> s.render(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scheme");
    }

    @Test
    void xccov_wraps_in_shell_redirection() {
        BuiltinStep s = BuiltinRegistry.lookup("xccov");
        List<String> cmd = s.render(Map.of(
                "xcresultPath", "build/r.xcresult",
                "outputPath", "build/cov.json"));
        assertThat(cmd).startsWith("sh", "-c");
        // Redirection is part of the shell payload, not argv[3]+
        assertThat(cmd.get(2)).contains("xcrun xccov view --report --json");
        assertThat(cmd.get(2)).contains("build/r.xcresult");
        assertThat(cmd.get(2)).contains("> 'build/cov.json'");
    }

    @Test
    void gitleaks_uses_redact_by_default() {
        List<String> cmd = BuiltinRegistry.lookup("gitleaks").render(Map.of());
        assertThat(cmd).startsWith("gitleaks", "detect");
        assertThat(cmd).contains("--redact", "--no-banner");
    }

    @Test
    void trivy_default_severity_is_high_critical() {
        List<String> cmd = BuiltinRegistry.lookup("trivy-fs").render(Map.of());
        assertThat(cmd).startsWith("trivy", "fs");
        int idx = cmd.indexOf("--severity");
        assertThat(idx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(idx + 1)).isEqualTo("HIGH,CRITICAL");
        assertThat(cmd).contains("--ignore-unfixed");
    }

    @Test
    void semgrep_default_config_is_auto() {
        List<String> cmd = BuiltinRegistry.lookup("semgrep").render(Map.of());
        assertThat(cmd).contains("--config", "auto");
        assertThat(cmd).endsWith(".");
    }

    @Test
    void dependency_check_default_failOnCvss_is_seven() {
        List<String> cmd = BuiltinRegistry.lookup("dependency-check").render(Map.of(
                "project", "MyApp"));
        int idx = cmd.indexOf("--failOnCVSS");
        assertThat(idx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(idx + 1)).isEqualTo("7");
    }

    @Test
    void sonar_scanner_emits_property_flags() {
        List<String> cmd = BuiltinRegistry.lookup("sonar-scanner").render(Map.of(
                "hostUrl", "http://localhost:9000",
                "token", "tk",
                "projectKey", "ios",
                "xccovReportPaths", List.of("build/cov.json")));
        assertThat(cmd).startsWith("sonar-scanner");
        assertThat(cmd).contains(
                "-Dsonar.host.url=http://localhost:9000",
                "-Dsonar.token=tk",
                "-Dsonar.projectKey=ios",
                "-Dsonar.swift.coverage.xccov.reportPaths=build/cov.json");
    }

    @Test
    void command_wraps_run_in_sh_minus_c() {
        List<String> cmd = BuiltinRegistry.lookup("command").render(Map.of(
                "run", "echo hello && echo world"));
        assertThat(cmd).containsExactly("sh", "-c", "echo hello && echo world");
    }

    @Test
    void unknown_id_returns_null() {
        assertThat(BuiltinRegistry.lookup("no-such-step")).isNull();
    }
}
