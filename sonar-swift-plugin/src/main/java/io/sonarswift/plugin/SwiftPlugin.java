package io.sonarswift.plugin;

import io.sonarswift.plugin.coverage.CoberturaCoverageSensor;
import io.sonarswift.plugin.coverage.SlatherCoverageSensor;
import io.sonarswift.plugin.coverage.XccovCoverageSensor;
import io.sonarswift.plugin.export.SarifExportSensor;
import io.sonarswift.plugin.external.GenericIssueSensor;
import io.sonarswift.plugin.external.OCLintSensor;
import io.sonarswift.plugin.external.PeripherySensor;
import io.sonarswift.plugin.external.SwiftLintSensor;
import io.sonarswift.plugin.language.ObjectiveC;
import io.sonarswift.plugin.language.Swift;
import io.sonarswift.plugin.leak.LeakDetectionSensor;
import io.sonarswift.plugin.profile.ObjectiveCQualityProfile;
import io.sonarswift.plugin.profile.SwiftQualityProfile;
import io.sonarswift.plugin.rules.ObjectiveCRulesDefinition;
import io.sonarswift.plugin.rules.SwiftRulesDefinition;
import io.sonarswift.plugin.sensors.ObjectiveCSensor;
import io.sonarswift.plugin.sensors.SwiftSensor;
import io.sonarswift.plugin.test.XcresultTestExecutionSensor;

import org.sonar.api.Plugin;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.resources.Qualifiers;

import static io.sonarswift.plugin.SwiftPluginConstants.*;

/**
 * Entry point for the SonarQube plugin. Registers all extensions
 * (languages, profiles, rules, sensors, configurable properties).
 *
 * <p>This class is referenced by name in {@code sonar-packaging-maven-plugin}'s
 * {@code <pluginClass>} configuration in pom.xml. Renaming requires updating
 * that POM.</p>
 */
public class SwiftPlugin implements Plugin {

    private static final String CATEGORY = "Sonar Swift";
    private static final String SUBCAT_GENERAL = "General";
    private static final String SUBCAT_PARSER = "Native parser";
    private static final String SUBCAT_EXTERNAL = "External tools";
    private static final String SUBCAT_COVERAGE = "Code coverage";
    private static final String SUBCAT_SECURITY = "Security";
    private static final String SUBCAT_CONCURRENCY = "Concurrency";
    private static final String SUBCAT_EXPORT = "Export & diagnostics";

    @Override
    public void define(Context context) {
        context.addExtensions(
                // languages
                Swift.class,
                ObjectiveC.class,

                // rules + profiles
                SwiftRulesDefinition.class,
                ObjectiveCRulesDefinition.class,
                SwiftQualityProfile.class,
                ObjectiveCQualityProfile.class,

                // sensors — native analysis
                SwiftSensor.class,
                ObjectiveCSensor.class,

                // sensors — external tools
                SwiftLintSensor.class,
                OCLintSensor.class,
                PeripherySensor.class,
                GenericIssueSensor.class,

                // sensors — coverage + test execution
                XccovCoverageSensor.class,
                SlatherCoverageSensor.class,
                CoberturaCoverageSensor.class,
                XcresultTestExecutionSensor.class,

                // sensors — export / diagnostics
                SarifExportSensor.class,
                LeakDetectionSensor.class);

        context.addExtensions(properties());
    }

    private static java.util.List<PropertyDefinition> properties() {
        return java.util.List.of(
                PropertyDefinition.builder(PROP_SWIFT_FILE_SUFFIXES)
                        .name("Swift file suffixes")
                        .description("Comma-separated list of suffixes for files to analyze as Swift.")
                        .defaultValue(String.join(",", SWIFT_FILE_SUFFIXES_DEFAULT))
                        .multiValues(true)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_GENERAL)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_OBJC_FILE_SUFFIXES)
                        .name("Objective-C file suffixes")
                        .description("Comma-separated list of suffixes for files to analyze as Objective-C.")
                        .defaultValue(String.join(",", OBJC_FILE_SUFFIXES_DEFAULT))
                        .multiValues(true)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_GENERAL)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_SWIFT_PARSER_ENABLED)
                        .name("Enable native Swift parser")
                        .description("If true, spawn the bundled SwiftSonarParser binary to compute the AST. "
                                + "Disable to fall back to external-tool-only analysis (faster startup, fewer findings).")
                        .defaultValue("true")
                        .type(PropertyType.BOOLEAN)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_PARSER)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_SWIFT_PARSER_PATH)
                        .name("SwiftSonarParser binary path")
                        .description("Override the bundled SwiftSonarParser binary. Useful for development.")
                        .category(CATEGORY)
                        .subCategory(SUBCAT_PARSER)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_OBJC_CLANG_PATH)
                        .name("clang binary path")
                        .description("Override which clang is used for Objective-C AST analysis. "
                                + "Defaults to `xcrun --find clang` on macOS, `/usr/bin/clang` elsewhere.")
                        .category(CATEGORY)
                        .subCategory(SUBCAT_PARSER)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_SWIFTLINT_REPORT_PATHS)
                        .name("SwiftLint report paths")
                        .description("Comma-separated paths to SwiftLint JSON reports "
                                + "(generated by `swiftlint lint --reporter json`).")
                        .multiValues(true)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXTERNAL)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_SWIFTLINT_AUTORUN)
                        .name("Auto-run SwiftLint")
                        .description("Invoke `swiftlint` automatically during the scan. "
                                + "Requires `swiftlint` on PATH or set the path explicitly via "
                                + PROP_SWIFTLINT_PATH + ".")
                        .defaultValue("false")
                        .type(PropertyType.BOOLEAN)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXTERNAL)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_SWIFTLINT_AUTO_INSTALL)
                        .name("Auto-install SwiftLint if missing")
                        .description("When autorun is enabled and SwiftLint is not found, "
                                + "try `brew install swiftlint`. macOS only.")
                        .defaultValue("false")
                        .type(PropertyType.BOOLEAN)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXTERNAL)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_SWIFTLINT_PATH)
                        .name("SwiftLint binary path")
                        .description("Explicit path to the `swiftlint` executable. Skips PATH lookup.")
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXTERNAL)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_SWIFTLINT_CONFIG)
                        .name("SwiftLint config path")
                        .description("Explicit path to `.swiftlint.yml`. Defaults to the project root file if present.")
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXTERNAL)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_SWIFTLINT_STRICT)
                        .name("SwiftLint strict mode")
                        .description("Pass `--strict` to SwiftLint — escalates warnings to errors.")
                        .defaultValue("false")
                        .type(PropertyType.BOOLEAN)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXTERNAL)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_SWIFTLINT_LIMIT_TO_SCANNED)
                        .name("Limit SwiftLint to scanned files")
                        .description("Pass each scanned Swift file explicitly to SwiftLint instead of letting it discover its own targets.")
                        .defaultValue("false")
                        .type(PropertyType.BOOLEAN)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXTERNAL)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_OCLINT_REPORT_PATHS)
                        .name("OCLint report paths")
                        .description("Comma-separated paths to OCLint PMD XML reports.")
                        .multiValues(true)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXTERNAL)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_PERIPHERY_REPORT_PATH)
                        .name("Periphery report path")
                        .description("Path to a Periphery JSON report for dead-code analysis.")
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXTERNAL)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_PERIPHERY_AUTORUN)
                        .name("Auto-run Periphery")
                        .description("Invoke `periphery scan` automatically during the scan. "
                                + "Requires `periphery` on PATH and a buildable workspace.")
                        .defaultValue("false")
                        .type(PropertyType.BOOLEAN)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXTERNAL)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_XCCOV_REPORT_PATHS)
                        .name("xccov JSON report paths")
                        .description("Comma-separated paths to xccov JSON reports "
                                + "(`xcrun xccov view --report --json *.xcresult`).")
                        .multiValues(true)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_COVERAGE)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_XCRESULT_PATHS)
                        .name("xcresult bundle paths")
                        .description("Comma-separated paths to .xcresult bundles. "
                                + "The plugin runs xccov itself. Requires `xcrun` on PATH.")
                        .multiValues(true)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_COVERAGE)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_SLATHER_REPORT_PATHS)
                        .name("Slather XML report paths")
                        .description("Comma-separated paths to Slather coverage XML.")
                        .multiValues(true)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_COVERAGE)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_COBERTURA_REPORT_PATHS)
                        .name("Cobertura XML report paths")
                        .description("Comma-separated paths to Cobertura coverage XML.")
                        .multiValues(true)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_COVERAGE)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_XCTEST_REPORT_PATHS)
                        .name("xcresult paths for test execution")
                        .description("Same .xcresult bundles parsed for test counts (tests, failures, errors, skipped, duration).")
                        .multiValues(true)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_COVERAGE)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_SECRETS_ENTROPY)
                        .name("Secret-detection entropy threshold")
                        .description("Shannon entropy threshold (bits/char) for the heuristic secret detector. "
                                + "Higher = fewer false positives, more missed secrets.")
                        .defaultValue("4.5")
                        .type(PropertyType.FLOAT)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_SECURITY)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                // ---- concurrency ----
                PropertyDefinition.builder(PROP_CONCURRENCY_ENABLED)
                        .name("Enable Swift Concurrency rules")
                        .description("If true, the concurrency rule pack (Sendable, MainActor, actor isolation, "
                                + "Task retain cycles, missing await) is active.")
                        .defaultValue("true")
                        .type(PropertyType.BOOLEAN)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_CONCURRENCY)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_CONCURRENCY_ACTOR_ISOLATION_STRICT)
                        .name("Strict actor-isolation checks")
                        .description("Apply stricter actor-isolation analysis (matches Swift 6 strict concurrency mode).")
                        .defaultValue("false")
                        .type(PropertyType.BOOLEAN)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_CONCURRENCY)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                // ---- export ----
                PropertyDefinition.builder(PROP_GENERIC_ISSUE_REPORT_PATHS)
                        .name("Generic external issue reports")
                        .description("Comma-separated paths to Sonar generic external-issue JSON files.")
                        .multiValues(true)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXPORT)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_SARIF_EXPORT_PATH)
                        .name("SARIF export path")
                        .description("If set, sonar-swift writes a SARIF 2.1.0 report at this path summarising "
                                + "every native-check finding produced during the scan.")
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXPORT)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                // ---- diagnostics ----
                PropertyDefinition.builder(PROP_LEAK_DETECT_ENABLED)
                        .name("Enable scanner self-diagnostics")
                        .description("Writes .sonar/leaks/latest.json after each scan with JVM heap, GC and "
                                + "parser-sidecar memory metrics. Compared against the recorded budget.")
                        .defaultValue("false")
                        .type(PropertyType.BOOLEAN)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXPORT)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_LEAK_BUDGET_PATH)
                        .name("Leak budget file path")
                        .description("Override the location of the leak-budget JSON. Defaults to .sonar/leaks/budget.json.")
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXPORT)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_DASHBOARD_ENABLED)
                        .name("Dashboard auto-start")
                        .description("Whether the bundled dashboard subcommand should auto-start when "
                                + "the CLI is invoked without args.")
                        .defaultValue("false")
                        .type(PropertyType.BOOLEAN)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXPORT)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build(),

                PropertyDefinition.builder(PROP_DASHBOARD_PORT)
                        .name("Dashboard port")
                        .description("Port the local dashboard binds to (default 8080).")
                        .defaultValue("8080")
                        .type(PropertyType.INTEGER)
                        .category(CATEGORY)
                        .subCategory(SUBCAT_EXPORT)
                        .onQualifiers(Qualifiers.PROJECT)
                        .build());
    }
}
