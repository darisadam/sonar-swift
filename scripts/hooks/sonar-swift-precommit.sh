#!/usr/bin/env bash
# Pre-commit hook: run sonar-swift's *quick* mode against the staged files.
#
# Quick mode runs only blocker-tier checks (force-unwrap, force-try, force-cast,
# hardcoded secret, weak crypto, MainActor violation, Sendable misuse). It does
# NOT round-trip to SonarQube — output goes to stderr in a compiler-style format
# so editors can parse it.
#
# Used by:
#   - the pre-commit framework (entry in .pre-commit-config.yaml)
#   - the Makefile (`make precommit`)
#   - CI as an early "fail fast" gate
#
# Implementation: invokes the sonar-swift-cli with `quick-scan` subcommand. The
# CLI handles either:
#   1) the bundled CLI jar at sonar-swift-cli/target/sonar-swift-cli-*.jar (dev)
#   2) `sonar-swift` on PATH (when installed via brew tap / curl bash)
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"

# Pick the CLI binary
SONAR_SWIFT=""
if command -v sonar-swift >/dev/null 2>&1; then
    SONAR_SWIFT="sonar-swift"
elif [[ -f "$REPO_ROOT/sonar-swift-cli/target/sonar-swift-cli.jar" ]]; then
    SONAR_SWIFT="java -jar $REPO_ROOT/sonar-swift-cli/target/sonar-swift-cli.jar"
elif compgen -G "$REPO_ROOT/sonar-swift-cli/target/sonar-swift-cli-*.jar" >/dev/null; then
    # shellcheck disable=SC2012
    JAR=$(ls "$REPO_ROOT"/sonar-swift-cli/target/sonar-swift-cli-*.jar | head -n1)
    SONAR_SWIFT="java -jar $JAR"
else
    echo "[sonar-swift] CLI jar not built — run 'mvn -q -ntp -DskipTests package' first. Skipping hook." >&2
    exit 0
fi

# Collect files passed by pre-commit; filter to .swift / .m / .mm / .h
FILES=()
for f in "$@"; do
    case "$f" in
        *.swift|*.m|*.mm|*.h) [[ -f "$f" ]] && FILES+=("$f") ;;
    esac
done

if [[ ${#FILES[@]} -eq 0 ]]; then
    exit 0
fi

# Run the quick scanner — exits 0 on no blockers, 1 otherwise.
# shellcheck disable=SC2086
$SONAR_SWIFT quick-scan --format=compiler "${FILES[@]}"
