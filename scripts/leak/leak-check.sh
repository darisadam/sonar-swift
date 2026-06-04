#!/usr/bin/env bash
# Self-diagnostic for the sonar-swift scanner.
#
# Three modes:
#   --mode=capture   run the scanner against the sample project, write a fresh
#                    .sonar/leaks/latest.json
#   --mode=compare   run capture, then compare against .sonar/leaks/budget.json.
#                    Exit non-zero if used heap is over budget + slack.
#   --mode=accept    treat the latest report as the new budget. Used after a
#                    legitimate increase (new sensor, new rules, etc.).
#
# Options:
#   --slack=0.05     allowable headroom over budget (default 5%)
#   --quiet          suppress informational output (pre-commit hook uses this)
#
# Output: pretty summary to stdout; machine-readable JSON to .sonar/leaks/.
#
# Why the budget approach: absolute "leak detection" against a runtime that
# doesn't expose allocation graphs is unreliable. We instead lock in a
# *baseline footprint* — known-good heap usage on a known input — and fail
# when that footprint grows unexpectedly. This catches regressions like an
# accidentally-static cache, an unbounded queue, or a parser sidecar that
# doesn't release tokens.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

MODE="capture"
SLACK="0.05"
QUIET=0
for arg in "$@"; do
    case "$arg" in
        --mode=*) MODE="${arg#*=}" ;;
        --slack=*) SLACK="${arg#*=}" ;;
        --quiet) QUIET=1 ;;
        --help|-h)
            sed -n '2,30p' "$0"
            exit 0 ;;
        *) echo "Unknown arg: $arg" >&2; exit 2 ;;
    esac
done

LEAKS_DIR="$REPO_ROOT/.sonar/leaks"
LATEST="$LEAKS_DIR/latest.json"
BUDGET="$LEAKS_DIR/budget.json"
mkdir -p "$LEAKS_DIR"

say() { [[ "$QUIET" -eq 1 ]] || printf '[leak-check] %s\n' "$*"; }

# ---- capture ----
capture() {
    say "Running scanner against examples/ios-sample-app with leak detection on…"

    # Build artifacts if missing
    if ! ls sonar-swift-plugin/target/sonar-swift-plugin-*.jar >/dev/null 2>&1; then
        say "Plugin jar missing — building…"
        mvn -q -ntp -DskipTests package
    fi

    # Run a scan with leak sensor enabled. We can't depend on SonarQube being
    # up here — the sensor writes the report regardless of whether the scan's
    # publish step succeeds, because the sensor runs before the publisher.
    SAMPLE="$REPO_ROOT/examples/ios-sample-app"
    pushd "$SAMPLE" >/dev/null

    SONAR_HOST_URL="${SONAR_HOST_URL:-http://localhost:9000}"
    SONAR_TOKEN="${SONAR_TOKEN:-admin}"

    PARSER_BIN="$REPO_ROOT/sonar-swift-parser/.build/release/SwiftSonarParser"
    EXTRA=()
    if [[ -x "$PARSER_BIN" ]]; then
        EXTRA+=(-Dsonar.swift.parser.path="$PARSER_BIN")
    fi

    JAVA_OPTS="-Xmx512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$LEAKS_DIR/oom.hprof" \
    sonar-scanner \
        -Dsonar.host.url="$SONAR_HOST_URL" \
        -Dsonar.token="$SONAR_TOKEN" \
        -Dsonar.swift.leak.enabled=true \
        -Dsonar.swift.leak.budgetPath="$BUDGET" \
        "${EXTRA[@]}" \
        || say "sonar-scanner returned non-zero (probably no SonarQube reachable). Report still captured."

    popd >/dev/null

    if [[ ! -f "$LATEST" ]]; then
        echo "[leak-check] FAILED — no .sonar/leaks/latest.json produced" >&2
        return 1
    fi

    say "Capture complete:"
    [[ "$QUIET" -eq 1 ]] || jq -r '
        "  heap.used      = \(.heap.used)",
        "  heap.committed = \(.heap.committed)",
        "  classes.loaded = \(.classes.loaded)",
        "  threads.peak   = \(.threads.peak)",
        "  parser.resident = \(.parser.residentBytes // "n/a")"
    ' "$LATEST" 2>/dev/null || cat "$LATEST"
}

# ---- compare ----
compare() {
    if [[ ! -f "$BUDGET" ]]; then
        say "No budget yet — capturing initial baseline."
        capture
        cp "$LATEST" "$BUDGET"
        say "Baseline written to $BUDGET — re-run --mode=compare after changes."
        return 0
    fi
    capture

    # Compare used heap. jq is the tool of choice; fall back to grep if missing.
    if command -v jq >/dev/null 2>&1; then
        BUDGET_USED=$(jq -r '.heap.used' "$BUDGET")
        CURRENT_USED=$(jq -r '.heap.used' "$LATEST")
    else
        BUDGET_USED=$(grep -oE '"used"[[:space:]]*:[[:space:]]*[0-9]+' "$BUDGET" | head -n1 | grep -oE '[0-9]+')
        CURRENT_USED=$(grep -oE '"used"[[:space:]]*:[[:space:]]*[0-9]+' "$LATEST" | head -n1 | grep -oE '[0-9]+')
    fi

    ALLOWED=$(python3 -c "print(int($BUDGET_USED * (1.0 + $SLACK)))" 2>/dev/null \
        || awk "BEGIN { printf \"%d\", $BUDGET_USED * (1 + $SLACK) }")

    if [[ "$CURRENT_USED" -le "$ALLOWED" ]]; then
        say "OK — used=$CURRENT_USED bytes, budget=$BUDGET_USED, allowed up to $ALLOWED (slack ${SLACK})"
        return 0
    fi

    echo "[leak-check] REGRESSION — used=$CURRENT_USED bytes, budget=$BUDGET_USED, allowed=$ALLOWED" >&2
    echo "[leak-check] If this growth is intentional: run scripts/leak/leak-check.sh --mode=accept" >&2
    return 1
}

# ---- accept ----
accept() {
    if [[ ! -f "$LATEST" ]]; then
        echo "[leak-check] no .sonar/leaks/latest.json — run --mode=capture first" >&2
        return 1
    fi
    cp "$LATEST" "$BUDGET"
    say "Budget updated → $BUDGET"
}

case "$MODE" in
    capture) capture ;;
    compare) compare ;;
    accept)  accept ;;
    *)       echo "Unknown mode: $MODE" >&2; exit 2 ;;
esac
