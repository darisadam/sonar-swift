# Roadmap

Milestone-by-milestone view of what ships when. The plan that explains the
*why* is in [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md).

## Milestones

### 0.1.0 — "Hello, Swift" (end of week 1)
- Maven build produces a loadable plugin JAR
- Plugin registers `swift` and `objc` languages
- `sonar-scanner` runs without errors on the sample project
- Zero rules, zero metrics. Just plumbing.

### 0.2.0 — "I can count" (end of week 3)
- LOC, NCLOC, comment lines computed
- Functions, classes, statements counted
- Files visible in SonarQube UI with metrics, no issues yet
- Native Swift parser bridge online

### 0.3.0 — "First findings" (end of week 6)
- 20 Swift rules + 10 ObjC rules active in the default profile
- Cyclomatic + cognitive complexity computed
- Quick-fix machinery in place (no actual quick-fixes yet)

### 0.4.0 — "Coverage" (end of week 9)
- xccov + Slather + Cobertura + generic-XML coverage parsers
- Line + branch coverage on UI
- New-code coverage works for PR analysis
- 40 Swift rules + 20 ObjC rules

### 0.5.0 — "Duplications & dead code" (end of week 11)
- CPD active for both languages
- Periphery integration for Swift dead code
- 60 Swift rules + 30 ObjC rules

### 0.6.0 — "Security" (end of week 14)
- MASVS-aligned vulnerability rules (~15)
- Security-hotspot rules (~10)
- Hardcoded secret detection
- 80 Swift rules + 40 ObjC rules

### 0.7.0 — "DX" (end of week 16)
- Fastlane action
- GitHub Actions composite action
- GitLab CI template
- Docker dev image polished

### 1.0.0 — "GA" (end of week 17)
- ≥ 100 Swift rules + ≥ 50 ObjC rules
- Docs site published
- Integration tests green on four reference projects
- At least one external team using it on a real codebase
- Sonar Marketplace submission opened

## Feature matrix at 1.0

| Feature                              | Swift | Objective-C | Notes                                            |
| ------------------------------------ | :---: | :---------: | ------------------------------------------------ |
| Issue detection                      |   ✓   |      ✓      | Native AST + external tools                      |
| Quick-fixes                          |   ✓   |      –      | Swift only at 1.0; ObjC in 1.1                   |
| LOC / NCLOC                          |   ✓   |      ✓      |                                                  |
| Cyclomatic complexity                |   ✓   |      ✓      |                                                  |
| Cognitive complexity                 |   ✓   |      ✓      |                                                  |
| Duplications (CPD)                   |   ✓   |      ✓      |                                                  |
| Dead code                            |   ✓   |      △      | Periphery for Swift; native check coverage for ObjC at 1.0 is partial |
| Coverage (xccov)                     |   ✓   |      ✓      |                                                  |
| Coverage (Slather)                   |   ✓   |      ✓      |                                                  |
| Coverage (Cobertura)                 |   ✓   |      ✓      |                                                  |
| Coverage (Sonar generic XML)         |   ✓   |      ✓      |                                                  |
| Security: secrets                    |   ✓   |      ✓      |                                                  |
| Security: MASVS rules                |   ✓   |      ✓      |                                                  |
| Security hotspots                    |   ✓   |      ✓      |                                                  |
| SwiftLint integration                |   ✓   |     n/a     |                                                  |
| OCLint integration                   |  n/a  |      ✓      |                                                  |
| Periphery integration                |   ✓   |     n/a     |                                                  |
| Tailor integration                   |   ✓   |     n/a     |                                                  |
| Multi-target Xcode projects          |   ✓   |      ✓      |                                                  |
| SwiftPM projects                     |   ✓   |     n/a     |                                                  |
| Mixed Swift + ObjC modules           |   ✓   |      ✓      |                                                  |

## Beyond 1.0 (in priority order)

1. ObjC quick-fixes (parity with Swift)
2. SourceKit-LSP integration (resolve symbols → cross-file taint tracking)
3. SwiftUI-specific rule pack (state mutation, view-builder misuse, body-too-big)
4. SwiftConcurrency rule pack (actor isolation, `Sendable` violations, data races)
5. visionOS / RealityKit rule pack
6. Custom-rule SDK ("write your own rule in Swift, plug it in")
7. Incremental scanning (scan only files changed since `sonar.scm.revision`)
8. SARIF export for non-Sonar consumers
