#!/usr/bin/env bash
# Pre-commit hook: run `swift-format` on the supplied .swift files.
# Falls through if swift-format is not installed — we don't want pre-commit
# to be a hard blocker for contributors who haven't installed every tool yet.
set -euo pipefail

if ! command -v swift-format >/dev/null 2>&1; then
    echo "[sonar-swift] swift-format not installed — skipping (brew install swift-format to enable)"
    exit 0
fi

CONFIG="$(git rev-parse --show-toplevel)/.swift-format"
ARGS=(lint --strict)
if [[ -f "$CONFIG" ]]; then
    ARGS+=(--configuration "$CONFIG")
fi

rc=0
for f in "$@"; do
    [[ -f "$f" ]] || continue
    if ! swift-format "${ARGS[@]}" "$f"; then
        rc=1
    fi
done
exit "$rc"
