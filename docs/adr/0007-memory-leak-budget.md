# ADR-0007 — Memory-leak budget for the scanner

Date: 2026-06-04
Status: Accepted

## Context

The plugin is long-lived in CI: a single scan can analyse thousands of files,
and a SonarQube server may keep the plugin classloader alive across analyses.
A retention bug (static cache, unbounded list, sidecar that doesn't release
resources) silently degrades scan performance and eventually causes OOMs on
shared CI runners.

We need a continuous check that catches retention regressions in the scanner
itself — before they reach the consumer projects.

## Options considered

1. **Allocation profiling on every PR** — `-XX:+HeapDumpAfterFullGC`,
   `-XX:NativeMemoryTracking`, periodic `jcmd GC.heap_info`. Heavyweight;
   results vary by JDK / GC / OS conditions; noisy on shared runners.

2. **Strict absolute thresholds** — "heap must be under 200 MB." Rigid;
   breaks the moment we add legitimate features.

3. **Budget + slack** (chosen) — record a baseline footprint on a known
   sample project, compare each run against the baseline, fail if growth
   exceeds a configurable slack (default 5%). Accept-new-baseline is an
   explicit, human-reviewed action.

## Decision

Ship a `LeakDetectionSensor` that, when enabled, runs at end-of-scan and
serialises a memory profile to `.sonar/leaks/latest.json`. Compare against
`.sonar/leaks/budget.json` (committed to the repo) with a 5% default slack.
The Swift parser sidecar exposes its own snapshot via `{"op":"metrics"}`,
captured into the same report so retention on either side of the bridge is
visible.

A CI workflow runs the scan + compare on every PR touching plugin or parser
sources. A pre-commit hook runs the same check locally when relevant files
are staged.

## Consequences

- Budget regressions surface as PR failures with clear "this PR added 12%
  heap usage; accept-budget if intentional" messaging.
- The budget JSON IS part of the repo. Any legitimate growth is reviewed
  in the same PR that introduces it.
- Default slack of 5% is conservative — fine-grained enough to catch a
  forgotten static cache, loose enough to tolerate GC noise.
- We don't profile allocation paths (no flame graphs). For deep
  investigation, devs still use `jmap` / Instruments / Async-Profiler.

## Related

- [`docs/leak-detection.md`](../leak-detection.md) — user-facing docs
- [`scripts/leak/leak-check.sh`](../../scripts/leak/leak-check.sh) — the runner
- [`LeakDetectionSensor.java`](../../sonar-swift-plugin/src/main/java/io/sonarswift/plugin/leak/LeakDetectionSensor.java)
- [`ParserMemoryProbe.java`](../../sonar-swift-plugin/src/main/java/io/sonarswift/plugin/leak/ParserMemoryProbe.java)
- [`Metrics.swift`](../../sonar-swift-parser/Sources/SwiftSonarParser/Metrics.swift)
