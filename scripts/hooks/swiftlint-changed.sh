#!/usr/bin/env bash
# Pre-commit hook: run SwiftLint against the supplied paths.
# Auto-discovers .swiftlint.yml at repo root.
# If SwiftLint is not installed, the hook tells the user how to install it and
# exits 0 — we deliberately don't block the commit just because the tool is
# missing on this machine.
set -euo pipefail

if ! command -v swiftlint >/dev/null 2>&1; then
    cat <<EOF >&2
[sonar-swift] swiftlint not installed — skipping.
              Install with: brew install swiftlint
              Or:           mint install realm/SwiftLint
EOF
    exit 0
fi

REPO_ROOT="$(git rev-parse --show-toplevel)"
CONFIG="$REPO_ROOT/.swiftlint.yml"

ARGS=(lint --quiet --reporter emoji --strict)
if [[ -f "$CONFIG" ]]; then
    ARGS+=(--config "$CONFIG")
fi

# Filter to .swift files (pre-commit may pass other extensions through this hook
# when it's chained with other file types)
TARGETS=()
for f in "$@"; do
    [[ "$f" == *.swift && -f "$f" ]] && TARGETS+=("$f")
done

if [[ ${#TARGETS[@]} -eq 0 ]]; then
    exit 0
fi

swiftlint "${ARGS[@]}" "${TARGETS[@]}"
