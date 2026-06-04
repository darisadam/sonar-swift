# Pre-commit hooks

`sonar-swift` ships an opinionated [`.pre-commit-config.yaml`](../.pre-commit-config.yaml)
so contributors and consumer-project teams can catch problems before they reach
CI.

## Install

```bash
pip install pre-commit          # one-time, per dev machine
pre-commit install              # writes .git/hooks/pre-commit for this repo
```

Or via `make`:

```bash
make precommit-install
```

## Run on demand

```bash
pre-commit run --all-files
# or
make precommit
```

## What runs

| Hook                          | What it does                                                       |
| ----------------------------- | ------------------------------------------------------------------ |
| `check-yaml` / `check-json`   | Parses every YAML / JSON file in the staged set.                   |
| `check-merge-conflict`        | Refuses commits with `<<<<<<<` markers.                            |
| `check-added-large-files`     | Blocks commits with files over 512 KB.                             |
| `end-of-file-fixer`           | Ensures trailing newline.                                          |
| `trailing-whitespace`         | Strips trailing whitespace from text files.                        |
| `detect-private-key`          | Refuses commits with PEM-style private keys.                       |
| `swift-format`                | Runs Apple's `swift-format lint --strict` on changed `.swift`.     |
| `swiftlint`                   | Runs `swiftlint --strict` on changed `.swift`.                     |
| `sonar-swift-quick`           | Lightweight native scan against the changed Apple-platform files.  |
| `sonar-swift-leak-budget`     | When plugin/parser sources change, re-runs the leak budget check.  |
| `maven-validate`              | Validates POMs when `pom.xml` is staged.                           |

## Quick-scan mode

The `sonar-swift quick-scan` subcommand is what powers the `sonar-swift-quick`
hook. It's a pure-Java scanner that re-implements the highest-signal rules from
the plugin (force-unwrap, force-try, hardcoded secrets, MD5, weak concurrency
patterns) so the hook stays fast (no SonarQube round-trip).

Invoke it directly to ad-hoc scan a folder:

```bash
java -jar sonar-swift-cli/target/sonar-swift-cli.jar quick-scan path/to/file.swift
java -jar sonar-swift-cli/target/sonar-swift-cli.jar quick-scan --format=json src/
```

Exit code is `1` if any **BLOCKER** finding is reported, `0` otherwise. That
allows pre-commit to gate commits on hard bugs while still surfacing softer
findings as warnings.

## Skipping the hook (last resort)

```bash
SKIP=sonar-swift-leak-budget git commit -m "…"
git commit --no-verify  -m "…"        # NOT recommended
```

Prefer fixing the cause. The leak budget can be refreshed with:

```bash
make leak-rebudget
```

after a known increase has been audited.
