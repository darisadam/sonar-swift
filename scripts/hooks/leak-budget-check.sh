#!/usr/bin/env bash
# Pre-commit hook: enforces the scanner's own memory budget.
#
# Runs scripts/leak/leak-check.sh in "compare" mode against the budget recorded
# in .sonar/leaks/budget.json. If the new measurement exceeds the budget by
# more than the configured slack (default 5%), the commit is blocked.
#
# To accept a new (higher) baseline:
#     make leak-rebudget
#
# To skip (last resort — DO NOT skip casually):
#     SKIP=sonar-swift-leak-budget git commit ...
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
"$REPO_ROOT/scripts/leak/leak-check.sh" --mode=compare --quiet
