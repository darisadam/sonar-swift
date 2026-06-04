# SonarQube feature coverage

Mapping from each SonarQube capability to where it lives in sonar-swift.

## Native

| Feature                       | Where                                                                 |
| ----------------------------- | --------------------------------------------------------------------- |
| Issue detection               | `rules/swift/**` + `rules/objc/**` + concurrency pack                 |
| Cyclomatic / cognitive cplx   | `metrics/CyclomaticComplexityCalculator` + `CognitiveComplexityCalculator` |
| LOC / NCLOC / comments        | `metrics/LinesCalculator`                                             |
| Functions / classes / stmts   | `metrics/StructuralMetricsCalculator`                                 |
| Duplications (CPD)            | `sensors/SwiftSensor#emitCpdTokens`                                   |
| Test execution                | `test/XcresultTestExecutionSensor` (NEW)                              |

## External tools

| Tool        | Sensor                       | Trigger                                                              |
| ----------- | ---------------------------- | -------------------------------------------------------------------- |
| SwiftLint   | `SwiftLintSensor`            | `sonar.swift.swiftlint.reportPaths` OR `sonar.swift.swiftlint.autorun=true` |
| OCLint      | `OCLintSensor`               | `sonar.objc.oclint.reportPaths`                                      |
| Periphery   | `PeripherySensor`            | `sonar.swift.periphery.reportPath`                                   |
| Tailor      | `SwiftLintSensor`-style stub | reserved for 0.3                                                     |
| Generic     | `GenericIssueSensor` (NEW)   | `sonar.swift.externalIssues.reportPaths`                              |

## Coverage

| Format       | Sensor                       | Trigger                                                              |
| ------------ | ---------------------------- | -------------------------------------------------------------------- |
| xccov JSON   | `XccovCoverageSensor`        | `sonar.swift.coverage.xccov.reportPaths`                              |
| xcresult     | `XccovCoverageSensor`        | `sonar.swift.coverage.xcresultPaths` (invokes `xcrun xccov` itself)  |
| Slather XML  | `SlatherCoverageSensor`      | `sonar.swift.coverage.slather.reportPaths`                            |
| Cobertura    | `CoberturaCoverageSensor`    | `sonar.swift.coverage.cobertura.reportPaths`                          |

## Export

| Format       | Sensor                       | Trigger                                                              |
| ------------ | ---------------------------- | -------------------------------------------------------------------- |
| SARIF 2.1.0  | `SarifExportSensor` (NEW)    | `sonar.swift.export.sarifPath=path/to/out.sarif`                     |

## Quality

| Feature          | Where                                                                          |
| ---------------- | ------------------------------------------------------------------------------ |
| Quality profiles | `Sonar way`, `Strict`, `SwiftLint-compat` — `profiles/*.json`                  |
| Quality gates    | Reference set in `SwiftQualityGate.java` — applied via SonarQube API           |
| Suppressions     | `// sonar-swift:disable …` / `// swiftlint:disable …` — `SuppressionRegistry`  |

## Server-side features

SonarQube features that live on the server (PR decoration, branch analysis,
multi-project portfolios, governance reports) are not re-implemented here —
they Just Work once the right metrics are surfaced by the scanner, which sonar-swift
does. See SonarQube's own docs for those flows.

## What's intentionally NOT covered

| Feature                  | Why                                                                |
| ------------------------ | ------------------------------------------------------------------ |
| Edition-locked features  | Enterprise edition features (e.g. Security Vulnerabilities Hotspot Review queues) ship with SonarQube itself — out of scope for plugins. |
| Mobile platform-specific reports | Apple-only test bundles are supported; cross-mobile abstractions out of scope. |
