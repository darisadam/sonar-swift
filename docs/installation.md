# Installation

## Prerequisites

- **SonarQube server**: 10.x LTS or 25.x community / developer / enterprise.
- **Java 17+** on whatever machine runs `sonar-scanner`.
- **macOS** for the test/coverage step (only if you want Xcode coverage; the
  scan step itself runs on any OS).
- Optional for native AST analysis: Swift 5.10+ toolchain (macOS or Linux) and
  `clang` 15+ on `$PATH`.
- Optional integrations: `swiftlint`, `oclint`, `periphery`, `tailor` on `$PATH`.

## Install the plugin

### Server-side (once per SonarQube instance)

1. Download `sonar-swift-plugin-X.Y.Z.jar` from the
   [GitHub Releases page](https://github.com/sonar-swift/sonar-swift/releases).
2. Copy it to `$SONAR_HOME/extensions/plugins/`.
3. Restart SonarQube:

   ```bash
   $SONAR_HOME/bin/<your-os>/sonar.sh restart
   ```

4. Log in as admin and confirm in **Administration → Marketplace → Installed**
   that "Sonar Swift" appears.

### Verify in UI

- **Quality Profiles** → languages should now include *Swift* and *Objective-C*.
- **Quality Profiles** → for each: *Sonar way*, *Strict*, *SwiftLint-compat*
  built-in profiles exist.
- **Rules** → filter by language; you should see the catalogs.

## Install the scanner

### macOS

```bash
brew install sonar-scanner
```

### Linux (Docker)

```bash
docker pull sonarsource/sonar-scanner-cli:latest
docker run --rm -v "$(pwd):/usr/src" sonarsource/sonar-scanner-cli
```

### Per-project config (`sonar-project.properties`)

```properties
sonar.projectKey=com.example.myapp
sonar.projectName=MyApp
sonar.projectVersion=1.0
sonar.sources=Sources
sonar.tests=Tests
sonar.sourceEncoding=UTF-8
```

Defaults that usually need no override:

```properties
sonar.swift.file.suffixes=.swift
sonar.objc.file.suffixes=.h,.m,.mm

# Where the plugin finds the helper Swift parser binary.
# Default: bundled inside the plugin JAR, extracted to a temp dir.
# Override only if you want to run a custom build.
# sonar.swift.parser.path=/usr/local/bin/SwiftSonarParser

# clang for ObjC AST. Default: $(xcrun --find clang).
# sonar.objc.clang.path=/usr/bin/clang
```

## First scan

```bash
cd /path/to/MyApp

# Run tests + generate coverage (recommended)
xcodebuild test \
    -workspace MyApp.xcworkspace \
    -scheme MyApp \
    -enableCodeCoverage YES \
    -resultBundlePath build/MyApp.xcresult

# Run the scan
sonar-scanner \
    -Dsonar.host.url=$SONAR_HOST_URL \
    -Dsonar.token=$SONAR_TOKEN \
    -Dsonar.swift.coverage.xcresultPaths=build/MyApp.xcresult
```

Open SonarQube and you should see the project with issues, metrics, and
coverage.

## Local SonarQube for development

```bash
git clone https://github.com/sonar-swift/sonar-swift
cd sonar-swift
docker-compose -f docker/docker-compose.yml up -d
```

This starts SonarQube on `http://localhost:9000` with our plugin pre-loaded
from the dev build. Default credentials `admin / admin` (change on first
login).

Then:

```bash
make scan-sample      # scans examples/ios-sample-app, opens results in browser
```

## Upgrading

In-place: drop the new JAR in `extensions/plugins`, remove the old one,
restart SonarQube. New rules default to *disabled* in the *Sonar way* profile
on every minor release — you opt them in by going to **Quality Profiles →
Sonar way → Activate More**.

Major-version upgrades may renumber rules; we publish a migration guide per
major release.

## Troubleshooting

| Symptom                                         | Likely cause / fix                                                       |
| ----------------------------------------------- | ------------------------------------------------------------------------ |
| "No Swift files found"                          | `sonar.sources` doesn't include your Swift dir                            |
| "Coverage file not found"                       | Path is relative to project root; double-check                            |
| "Swift parser process exited with code 9"       | Memory pressure — set `SONAR_SCANNER_OPTS="-Xmx2g"`                       |
| "clang: error: unknown argument"                | clang version too old; need 15+. `xcrun --find clang` should point at Xcode's |
| "Rule SXXXX raised on every line"               | False positive — file a bug with a minimal repro                          |
| Plugin missing from UI after install            | Check `sonar.log` for class-loader errors; usually a SonarQube version mismatch |

Need help? Open an issue:
<https://github.com/sonar-swift/sonar-swift/issues>.
