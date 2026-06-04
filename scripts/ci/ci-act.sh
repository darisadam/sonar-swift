#!/usr/bin/env bash
# Wrapper around https://github.com/nektos/act that mirrors our GH Actions
# workflow on a local Docker engine. Use this when you want bit-exact CI
# reproduction. For day-to-day iteration use scripts/ci/ci-local.sh — it's
# faster because it doesn't spin a container per step.
#
# First run:
#     brew install act
#     act --list                # show the jobs
#
# Common invocations:
#     ./scripts/ci/ci-act.sh                # run all jobs
#     ./scripts/ci/ci-act.sh plugin         # run only the plugin job
#     ./scripts/ci/ci-act.sh -j integration # explicit job filter
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

if ! command -v act >/dev/null 2>&1; then
    cat <<EOF >&2
[ci-act] 'act' not installed.
         Install:  brew install act
         Docs:     https://github.com/nektos/act
EOF
    exit 2
fi

# Default: run the build.yml workflow on a recent ubuntu image (medium-size — the
# default is a stripped-down ubuntu that can't run the Swift parser stage).
EVENT="push"
JOB="${1:-}"
shift || true

ARGS=(
    -W .github/workflows/build.yml
    --container-architecture linux/amd64
    -P ubuntu-24.04=catthehacker/ubuntu:full-24.04
)
if [[ -n "$JOB" && "$JOB" != "-j" ]]; then
    ARGS+=(-j "$JOB")
fi

# Forward extra args
act "$EVENT" "${ARGS[@]}" "$@"
