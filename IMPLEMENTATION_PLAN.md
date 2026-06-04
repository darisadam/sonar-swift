# sonar-swift — Implementation Plan

A full SonarQube scanner for Apple platform codebases (iOS, macOS, watchOS, tvOS,
visionOS) written in **Swift** and **Objective-C**, packaged as a standard
SonarQube plugin (`.jar`) plus a small companion Swift binary for AST work.

This document is the master plan. Detailed designs live in [`docs/`](docs/) and
[`docs/adr/`](docs/adr/). The current punch-list of milestones lives in
[`ROADMAP.md`](ROADMAP.md).

---

## 1. Vision

Give iOS / Apple-platform teams the same SonarQube experience that JVM, .NET,
JS/TS, and Python teams already enjoy:

- One pane of glass for **bugs, code smells, vulnerabilities, security
  hotspots, duplications, dead code, coverage, complexity**, and **technical
  debt**.
- First-class support for the **Quality Gate** pull-request workflow.
- Sensible defaults that match the Apple **Swift API Design Guidelines** and
  **OWASP MASVS** for iOS, but with profiles configurable per project.
- Designed for **CI-first** use (GitHub Actions, GitLab CI, Bitrise, Xcode
  Cloud, CircleCI, Jenkins) and **local-first** developer iteration (one
  `make scan` from a checkout).

## 2. Scope — what SonarQube does, mapped to this project

SonarQube core surface area, with our implementation strategy for each:

| Capability                  | SQ feature                                        | Our approach                                                                                  |
| --------------------------- | ------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| **Issue detection**         | Rules engine, issue API, quick-fixes              | Native checks running on a SwiftSyntax / libclang AST + bridged external tools                |
| **Code smells**             | `CODE_SMELL` issues                               | ~120 rules (Swift) + ~60 rules (ObjC) at GA                                                   |
| **Bugs**                    | `BUG` issues                                      | ~40 rules (force-unwrap, dangling main thread, retain cycles, etc.)                           |
| **Vulnerabilities**         | `VULNERABILITY` issues                            | OWASP MASVS-aligned rules (ATS bypass, weak crypto, keychain misuse, hardcoded secrets)       |
| **Security hotspots**       | `SECURITY_HOTSPOT` review pipeline                | Hotspot rules for `eval`-like APIs, file paths, URL handling, deeplinks                       |
| **Cyclomatic complexity**   | `complexity` metric                               | AST visitor over Swift/ObjC functions                                                          |
| **Cognitive complexity**    | `cognitive_complexity` metric                     | Sonar's official spec implemented over our AST                                                |
| **LOC / NCLOC / comments**  | `ncloc`, `comment_lines`, `comment_lines_density` | Token classifier over both languages                                                          |
| **Functions / classes**     | `functions`, `classes`, `statements`              | AST visitor                                                                                   |
| **Duplications**            | CPD subsystem                                     | Token sequences emitted from our tokenizer → SonarQube's CPD                                  |
| **Coverage**                | `coverage`, `line_coverage`, `branch_coverage`    | Parsers for `xccov` (Xcode native), Slather, Cobertura, generic SQ generic-coverage XML       |
| **Dead code**               | (Indirect via custom rules)                       | Periphery integration for Swift + custom unused-selector / unused-class checks for ObjC       |
| **Technical debt / SQALE**  | `sqale_index`                                     | Each rule has a remediation cost (`5min`, `30min`, …). SonarQube aggregates into debt ratio.  |
| **Quality profiles**        | `BuiltInQualityProfilesDefinition`                | Three: *Sonar way* (default), *Strict*, *SwiftLint-compat*                                    |
| **Quality gates**           | Server-side, plugin contributes metrics           | We surface the metrics the gates need (new-code coverage, new-code duplications, etc.)        |
| **Pull-request decoration** | SonarQube Developer/Enterprise feature            | Out of scope to re-implement — we just feed the right metrics                                 |
| **Multi-module projects**   | `sonar.modules`                                   | Each Xcode target / SwiftPM target can be a module                                            |
| **Branch analysis**         | Standard SQ branch support                        | Works once `sonar.branch.name` is set in CI                                                   |

## 3. Architecture in one paragraph

A **Java SonarQube plugin** (`sonar-swift-plugin.jar`) is loaded by SonarScanner.
For every Swift file in the project it spawns / re-uses a single
**`SwiftSonarParser`** Swift binary (built with SwiftSyntax) and exchanges
newline-delimited JSON: in goes a file path, out comes a compact AST + token
stream. For every Objective-C file it shells out to `clang -Xclang -ast-dump`
(or libclang via JavaCPP — see [ADR-002](docs/adr/0002-parser-strategy.md)).
Java-side **checks** walk the AST and emit SonarQube issues / measures /
duplication tokens. External-tool **sensors** (SwiftLint, OCLint, Periphery,
Tailor) parse third-party JSON/XML and contribute additional issues.
Coverage sensors parse `xccov`/Slather/Cobertura/generic-XML.

See [`docs/architecture.md`](docs/architecture.md) for the diagram + data flow.

## 4. Phased delivery

This is what the next four months look like. Weeks are *engineer-weeks*; one
engineer focused full-time. Two engineers in parallel can roughly halve.

### Phase 0 — Foundation (week 1)
- Maven multi-module skeleton (`pom.xml`, `sonar-swift-plugin`, `sonar-swift-cli`)
- Swift Package skeleton (`sonar-swift-parser`)
- GitHub Actions CI (build, unit-test, package)
- Docker dev image (SonarQube 25 LTS + scanner + our plugin pre-installed)
- README quickstart that runs end-to-end against the sample iOS app

### Phase 1 — Plugin shell + languages (weeks 2-3)
- `SwiftPlugin` extension entry point
- `Swift` and `ObjectiveC` `AbstractLanguage` definitions, file suffixes, keys
- Default `BuiltInQualityProfilesDefinition` with three profiles
- Rule key registry, rule descriptions wired via HTML files in resources
- Empty sensor that picks up `.swift` and `.m`/`.h` files and logs them

### Phase 2 — Native Swift parser bridge (weeks 3-5)
- `sonar-swift-parser` Swift Package using `swift-syntax` 600+
- NDJSON request/response protocol over stdin/stdout
- Java `SwiftSyntaxClient` that owns the subprocess lifecycle, retries crashes,
  multiplexes file requests in batches
- AST types in Java (`SwiftAst.FunctionDecl`, `SwiftAst.ClassDecl`, …)
- Token stream output for CPD + LOC

### Phase 3 — Metrics (week 6)
- `LinesCalculator` — `ncloc`, `comment_lines`, `lines`
- `ComplexityCalculator` — cyclomatic per function + per file
- `CognitiveComplexityCalculator` — Sonar's spec
- `StructuralMetrics` — functions, classes, statements
- File-level + project-level rollups via Sonar `SensorContext`

### Phase 4 — Rules engine + initial rule set (weeks 7-10)
- Check API surface (`SwiftCheck`, `ObjCCheck` base classes with `AstVisitor`)
- HTML rule description loader, JSON rule metadata
- Quick-fix DSL (text-edit emission)
- **Swift rules** (target ~60 at end of phase): see [docs/rules.md](docs/rules.md)
- **Objective-C rules** (target ~25 at end of phase)

### Phase 5 — Duplication (week 11)
- Plug our token stream into `NewCpdTokens`
- Filter strings/comments/literals from CPD per language conventions

### Phase 6 — Coverage (week 12)
- `XccovCoverageSensor` — runs `xcrun xccov view --report --json *.xcresult` itself if asked
- `SlatherCoverageSensor` — reads Slather XML
- `CoberturaCoverageSensor` — generic Cobertura
- `GenericCoverageSensor` — Sonar generic test-coverage XML

### Phase 7 — Dead code (week 13)
- `PerigerySensor` — invokes Periphery or parses pre-generated JSON
- ObjC unused-selector / unused-class checks (native, using AST cross-refs)

### Phase 8 — Security (weeks 14-15)
- Hardcoded-secret detection (entropy + regex catalog, MASVS-aligned)
- Insecure-URL / cleartext-traffic
- Weak crypto (MD5/SHA1/ECB/DES, `arc4random`-as-CSPRNG)
- iOS-specific: `NSAllowsArbitraryLoads`, `UIWebView`, keychain accessibility,
  pasteboard PII, deeplink validation
- Security-hotspot rules where context is needed

### Phase 9 — CI/CD + DX (week 16)
- Fastlane action (`fastlane sonar_swift`)
- GitHub Actions composite action + Marketplace listing
- GitLab CI template
- Xcode Cloud webhook (post-build → Sonar)
- CLI subcommand wrappers (`sonar-swift scan`, `sonar-swift report`)

### Phase 10 — Polish & release (week 17)
- Integration test matrix (Alamofire / SwiftUI demo / a UIKit demo / a mixed
  ObjC+Swift demo)
- Docs site (GitHub Pages from `docs/`)
- 1.0.0 release tag, GitHub release notes, Sonar marketplace PR

## 5. Cross-cutting concerns

- **Performance**: target ≤ 200ms/file for the typical Swift file on M-series.
  Subprocess pooling, batched parses, and incremental scanning (skip unchanged
  files when `sonar.scm.disabled=false`).
- **Memory**: stream AST nodes from parser → checks, don't materialize whole
  AST when not needed. ObjC AST dumps can be large.
- **Logging**: SLF4J-via-Sonar's logger; tag all logs with `[sonar-swift]`.
- **Telemetry**: opt-in only, anonymous counts (files scanned, rule activations).
- **Reproducibility**: pin SwiftSyntax version, pin clang version range, ship a
  versioned Docker image teams can use to get bit-exact CI results.

## 6. Risks & open questions

| Risk                                                            | Mitigation                                                                                                       |
| --------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| SwiftSyntax API churn between Swift releases                    | Pin a tested toolchain in CI; matrix-test against the last two Swift majors                                      |
| ObjC AST via libclang requires native libs (not pure Java)      | Two backends: `clang -Xclang -ast-dump` (subprocess, easy) and JavaCPP libclang (faster, harder packaging); start with subprocess |
| Scanner runs on Linux CI but Swift on Linux is fragile          | Provide Docker image with Swift-on-Linux toolchain pre-installed; document macOS-runner path as preferred       |
| Plugin marketplace approval timelines                           | Don't gate releases on marketplace — distribute via GitHub Releases first                                        |
| Quality-profile churn surprising teams                          | SemVer-tag profiles; new rules default to `disabled` on next-major-only                                          |

## 7. Success criteria for 1.0

- A green CI run on the four reference integration projects
- p95 scan time < 30s on a 50k-LOC project
- ≥ 100 Swift rules + ≥ 50 ObjC rules at GA
- Coverage from `xccov` working end-to-end with one CI invocation
- Documentation site live
- One real team using it on a real codebase
