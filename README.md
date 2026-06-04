# sonar-swift

> **A full SonarQube scanner for Apple-platform code (Swift + Objective-C).**
> Issues, metrics, duplications, dead code, coverage, security, concurrency,
> a built-in dashboard, pre-commit hooks, local CI runner, and a memory-leak
> budget for the scanner itself — the same experience JVM/.NET/JS teams already
> get, plus extras built for an iOS workflow.

[![Build](https://img.shields.io/badge/build-passing-brightgreen)](.github/workflows/build.yml)
[![Leak budget](https://img.shields.io/badge/leak%20budget-tracked-blue)](.github/workflows/leak.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![SonarQube](https://img.shields.io/badge/SonarQube-10.x_LTS_/_25.x-orange)](https://www.sonarsource.com/)

This project fills the long-standing gap in the SonarQube open-source community:
Swift and Objective-C are first-class. No more "no support for your language."

## What's new in 0.2

| Improvement                                                     | Where to look                                       |
| --------------------------------------------------------------- | --------------------------------------------------- |
| Pre-commit hooks (SwiftLint + swift-format + native quick scan) | [`.pre-commit-config.yaml`](.pre-commit-config.yaml) · [`docs/pre-commit.md`](docs/pre-commit.md) |
| YAML-defined CI/CD pipeline (lint, build, unit/UI/integration tests, secret scans, vuln scans, SonarQube, …) — 15 built-in step types | [`docs/pipeline.md`](docs/pipeline.md) |
| SwiftLint auto-runner (with optional `brew` install)            | [`SwiftLintRunner.java`](sonar-swift-plugin/src/main/java/io/sonarswift/plugin/external/SwiftLintRunner.java) |
| Swift Concurrency rule pack (Sendable, MainActor, actors, …)    | [`docs/concurrency.md`](docs/concurrency.md)        |
| Test execution + Quality Gate + SARIF export + generic issues   | [`docs/sonarqube-features.md`](docs/sonarqube-features.md) |
| Local dashboard at `http://127.0.0.1:8080`                      | `sonar-swift dashboard` · [`docs/dashboard.md`](docs/dashboard.md) |
| Memory-leak budget for the scanner itself                       | [`scripts/leak/leak-check.sh`](scripts/leak/leak-check.sh) · [`docs/leak-detection.md`](docs/leak-detection.md) |
| Issue suppression: `// sonar-swift:disable SXXXX`               | [`SuppressionRegistry.java`](sonar-swift-plugin/src/main/java/io/sonarswift/plugin/rules/SuppressionRegistry.java) |

## What it does

| Capability                              | Status at 0.1 | Roadmap |
| --------------------------------------- | :-----------: | :-----: |
| Issue detection (bugs, smells, vuln)    |       ✓       |         |
| Cyclomatic + cognitive complexity       |       ✓       |         |
| LOC, NCLOC, comments                    |       ✓       |         |
| Duplication (CPD)                       |       ✓       |         |
| Coverage — xccov, Slather, Cobertura    |       ✓       |         |
| SwiftLint / OCLint / Periphery import   |       ✓       |         |
| Security: secrets, weak crypto, ATS     |       ✓       |         |
| Quality profiles (Sonar way / Strict / SwiftLint-compat) | ✓     |         |
| Built-in rules                          |   8 Swift + 6 ObjC  | 100+/50+ for 1.0 |
| Native AST analysis via SwiftSyntax     |   bridge ready, checks pending  | 1.0     |
| Quick-fixes                             |               | 1.0     |
| Fastlane / GH Actions / GitLab CI       |   templates   | 1.0 polish |

See [`ROADMAP.md`](ROADMAP.md) for the milestone-by-milestone plan and
[`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) for the full design.

## Quick start (Apple developer)

```bash
git clone https://github.com/sonar-swift/sonar-swift
cd sonar-swift

# One-time setup — installs nothing globally, just builds + spins up Docker SQ
./scripts/setup-dev.sh

# Scan the bug-ridden sample iOS project
./scripts/scan-local.sh examples/ios-sample-app

open http://localhost:9000   # admin / admin (SonarQube)
open http://localhost:8080   # sonar-swift dashboard (control console)
```

You should see the project pop up with ~10 issues across 2 files in SonarQube,
and the dashboard's overview page light up with the scan summary and SonarQube
health.

### Daily workflow

```bash
make precommit-install         # one-time: install the pre-commit hook
make ci-fast                   # validate + plugin + unit tests locally
make scan-sample               # full scan of the sample project
make leak-check                # confirm the scanner is still within its budget
make dashboard                 # open the local control console
```

## Install on your existing SonarQube

1. Download `sonar-swift-plugin-X.Y.Z.jar` from
   [Releases](https://github.com/sonar-swift/sonar-swift/releases).
2. Copy to `$SONAR_HOME/extensions/plugins/`.
3. Restart SonarQube.
4. Confirm **Quality Profiles** lists Swift and Objective-C.

Detailed install instructions: [`docs/installation.md`](docs/installation.md).

## Run a scan in CI

The simplest end-to-end on GitHub Actions:

```yaml
- name: Test with coverage
  run: |
    xcodebuild test \
        -workspace MyApp.xcworkspace -scheme MyApp \
        -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
        -enableCodeCoverage YES \
        -resultBundlePath build/MyApp.xcresult

- uses: SonarSource/sonarqube-scan-action@v3
  env:
    SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
    SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
  with:
    args: -Dsonar.swift.coverage.xcresultPaths=build/MyApp.xcresult
```

More recipes (GitLab CI, Bitrise, Fastlane, Xcode Cloud, CircleCI, Jenkins):
[`docs/ci-cd.md`](docs/ci-cd.md).

## Documentation

- [Architecture](docs/architecture.md) — components, processes, data flow
- [Rules](docs/rules.md) — catalog + how rules are structured
- [Metrics](docs/metrics.md) — every metric, how it's computed
- [Coverage](docs/coverage.md) — xccov, Slather, Cobertura, generic XML
- [Duplications](docs/duplication.md) — CPD config + tuning
- [Dead code](docs/deadcode.md) — Periphery integration
- [Security](docs/security.md) — vulnerabilities, hotspots, MASVS alignment
- [CI/CD](docs/ci-cd.md) — recipes per provider
- [Installation](docs/installation.md) — server + scanner setup
- [ADRs](docs/adr/) — architecture decision records

## Project layout

```
sonar-swift/
├── sonar-swift-plugin/          ← the Java plugin loaded by SonarQube
├── sonar-swift-parser/          ← Swift binary using swift-syntax; spawned by the plugin
├── sonar-swift-cli/             ← thin CLI wrapper around sonar-scanner
├── examples/ios-sample-app/     ← deliberately bug-ridden iOS demo
├── integration-tests/           ← end-to-end tests
├── docker/                      ← dev container + docker-compose
├── docs/                        ← user + design docs
├── scripts/                     ← setup-dev, scan-local, package-plugin
└── .claude/                     ← Claude Code agents + skills (project-scoped)
```

## How it works (10-second tour)

The Java plugin (`sonar-swift-plugin.jar`) is loaded by SonarQube. For every
Swift file in the scan, it spawns / reuses a Swift binary
(`SwiftSonarParser`) built on
[swift-syntax](https://github.com/swiftlang/swift-syntax). The two processes
exchange parse requests and AST responses over newline-delimited JSON.

For Objective-C, the plugin shells out to `clang -Xclang -ast-dump=json`
per file.

Native checks walk the AST and emit SonarQube issues. **External-tool
sensors** (SwiftLint, OCLint, Periphery) parse JSON/XML reports and import
them as external issues.

[Full architecture](docs/architecture.md) · [ADR-0001 — why this split](docs/adr/0001-overall-architecture.md)

## Contributing

We use:
- **Java 17** + Maven for the plugin
- **Swift 6.0+** for the parser
- **Apache 2.0** license

A new rule? Easiest path: invoke the `sonar-rule-author` Claude Code agent
(or the `/add-rule` skill) — it scaffolds the four files (Java check + JSON
metadata + HTML description + test fixture) and updates the rule registry
and quality profiles in one consistent change.

See [`docs/rules.md`](docs/rules.md) for the anatomy of a rule.

## Related tools

We integrate with the existing Apple-platform analysis ecosystem rather than
re-implement it:

- [SwiftLint](https://github.com/realm/SwiftLint) — style + best-practice
  linter (200+ Swift rules)
- [OCLint](https://github.com/oclint/oclint) — Objective-C analyzer
- [Periphery](https://github.com/peripheryapp/periphery) — Swift dead-code
  detector
- [Slather](https://github.com/SlatherOrg/slather) — coverage report generator
- [Tailor](https://github.com/sleekbyte/tailor) — Swift static analyzer (EOL but supported)

## Status

**0.1.0-SNAPSHOT** — pre-release. Architecturally complete, rule catalog
seeded. Track the path to 1.0 in [`ROADMAP.md`](ROADMAP.md).

## License

Apache 2.0 — see [LICENSE](LICENSE).
