# Coverage

How to get Xcode test coverage into SonarQube via this plugin.

## TL;DR — supported input formats

| Format               | Source tool                          | Sonar property                                  |
| -------------------- | ------------------------------------ | ----------------------------------------------- |
| Xcode `xccov` JSON   | `xcrun xccov view --report --json`   | `sonar.swift.coverage.xccov.reportPaths`        |
| Xcode `.xcresult`    | Xcode 11+, parsed by us              | `sonar.swift.coverage.xcresultPaths`            |
| Slather XML          | [Slather](https://github.com/SlatherOrg/slather) | `sonar.swift.coverage.slather.reportPaths` |
| Cobertura XML        | Slather `--cobertura-xml`, others    | `sonar.swift.coverage.cobertura.reportPaths`    |
| Sonar generic XML    | hand-rolled                          | `sonar.coverageReportPaths` (Sonar built-in)    |

Any combination can be set; the plugin merges. Per-line coverage values from
later sensors win on conflict.

## Recommended setup (Xcode → xccov)

This is the lowest-friction path: zero third-party dependencies.

```bash
# In your CI script, after tests run:
xcodebuild test \
    -workspace MyApp.xcworkspace \
    -scheme MyApp \
    -derivedDataPath build/ \
    -enableCodeCoverage YES \
    -resultBundlePath build/MyApp.xcresult

# Then the scanner picks it up:
sonar-scanner \
    -Dsonar.swift.coverage.xcresultPaths=build/MyApp.xcresult
```

The plugin invokes `xcrun xccov view --report --json $path` itself and parses
the result. No need to pre-generate JSON.

If your CI runs on a Linux runner (no `xcrun` available), run the conversion
on the macOS step that ran tests, and pass the JSON path:

```bash
# macOS step
xcrun xccov view --report --json build/MyApp.xcresult > coverage.json

# Linux scanner step
sonar-scanner \
    -Dsonar.swift.coverage.xccov.reportPaths=coverage.json
```

## Slather

For teams already invested in Slather:

```bash
slather coverage --output-directory build/slather \
    --scheme MyApp \
    MyApp.xcodeproj
sonar-scanner \
    -Dsonar.swift.coverage.slather.reportPaths=build/slather/coverage.xml
```

We accept Slather's native XML; you don't have to convert to Cobertura.

## Cobertura

If you're producing Cobertura XML (Slather `--cobertura-xml`, custom scripts):

```bash
sonar-scanner \
    -Dsonar.swift.coverage.cobertura.reportPaths=build/coverage.cobertura.xml
```

## Generic SonarQube XML

If nothing else fits, hand-roll the
[SonarQube generic test coverage format](https://docs.sonarqube.org/latest/analyzing-source-code/test-coverage/generic-test-data/):

```xml
<coverage version="1">
    <file path="MyApp/Login.swift">
        <lineToCover lineNumber="12" covered="true"/>
        <lineToCover lineNumber="13" covered="false"/>
        <lineToCover lineNumber="20" covered="true" branchesToCover="2" coveredBranches="1"/>
    </file>
</coverage>
```

Pass via the built-in Sonar property:

```bash
sonar-scanner -Dsonar.coverageReportPaths=build/coverage.xml
```

## Mapping coverage paths to source files

Coverage reports use absolute or build-relative paths. We resolve them to
SonarQube's `InputFile`s by:

1. Direct match (path identical to an `InputFile.absolutePath()`)
2. Suffix match against `InputFile.relativePath()`
3. Best-effort basename match if exactly one `InputFile` has that basename
4. Skip + log at WARN if nothing matches (don't fail the scan)

A file that has *no* coverage entry is treated as 0% covered — same as
SonarJava. Set `sonar.coverage.exclusions` to opt out of coverage for files
you don't want measured.

## Branch coverage caveats

- `xccov` gives line coverage and *region* coverage, not strict branch coverage.
  We map regions to branches conservatively: a line with multiple regions
  contributes regions as branches.
- Slather emits branch data only when the build was configured with
  `-fprofile-arcs -ftest-coverage` (most projects don't).
- The Cobertura format has explicit branch info; we use it verbatim.

## Multi-target / mixed-language projects

Each target's coverage report should be passed separately; the plugin merges.
For mixed Swift+ObjC modules: both languages share the same coverage report
(Xcode merges them at test time). The plugin reads it once and assigns lines
to whichever language owns the file.

## Test execution metrics

In addition to coverage we read test counts from `.xcresult` bundles:

```bash
sonar-scanner -Dsonar.swift.testExecution.xcresultPaths=build/MyApp.xcresult
```

This populates `tests`, `test_failures`, `test_errors`, `skipped_tests`,
`test_execution_time`. Optional but very useful for the Quality Gate.

## Validating locally

```bash
./scripts/scan-local.sh ./examples/ios-sample-app
open http://localhost:9000     # local SonarQube spun by docker-compose
```

You should see a coverage figure on the project's Overview page and per-line
gutters in the file browser.
