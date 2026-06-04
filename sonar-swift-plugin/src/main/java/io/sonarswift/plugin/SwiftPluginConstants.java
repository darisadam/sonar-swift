package io.sonarswift.plugin;

import java.util.List;

public final class SwiftPluginConstants {

    private SwiftPluginConstants() {}

    public static final String SWIFT_LANGUAGE_KEY = "swift";
    public static final String SWIFT_LANGUAGE_NAME = "Swift";
    public static final String OBJC_LANGUAGE_KEY = "objc";
    public static final String OBJC_LANGUAGE_NAME = "Objective-C";

    public static final String SWIFT_REPOSITORY_KEY = "swift";
    public static final String OBJC_REPOSITORY_KEY = "objc";

    public static final String SWIFT_REPOSITORY_NAME = "Sonar Swift";
    public static final String OBJC_REPOSITORY_NAME = "Sonar Objective-C";

    public static final String PROFILE_SONAR_WAY = "Sonar way";
    public static final String PROFILE_STRICT = "Strict";
    public static final String PROFILE_SWIFTLINT_COMPAT = "SwiftLint-compat";

    public static final String[] SWIFT_FILE_SUFFIXES_DEFAULT = {".swift"};
    public static final String[] OBJC_FILE_SUFFIXES_DEFAULT = {".h", ".m", ".mm"};

    // ---- property keys exposed to users ----

    public static final String PROP_SWIFT_FILE_SUFFIXES = "sonar.swift.file.suffixes";
    public static final String PROP_OBJC_FILE_SUFFIXES = "sonar.objc.file.suffixes";

    public static final String PROP_SWIFT_PARSER_PATH = "sonar.swift.parser.path";
    public static final String PROP_SWIFT_PARSER_ENABLED = "sonar.swift.parser.enabled";
    public static final String PROP_OBJC_CLANG_PATH = "sonar.objc.clang.path";

    public static final String PROP_SWIFTLINT_REPORT_PATHS = "sonar.swift.swiftlint.reportPaths";
    public static final String PROP_SWIFTLINT_AUTORUN = "sonar.swift.swiftlint.autorun";
    public static final String PROP_SWIFTLINT_AUTO_INSTALL = "sonar.swift.swiftlint.autoInstall";
    public static final String PROP_SWIFTLINT_PATH = "sonar.swift.swiftlint.path";
    public static final String PROP_SWIFTLINT_CONFIG = "sonar.swift.swiftlint.config";
    public static final String PROP_SWIFTLINT_STRICT = "sonar.swift.swiftlint.strict";
    public static final String PROP_SWIFTLINT_LIMIT_TO_SCANNED = "sonar.swift.swiftlint.limitToScanned";
    public static final String PROP_OCLINT_REPORT_PATHS = "sonar.objc.oclint.reportPaths";
    public static final String PROP_PERIPHERY_REPORT_PATH = "sonar.swift.periphery.reportPath";
    public static final String PROP_PERIPHERY_AUTORUN = "sonar.swift.periphery.autorun";

    public static final String PROP_XCCOV_REPORT_PATHS = "sonar.swift.coverage.xccov.reportPaths";
    public static final String PROP_XCRESULT_PATHS = "sonar.swift.coverage.xcresultPaths";
    public static final String PROP_SLATHER_REPORT_PATHS = "sonar.swift.coverage.slather.reportPaths";
    public static final String PROP_COBERTURA_REPORT_PATHS = "sonar.swift.coverage.cobertura.reportPaths";
    public static final String PROP_XCTEST_REPORT_PATHS = "sonar.swift.testExecution.xcresultPaths";

    public static final String PROP_SECRETS_ENTROPY = "sonar.swift.security.secrets.entropyThreshold";

    public static final String PROP_CONCURRENCY_ENABLED = "sonar.swift.concurrency.enabled";
    public static final String PROP_CONCURRENCY_ACTOR_ISOLATION_STRICT = "sonar.swift.concurrency.strictActorIsolation";
    public static final String PROP_GENERIC_ISSUE_REPORT_PATHS = "sonar.swift.externalIssues.reportPaths";
    public static final String PROP_SARIF_EXPORT_PATH = "sonar.swift.export.sarifPath";
    public static final String PROP_DASHBOARD_ENABLED = "sonar.swift.dashboard.enabled";
    public static final String PROP_DASHBOARD_PORT = "sonar.swift.dashboard.port";
    public static final String PROP_LEAK_BUDGET_PATH = "sonar.swift.leak.budgetPath";
    public static final String PROP_LEAK_DETECT_ENABLED = "sonar.swift.leak.enabled";

    public static final List<String> ALL_SWIFT_PROFILES = List.of(
            PROFILE_SONAR_WAY,
            PROFILE_STRICT,
            PROFILE_SWIFTLINT_COMPAT);
}
