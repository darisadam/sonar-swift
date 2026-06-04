# Pipelines

The `sonar-swift pipeline` subcommand defines, validates, and runs a YAML-based
CI/CD pipeline locally. Same shape as GitHub Actions / GitLab CI, but executed
on your dev box (or in any container) by the bundled `sonar-swift-cli.jar`.

```bash
sonar-swift pipeline init       # scaffold .sonar-swift/pipeline.yml
sonar-swift pipeline validate   # check the schema
sonar-swift pipeline list       # list stages
sonar-swift pipeline list --builtins   # catalogue of built-in steps
sonar-swift pipeline run        # execute the pipeline
```

Or via `make`: `make ci`, `make pipeline-init`, `make pipeline-list`,
`make pipeline-builtins`, `make pipeline-validate`.

## Schema

```yaml
name: <pipeline name>            # required
description: <text>              # optional
env:                             # global env, merged into every step
  KEY: value

failFast: false                  # stop the whole pipeline on first failed stage

stages:
  - id: <stable-id>              # required, unique
    name: <human-readable>       # optional, falls back to id
    needs: [<other-stage-id>]    # explicit DAG dependency; default sequential
    when: <expression>           # optional skip — see expressions below
    parallel: false              # run steps concurrently within the stage
    continueOnError: false       # a failure here doesn't fail the pipeline
    env:                         # stage-level env, overrides pipeline env
      KEY: value
    steps:
      - id: <stable-id>          # optional; auto-derived from `uses` / `run`
        name: <human-readable>   # optional
        uses: <built-in-id>      # one of the catalogued built-ins (see below)
        with:                    # built-in params
          key: value
        # OR
        run: <shell command>     # literal `sh -c "…"`
        env: { KEY: value }      # step-level env
        workingDirectory: subdir # relative to project root
        timeout: 1800            # seconds
        when: <expression>
        continueOnError: false
```

## Built-in steps

Run `sonar-swift pipeline list --builtins` for an always-up-to-date list with
parameters. Today's catalogue (15 steps):

| `uses:`            | What it does                                                                                  |
| ------------------ | --------------------------------------------------------------------------------------------- |
| `swiftlint`        | Runs SwiftLint (lint / analyze / fix) with the project `.swiftlint.yml`.                      |
| `swift-format`     | Apple's swift-format in `lint` or `format` mode.                                              |
| `xcode-build`      | `xcodebuild build` for a workspace or project.                                                |
| `xcode-test`       | `xcodebuild test` — unit / UI / integration via `onlyTesting`. Supports parallel & retries.   |
| `xccov`            | Extracts coverage JSON from an `.xcresult` via `xcrun xccov`.                                 |
| `slather`          | Generates Cobertura XML coverage via Slather.                                                 |
| `gitleaks`         | Scans the working tree / git history for committed secrets.                                   |
| `trufflehog`       | Secrets scanner with verified-only mode.                                                      |
| `semgrep`          | SAST scan with Semgrep rule packs.                                                            |
| `trivy-fs`         | Filesystem vulnerability scan via Trivy.                                                      |
| `dependency-check` | OWASP Dependency-Check.                                                                       |
| `sonar-scanner`    | Invokes `sonar-scanner` with the sonar-swift defaults wired in.                               |
| `sonar-swift-quick`| Runs the bundled native quick-scan (the same checks pre-commit uses).                         |
| `command`          | Explicit literal shell command — same effect as the top-level `run:` form.                    |
| `script`           | Invokes a script file at `path:` with optional `args:`.                                       |

Each built-in's required and optional parameters are printed by
`pipeline list --builtins`.

## Expressions

A tiny DSL is recognised inside `when:` and string params via `${{ … }}`:

- `${{ env.NAME }}` — env var lookup; empty if unset.
- `${{ env.NAME == 'value' }}` / `${{ env.NAME != 'value' }}` — comparison → "true" / "false".
- `always()`, `success()`, `failure()` — for conditional stages.

```yaml
- id: ui-test
  when: ${{ env.RUN_UI_TESTS != 'false' }}
  ...
```

## Pre-built templates

```bash
sonar-swift pipeline init --template ios-app          # default
sonar-swift pipeline init --template swift-package
sonar-swift pipeline init --template minimal
```

The `ios-app` template wires up the canonical iOS flow: lint → secret-scan
(gitleaks + trufflehog) → vulnerability-scan (trivy + dependency-check) →
build → unit + UI + integration tests → coverage → SonarQube. Everything is
opt-out via env vars (`RUN_UI_TESTS=false`, etc.).

## Run artefacts

Each run gets a unique `runId` (uuid8-epochMillis). Output lives under:

```
.sonar/ci/<runId>/
    state.json                      ← run metadata + per-step status
    <stage>/<step>.log              ← stdout + stderr per step
.sonar/ci/latest.json               ← pointer to the most recent run
```

The dashboard at `http://127.0.0.1:8080/pipeline` reads these and renders a
graph view of stages, step status, and clickable log previews.

## CI integration

In CI environments where `GITHUB_ACTIONS=true`, the reporter wraps each step
in `::group::` markers so the Actions UI shows collapsible step blocks
naturally. A failed step emits `::error::Step <name> failed`.

To run a pipeline as part of GitHub Actions:

```yaml
- name: sonar-swift pipeline
  run: |
    java -jar sonar-swift-cli/target/sonar-swift-cli.jar pipeline run
```

## Authoring tips

- **Parallel where you can**: a `lint` stage with `parallel: true` and three
  `uses:` entries (swiftlint, swift-format, sonar-swift-quick) finishes in
  the time of the slowest single tool, not their sum.
- **Use `continueOnError`** for tools you want signal from but don't want
  blocking the build yet (e.g. a new Semgrep rule pack you're trialling).
- **Always pass `xcresultPath`** to `xcode-test` — it's how the next stages
  feed coverage to `xccov` and tests to `sonar-scanner`.
- **Secrets**: don't write tokens into `env:` blocks. Reference them via
  `${{ env.SONAR_TOKEN }}` and inject from your CI / shell env.

## Why a custom runner instead of `act` / Bitrise / Fastlane

- **No Docker / Node / Ruby required** — single Java jar, runs anywhere a JDK
  17 runs (which is everywhere SonarQube already runs).
- **Same engine on dev box and CI** — the pipeline that ran locally is the
  same code path that ran in CI. No "works on my machine" YAML divergence.
- **First-class sonar-swift integration** — pipeline steps know about sonar-
  scanner properties, xccov outputs, leak budget, dashboard. Glue you'd
  otherwise write by hand in shell.

Future work: emit equivalent GitHub Actions / GitLab CI YAML from the same
pipeline definition (`sonar-swift pipeline export --target=github-actions`).
