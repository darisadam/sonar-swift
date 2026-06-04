# ADR-0005: External tools as first-class sensors

## Status

Accepted — 2026-06-03.

## Context

There's already a mature ecosystem of analysis tools for Apple platforms:

- **SwiftLint** — the de-facto Swift style checker, 200+ rules
- **OCLint** — the long-standing Objective-C analyzer
- **Periphery** — best-in-class Swift dead-code finder
- **Tailor** — Swift style checker (less popular than SwiftLint)

Re-implementing every check these tools do, in our plugin, is wasteful and
slow. Teams already trust these tools and have profiles tuned for them.

## Decision

Integrate each as a **sensor** that reads the tool's native output format and
emits SonarQube issues against rule keys we control:

- `SwiftLintSensor` — reads SwiftLint's JSON (`swiftlint lint --reporter json`).
  Maps `force_unwrapping` → our `S1001`, etc.
- `OCLintSensor` — reads OCLint's PMD XML.
- `PerigerySensor` — reads Periphery's JSON.
- `TailorSensor` — reads Tailor's JSON (optional, less common).

The plugin **does not run the tools** by default — it reads reports the CI
already generates. We add `autorun=true` flags for the lazy path, but the
recommended setup is "run SwiftLint as a CI step that caches its results,
then feed to sonar-swift."

Native rules in our plugin (those that need the AST) are *complementary*, not
duplicative — for example, we don't ship our own `force_unwrap` check
because SwiftLint already does it well. We focus native rules on:

- Cognitive complexity (no other tool computes this)
- Cross-file analysis that SwiftLint doesn't (yet) do
- Security rules SwiftLint doesn't cover
- ObjC rules OCLint doesn't cover

## Consequences

- **+** Day-one breadth: a SwiftLint-using team gets immediate SonarQube
  coverage matching their existing tooling.
- **+** Mature, tested rule logic for free.
- **−** Coupling to external tools' output formats. We pin minimum versions
  and have integration tests.
- **−** Possible rule overlap with our native rules. Mitigated by suppression
  rules in our profiles — if SwiftLint and we both report the same finding,
  we mark one as duplicate via Sonar's deduplication.
- **−** External tools' false-positive rates become ours by default. We let
  teams disable specific rules per sensor via `sonar.swift.swiftlint.disabledRules`.

## Tool-version matrix at 1.0

| Tool       | Minimum version | Tested up to | Output format         |
| ---------- | :-------------: | :----------: | --------------------- |
| SwiftLint  | 0.55            | latest       | `--reporter json`     |
| OCLint     | 22.02           | 24.x         | `-report-type pmd`    |
| Periphery  | 2.20            | latest       | `--format json`       |
| Tailor     | 0.12            | 0.12 (EOL)   | `--format json`       |

## Alternatives considered

- **Native-only**: rejected as scope explosion.
- **Embed the tools as libraries**: SwiftLint is a Swift package and could be
  linked into `SwiftSonarParser`. Tempting; we may do this in 1.1 to drop the
  separate CI step. For 1.0, subprocess + report-file boundary is cleaner.
