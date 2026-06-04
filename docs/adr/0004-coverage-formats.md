# ADR-0004: Support four coverage formats

## Status

Accepted — 2026-06-03.

## Context

There is no single coverage format universal to Apple-platform teams:

- Some use Xcode's native `xccov` from the `.xcresult` bundle (lowest friction)
- Some use Slather, which exports its own XML
- Some use Cobertura XML, often via Slather's `--cobertura-xml`
- Some hand-roll the SonarQube generic XML

If we support only one, half the teams have to convert.

## Decision

Support all four. One sensor per format:

- `XccovCoverageSensor` (reads from `.xcresult` *or* pre-extracted `xccov` JSON)
- `SlatherCoverageSensor`
- `CoberturaCoverageSensor`
- `GenericCoverageSensor` (delegates to SonarQube's built-in generic-coverage
  parser via `sonar.coverageReportPaths`)

When multiple are configured for the same file, the *last sensor to run* wins
on that file's line coverage. We pick run order to prefer the most-precise
format: xccov → Slather → Cobertura → generic.

## Consequences

- **+** Zero migration tax for new users — bring whatever you already have.
- **−** Four parsers to maintain. Mitigated by giving each a small, focused
  parser; only `XccovCoverageSensor` is non-trivial.
- **−** Risk of conflicting reports producing surprising numbers. We log at
  INFO when a line is covered by one report and uncovered by another, and
  document the precedence rules.

## Notes on `xccov`

The `xccov` JSON format is undocumented but stable. We snapshot example
outputs from each new Xcode major in `integration-tests/fixtures/xccov/` and
have parser tests that load them. Xcode-major upgrades that change the
schema will fail tests loudly.

## Alternatives considered

- **Pick one (xccov-only)**: would alienate Slather users with years of
  config invested.
- **Convert everything to generic XML internally before processing**: extra
  abstraction layer with no benefit; we'd be re-implementing each parser
  anyway.
