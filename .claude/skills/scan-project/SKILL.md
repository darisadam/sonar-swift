---
name: scan-project
description: Run an end-to-end scan against a Swift/iOS project — build, test with coverage, optional SwiftLint+Periphery report generation, then sonar-scanner invocation. Use when the user says "scan my app", "run sonar on this project", "send results to SonarQube".
---

# scan-project

End-to-end: from a checked-out Xcode workspace to issues in SonarQube.

## Inputs to gather

Ask up front (in one batch via `AskUserQuestion` if not obvious):

1. Project path (default: current working directory)
2. Workspace or project file (auto-detect `.xcworkspace` over `.xcodeproj`)
3. Scheme name (auto-detect from `xcodebuild -list`)
4. Whether to run SwiftLint and Periphery (yes/no — defaults yes if on PATH)
5. SonarQube URL + token (default: local at `http://localhost:9000` if
   `docker ps | grep sonar-swift-sq`)

## Steps

1. **Pre-flight**:
   - `xcodebuild` available?
   - `sonar-scanner` available? (`brew install sonar-scanner` if not)
   - SonarQube reachable? (`curl -sf $URL/api/system/status` returns `UP`)

2. **Build + test with coverage**:
   ```bash
   xcodebuild test \
       -workspace "$WS" \
       -scheme "$SCHEME" \
       -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
       -enableCodeCoverage YES \
       -resultBundlePath build/Coverage.xcresult
   ```

3. **Run SwiftLint** (optional):
   ```bash
   swiftlint lint --reporter json > build/swiftlint.json || true
   ```

4. **Run Periphery** (optional, slow):
   ```bash
   periphery scan \
       --workspace "$WS" \
       --schemes "$SCHEME" \
       --format json \
       > build/periphery.json || true
   ```

5. **Run sonar-scanner**:
   ```bash
   sonar-scanner \
       -Dsonar.host.url="$URL" \
       -Dsonar.token="$TOKEN" \
       -Dsonar.swift.coverage.xcresultPaths=build/Coverage.xcresult \
       -Dsonar.swift.swiftlint.reportPaths=build/swiftlint.json \
       -Dsonar.swift.periphery.reportPath=build/periphery.json
   ```

6. **Report**:
   - Link to the project page: `$URL/dashboard?id=$projectKey`
   - High-level numbers from the scan output (issues, coverage)
   - If the scan failed, surface the actual error (don't paper over).

## When the scan fails

- "Project key already exists under a different organization" — set
  `sonar.projectKey` explicitly in `sonar-project.properties`.
- "No tests were executed" — the user's scheme isn't set up for testing, or
  the simulator destination is wrong for the current Xcode.
- "Coverage report not found" — `-resultBundlePath` produced a different
  filename, e.g. with a timestamp. Use the exact path from the build log.
- "Could not find a quality profile" — happens if the plugin didn't load on
  the SonarQube server. Re-deploy plugin JAR and restart SQ.

## What NOT to do

- Don't run the scan on a dirty working tree without warning. New-code
  metrics use SCM, so unstaged changes can pollute the new-code window.
- Don't pass `--exhaustive-test-bundle-paths` blind — it's slow and rarely
  the user's intent.
- Don't push results to a production SonarQube without confirming. Use the
  local Docker SonarQube during development.
