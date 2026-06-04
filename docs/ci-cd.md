# CI/CD integration

Reference recipes for every common Apple-stack pipeline.

## Prerequisites everywhere

- Java 17 or 21 on the runner (only required where `sonar-scanner` itself runs)
- A reachable SonarQube server (URL + token)
- The `sonar-swift-plugin-X.Y.Z.jar` installed in `$SONAR_HOME/extensions/plugins/`
  on the server (one-time admin step)
- macOS runner for the *test + xccov* step; the *scanner* step can run on Linux

## sonar-project.properties — minimal

```properties
sonar.projectKey=com.example.myapp
sonar.projectName=MyApp
sonar.projectVersion=1.0
sonar.sources=Sources
sonar.tests=Tests
sonar.sourceEncoding=UTF-8

sonar.swift.file.suffixes=.swift
sonar.objc.file.suffixes=.h,.m,.mm

# pick your coverage path:
sonar.swift.coverage.xcresultPaths=build/MyApp.xcresult
```

## GitHub Actions

`.github/workflows/sonar.yml`:

```yaml
name: SonarQube

on:
  push:
    branches: [main]
  pull_request:

jobs:
  test-and-scan:
    runs-on: macos-15
    steps:
      - uses: actions/checkout@v5
        with:
          fetch-depth: 0      # Sonar needs full history for new-code period

      - name: Run tests with coverage
        run: |
          xcodebuild test \
              -workspace MyApp.xcworkspace \
              -scheme MyApp \
              -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
              -enableCodeCoverage YES \
              -resultBundlePath build/MyApp.xcresult

      - name: SonarQube scan
        uses: SonarSource/sonarqube-scan-action@v3
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
        with:
          args: >
            -Dsonar.swift.coverage.xcresultPaths=build/MyApp.xcresult
            -Dsonar.swift.swiftlint.reportPath=build/swiftlint.json
            -Dsonar.swift.periphery.reportPath=build/periphery.json
```

Or via our composite action (1.0+):

```yaml
- uses: sonar-swift/scan-action@v1
  with:
    sonar-token: ${{ secrets.SONAR_TOKEN }}
    sonar-host: ${{ secrets.SONAR_HOST_URL }}
    xcresult-path: build/MyApp.xcresult
```

## GitLab CI

`.gitlab-ci.yml`:

```yaml
stages: [test, sonar]

test:
  stage: test
  tags: [macos]
  script:
    - xcodebuild test
        -workspace MyApp.xcworkspace
        -scheme MyApp
        -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest'
        -enableCodeCoverage YES
        -resultBundlePath build/MyApp.xcresult
    - xcrun xccov view --report --json build/MyApp.xcresult > build/coverage.json
  artifacts:
    paths: [build/coverage.json]

sonar:
  stage: sonar
  image: sonarsource/sonar-scanner-cli:latest
  needs: [test]
  script:
    - sonar-scanner -Dsonar.swift.coverage.xccov.reportPaths=build/coverage.json
```

## Bitrise

```yaml
workflows:
  test-and-scan:
    steps:
      - xcode-test:
          inputs:
            - scheme: MyApp
            - generate_code_coverage_files: 'yes'
      - script:
          inputs:
            - content: |
                xcrun xccov view --report --json \
                  $BITRISE_XCRESULT_PATH > build/coverage.json
      - sonarqube-scanner@1:
          inputs:
            - extra_arguments: >
                -Dsonar.swift.coverage.xccov.reportPaths=build/coverage.json
```

## Fastlane (1.0+)

```ruby
lane :sonar_scan do
  scan(
    workspace: "MyApp.xcworkspace",
    scheme: "MyApp",
    code_coverage: true,
    result_bundle: true
  )
  sonar_swift(
    project_key: "com.example.myapp",
    sonar_url: ENV["SONAR_HOST_URL"],
    sonar_token: ENV["SONAR_TOKEN"],
    xcresult: lane_context[SharedValues::SCAN_DERIVED_DATA_PATH] + "/Logs/Test/*.xcresult"
  )
end
```

## Xcode Cloud

Xcode Cloud is locked to Apple's defaults but supports custom scripts via
`ci_scripts/ci_post_xcodebuild.sh`:

```bash
#!/bin/sh
set -e
xcrun xccov view --report --json "$CI_RESULT_BUNDLE_PATH" > "$CI_DERIVED_DATA_PATH/coverage.json"

# Send to SonarQube
curl -sSfL https://github.com/sonar-swift/sonar-swift/releases/latest/download/sonar-scanner.tgz | tar -xz
./sonar-scanner/bin/sonar-scanner \
    -Dsonar.host.url=$SONAR_HOST_URL \
    -Dsonar.token=$SONAR_TOKEN \
    -Dsonar.swift.coverage.xccov.reportPaths="$CI_DERIVED_DATA_PATH/coverage.json"
```

`SONAR_HOST_URL` and `SONAR_TOKEN` are set as Xcode Cloud environment
variables.

## CircleCI

`.circleci/config.yml`:

```yaml
version: 2.1
orbs:
  macos: circleci/macos@2
jobs:
  test-and-scan:
    macos:
      xcode: 26.0.0
    steps:
      - checkout
      - run:
          name: Test
          command: |
            xcodebuild test \
                -workspace MyApp.xcworkspace \
                -scheme MyApp \
                -destination 'platform=iOS Simulator,OS=latest,name=iPhone 16' \
                -enableCodeCoverage YES \
                -resultBundlePath build/MyApp.xcresult
      - run:
          name: SonarQube
          command: |
            sonar-scanner \
                -Dsonar.swift.coverage.xcresultPaths=build/MyApp.xcresult
```

## Jenkins (declarative pipeline)

```groovy
pipeline {
  agent { label 'mac' }
  stages {
    stage('Test') {
      steps {
        sh '''
          xcodebuild test \\
              -workspace MyApp.xcworkspace \\
              -scheme MyApp \\
              -enableCodeCoverage YES \\
              -resultBundlePath build/MyApp.xcresult
        '''
      }
    }
    stage('SonarQube') {
      steps {
        withSonarQubeEnv('SonarQube') {
          sh 'sonar-scanner -Dsonar.swift.coverage.xcresultPaths=build/MyApp.xcresult'
        }
      }
    }
  }
}
```

## Caching `sonar-scanner` locally for dev

```bash
brew install sonar-scanner
# Plugin gets installed to your local SonarQube server (Docker or native) once.
make scan
```

`make scan` is a Makefile target we ship in the sample app — it does the
test → xccov → sonar-scan dance in one command.

## Branch / PR analysis

Set the standard SonarQube branch properties in CI:

```bash
sonar-scanner \
    -Dsonar.branch.name=$GITHUB_HEAD_REF \
    -Dsonar.branch.target=$GITHUB_BASE_REF \
    -Dsonar.pullrequest.key=$PR_NUMBER \
    -Dsonar.pullrequest.branch=$GITHUB_HEAD_REF \
    -Dsonar.pullrequest.base=$GITHUB_BASE_REF
```

PR decoration (comments on the PR) is a SonarQube
Developer/Enterprise-edition feature — not something this plugin adds. We
just feed the right metrics.
