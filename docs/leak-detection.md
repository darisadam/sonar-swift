# Memory-leak budget

How sonar-swift catches memory regressions in *itself* — the Java plugin and
the Swift parser sidecar — before they reach production.

## Why a budget, not a graph

Absolute leak detection on a JVM (or any GC'd runtime) is unreliable. Heap
samples vary based on GC settings, JIT warmup, OS conditions. Allocation
profiling is heavyweight and noisy.

Instead we lock in a **baseline footprint** — known-good heap usage on a known
input (the bundled sample iOS project) — and fail when that footprint grows
beyond a configurable slack. It's the same approach React Native, Chromium and
many others use for "is the build heavier this PR than last week."

## Lifecycle

1. **Capture**: the `LeakDetectionSensor` runs at end-of-scan when
   `sonar.swift.leak.enabled=true`. It samples:
   - JVM heap (init / used / committed / max) after a forced GC
   - Non-heap (metaspace, code cache)
   - Thread / GC / class loading counters
   - The Swift parser sidecar's `mach_task_basic_info` snapshot, fetched via
     `{"op":"metrics"}` over NDJSON
2. **Write**: report is serialised to `.sonar/leaks/latest.json` and copied
   to a timestamped sibling for history.
3. **Compare**: if `.sonar/leaks/budget.json` exists, the sensor compares
   `heap.used` and logs a warning if the current measurement exceeds
   `budget × (1 + slack)` (default slack: 5%).
4. **Accept**: after a legitimate increase (new sensors, new rules, larger
   sample input), `make leak-accept` promotes the latest report to the new
   budget.

## CLI

```bash
make leak-check               # capture + compare against budget; non-zero if over
make leak-accept              # promote latest to new budget
./scripts/leak/leak-check.sh --mode=capture --slack=0.05
```

## CI gate

`.github/workflows/leak.yml` runs on every PR that touches plugin or parser
sources. It spins up SonarQube via docker, runs the scanner with leak detection
on, and compares against the committed budget. Failures attach the leak JSON
plus a heap dump to the run.

## Pre-commit gate

A lighter gate runs in `.pre-commit-config.yaml` as the `sonar-swift-leak-budget`
hook. It only fires when sources under `sonar-swift-plugin/` or
`sonar-swift-parser/` are staged, so non-plugin commits are unaffected.

## Report shape

```json
{
  "timestamp": "2026-06-04T09:42:30.123Z",
  "plugin": "sonar-swift",
  "heap":     {"init": 67108864, "used": 142606336, "committed": 268435456, "max": 536870912},
  "nonHeap":  {"init":  2555904, "used":  82145792, "committed":  86048768, "max": -1},
  "threads":  {"count": 18, "peak": 21, "daemon": 14},
  "classes":  {"loaded": 12421, "totalLoaded": 13002, "unloaded": 581},
  "gc": {
    "G1 Young Generation": {"count": 14, "time": 87},
    "G1 Old Generation":   {"count": 1,  "time": 23},
    "totalCount": 15,
    "totalTimeMs": 110
  },
  "parser": {
    "timestampMillis": 1717494150123,
    "residentBytes": 145457152,
    "virtualBytes": 4296859648,
    "liveThreads": 4,
    "allocationsTotal": 198103040,
    "allocationsPerFile": 23145,
    "filesParsed": 42
  },
  "scan": {"project": "ios-sample-app", "elapsedMs": 218}
}
```

## When the gate fails

1. **Look at the diff** — does the new code introduce a long-lived collection,
   a static cache, a parser-side buffer that doesn't drain?
2. **Re-run locally** — `make leak-check` to confirm the regression is real and
   not noise.
3. **If legitimate** — `make leak-rebudget` to accept the new baseline, commit
   the updated `.sonar/leaks/budget.json`, and reference the rationale in your
   PR.
4. **If unexpected** — open a heap dump (`.sonar/leaks/oom.hprof` is captured
   on OOM, but you can also use `jmap` on a running scan) and investigate.
