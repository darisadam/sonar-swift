#!/usr/bin/env bash
# Local CI runner — mirrors .github/workflows/build.yml so contributors can
# reproduce the GitHub Actions pipeline before pushing.
#
# Stages (skippable individually):
#   1. validate     — POM + YAML lint, format checks
#   2. plugin       — mvn -B -DskipTests=false package
#   3. parser       — swift build -c release; swift test
#   4. unit         — mvn test (already covered by stage 2 if -DskipTests=false)
#   5. precommit    — pre-commit run --all-files
#   6. integration  — spin up SonarQube via docker, install plugin, scan sample
#   7. leak         — scripts/leak/leak-check.sh --mode=compare
#   8. summary      — print pass/fail per stage, total time
#
# Usage:
#   ./scripts/ci/ci-local.sh                      # all stages
#   ./scripts/ci/ci-local.sh --only=plugin,unit   # subset
#   ./scripts/ci/ci-local.sh --skip=integration   # skip slow stages
#   ./scripts/ci/ci-local.sh --fast               # validate + plugin + unit only
#   ./scripts/ci/ci-local.sh --act                # delegate to nektos/act if installed
#
# Output: ANSI-coloured progress; summary written to .sonar/ci/local-run.json
# so the dashboard can render history.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

# ---- arg parsing ----
MODE=full
ONLY=""
SKIP=""
USE_ACT=0
for arg in "$@"; do
    case "$arg" in
        --only=*)   ONLY="${arg#*=}" ;;
        --skip=*)   SKIP="${arg#*=}" ;;
        --fast)     MODE=fast ;;
        --act)      USE_ACT=1 ;;
        --help|-h)
            sed -n '2,30p' "$0"
            exit 0 ;;
        *)
            echo "Unknown arg: $arg" >&2
            exit 2 ;;
    esac
done

# ---- act passthrough ----
if [[ "$USE_ACT" -eq 1 ]]; then
    if ! command -v act >/dev/null 2>&1; then
        echo "[ci-local] 'act' not installed — brew install act, then re-run." >&2
        exit 2
    fi
    exec act -j plugin --container-architecture linux/amd64
fi

# ---- pretty output ----
COLOR_BLUE=$'\033[1;34m'
COLOR_GREEN=$'\033[1;32m'
COLOR_RED=$'\033[1;31m'
COLOR_YELLOW=$'\033[1;33m'
COLOR_DIM=$'\033[2m'
COLOR_RESET=$'\033[0m'

say()   { printf '%s[ci-local]%s %s\n' "$COLOR_BLUE" "$COLOR_RESET" "$*"; }
good()  { printf '%s[ci-local] ✓%s %s\n' "$COLOR_GREEN" "$COLOR_RESET" "$*"; }
warn()  { printf '%s[ci-local] !%s %s\n' "$COLOR_YELLOW" "$COLOR_RESET" "$*" >&2; }
fail()  { printf '%s[ci-local] ✗%s %s\n' "$COLOR_RED" "$COLOR_RESET" "$*" >&2; }

# ---- stage runner ----
declare -A STAGE_RESULT
declare -A STAGE_DURATION
ALL_STAGES=(validate plugin parser unit precommit integration leak summary)
FAST_STAGES=(validate plugin unit summary)

stages_to_run() {
    if [[ "$MODE" == "fast" ]]; then
        printf '%s\n' "${FAST_STAGES[@]}"
        return
    fi
    if [[ -n "$ONLY" ]]; then
        echo "$ONLY" | tr ',' '\n'
        echo summary
        return
    fi
    if [[ -n "$SKIP" ]]; then
        for s in "${ALL_STAGES[@]}"; do
            if ! echo "$SKIP" | tr ',' '\n' | grep -qx "$s"; then
                echo "$s"
            fi
        done
        return
    fi
    printf '%s\n' "${ALL_STAGES[@]}"
}

run_stage() {
    local name="$1"
    say "▶ ${name}"
    local start_s
    start_s=$(date +%s)
    if "stage_${name}"; then
        STAGE_RESULT[$name]="pass"
        good "${name} (took $(($(date +%s) - start_s))s)"
    else
        STAGE_RESULT[$name]="fail"
        fail "${name} (took $(($(date +%s) - start_s))s)"
    fi
    STAGE_DURATION[$name]=$(($(date +%s) - start_s))
}

# ---- stages ----

stage_validate() {
    say "  Maven validate…"
    mvn -q -ntp validate
    say "  YAML/JSON lint…"
    if command -v yamllint >/dev/null 2>&1; then
        yamllint -d "{rules: {line-length: {max: 200}}}" \
            .github .pre-commit-config.yaml docker/docker-compose.yml >/dev/null 2>&1 || warn "yamllint produced warnings"
    fi
    return 0
}

stage_plugin() {
    say "  Building the Java plugin…"
    mvn -B -ntp -DskipTests=false package
}

stage_parser() {
    if ! command -v swift >/dev/null 2>&1; then
        warn "swift not on PATH — skipping parser stage"
        return 0
    fi
    say "  Building & testing Swift parser…"
    pushd sonar-swift-parser >/dev/null
    swift build -c release
    swift test
    popd >/dev/null
}

stage_unit() {
    say "  Running plugin unit tests (verbose)…"
    mvn -B -ntp test
}

stage_precommit() {
    if ! command -v pre-commit >/dev/null 2>&1; then
        warn "pre-commit not installed — skipping (pip install pre-commit)"
        return 0
    fi
    say "  Running pre-commit hooks on all files…"
    pre-commit run --all-files
}

stage_integration() {
    if ! command -v docker >/dev/null 2>&1; then
        warn "docker not installed — skipping integration stage"
        return 0
    fi
    say "  Spinning up local SonarQube + plugin…"
    docker compose -f docker/docker-compose.yml up -d --wait sonarqube
    say "  Waiting for SonarQube to come up…"
    for _ in $(seq 1 30); do
        if curl -sf http://localhost:9000/api/system/status 2>/dev/null | grep -q '"UP"'; then
            break
        fi
        sleep 5
    done
    if ! curl -sf http://localhost:9000/api/system/status | grep -q '"UP"'; then
        fail "SonarQube did not come up"
        return 1
    fi
    say "  Running sample-project scan…"
    ./scripts/scan-local.sh examples/ios-sample-app
}

stage_leak() {
    say "  Memory leak budget check…"
    "$REPO_ROOT/scripts/leak/leak-check.sh" --mode=compare
}

stage_summary() {
    local outdir="$REPO_ROOT/.sonar/ci"
    mkdir -p "$outdir"
    local outfile="$outdir/local-run.json"

    echo "{" > "$outfile"
    echo "  \"timestamp\": \"$(date -u +%FT%TZ)\"," >> "$outfile"
    echo "  \"mode\": \"$MODE\"," >> "$outfile"
    echo "  \"stages\": {" >> "$outfile"
    local first=1
    for name in "${!STAGE_RESULT[@]}"; do
        [[ "$first" -eq 1 ]] || echo "," >> "$outfile"
        first=0
        printf '    "%s": {"result": "%s", "durationSec": %d}' \
            "$name" "${STAGE_RESULT[$name]}" "${STAGE_DURATION[$name]}" >> "$outfile"
    done
    echo "" >> "$outfile"
    echo "  }" >> "$outfile"
    echo "}" >> "$outfile"

    say "Summary:"
    printf '  %-15s %-6s %s\n' "STAGE" "RESULT" "TIME"
    for name in "${ALL_STAGES[@]}"; do
        [[ -n "${STAGE_RESULT[$name]:-}" ]] || continue
        local color="$COLOR_GREEN"
        [[ "${STAGE_RESULT[$name]}" == "fail" ]] && color="$COLOR_RED"
        printf '  %-15s %b%-6s%b %ss\n' "$name" "$color" "${STAGE_RESULT[$name]}" "$COLOR_RESET" "${STAGE_DURATION[$name]}"
    done
    say "Detailed JSON: $outfile"

    # exit non-zero if any stage failed
    for r in "${STAGE_RESULT[@]}"; do
        if [[ "$r" == "fail" ]]; then
            return 1
        fi
    done
    return 0
}

# ---- main ----
say "sonar-swift local CI runner (mode=${MODE})"
TOTAL_START=$(date +%s)
EXIT_CODE=0
while IFS= read -r stage; do
    [[ -z "$stage" ]] && continue
    run_stage "$stage" || EXIT_CODE=1
done < <(stages_to_run)

say "Total elapsed: $(($(date +%s) - TOTAL_START))s"
exit "$EXIT_CODE"
