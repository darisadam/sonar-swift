---
name: bench-scanner
description: Benchmark the plugin's scan time and memory on a target Swift project. Use when the user asks "how fast is the scan", "is sonar-swift slow", "let's profile the scanner", or before a release to catch regressions.
---

# bench-scanner

Runs the scanner against a target project with timing + memory instrumentation
and produces a comparison table against previous runs.

## Setup

1. Pick the bench target — one of:
   - `examples/ios-sample-app` (small, ~50 LOC, baseline)
   - The user's own project (large, real-world)
2. Ensure the plugin is built fresh: `mvn -q -ntp -DskipTests package`
3. Ensure local SonarQube is up: invoke the `run-local-sonar` skill

## Steps

1. **Clear caches**:
   ```bash
   rm -rf .scannerwork
   ```

2. **Wall-clock + max-RSS via `/usr/bin/time -l`** on macOS or `-v` on Linux:
   ```bash
   /usr/bin/time -l sonar-scanner \
       -Dsonar.host.url=http://localhost:9000 \
       -Dsonar.token=admin
   ```
   Capture the output. Look for:
   - "real" — wall-clock seconds
   - "maximum resident set size" — peak RSS in bytes (divide by 1024^2 for MB)
   - Sensor-by-sensor timings logged by SonarQube itself ("Sensor … took N ms")

3. **JVM heap profile** (optional):
   ```bash
   SONAR_SCANNER_OPTS="-Xmx2g -XX:+UseG1GC -Xlog:gc*:file=gc.log" \
       sonar-scanner ...
   ```
   Then `tail gc.log` to spot full GCs.

4. **Per-file rate**:
   ```
   files / wall-clock = files-per-second
   ```
   Target: ≥ 25 files/sec for the typical mid-size project.

5. **Compare to baseline**:
   - Store this run's numbers in `bench/` (gitignored except for a
     `baseline.json` that we commit).
   - Diff against `baseline.json`. Flag regressions ≥ 15%.

## Output format

```
sonar-swift bench — examples/ios-sample-app — 2026-06-03 16:42
  files scanned         : 2
  wall-clock            : 6.7s
  peak RSS              : 412 MB
  files/sec             : 0.30  (small project, dominated by SQ startup)
  sensor breakdown:
    SwiftSensor         : 410 ms
    SwiftLintSensor     : 0   ms (no report)
    XccovCoverageSensor : 220 ms
    CPDSensor           : 90  ms
  vs baseline 2026-05-30:
    wall-clock          : +0.3s (within noise)
    peak RSS            : +12 MB (within noise)
```

## What to flag

- Wall-clock regression ≥ 15% on the same target
- RSS regression ≥ 30%
- Any sensor whose time grows non-linearly with file count (find the cause)
- New full-GC events during the scan (heap exhaustion likely)

## When the user wants to profile a hot path

Use `jfr` (Java Flight Recorder) — adds negligible overhead, gives a sampled
profile:

```bash
SONAR_SCANNER_OPTS="-XX:StartFlightRecording=duration=60s,filename=profile.jfr" \
    sonar-scanner ...
```

Open `profile.jfr` in JDK Mission Control or
[`profiler.firefox.com`](https://profiler.firefox.com/) via JFR-to-pprof.
