#!/usr/bin/env bash
# Scan a Swift project against a locally-running SonarQube.
#
# Usage:
#   ./scripts/scan-local.sh path/to/swift-project [extra sonar-scanner args...]

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="${1:?usage: scan-local.sh <project-dir>}"
shift || true

command -v sonar-scanner >/dev/null || {
    echo "sonar-scanner not installed. brew install sonar-scanner" >&2
    exit 1
}

cd "$PROJECT"

# Resolve parser path
PARSER_BIN="$REPO_ROOT/sonar-swift-parser/.build/release/SwiftSonarParser"
PARSER_ARGS=()
if [[ -x "$PARSER_BIN" ]]; then
    PARSER_ARGS+=(-Dsonar.swift.parser.path="$PARSER_BIN")
fi

# If there's a SwiftLint report at conventional path, pass it
if [[ -f swiftlint.json ]]; then
    PARSER_ARGS+=(-Dsonar.swift.swiftlint.reportPaths=swiftlint.json)
fi

# If there's an .xcresult or coverage.json, pass it
if [[ -d build/Coverage.xcresult ]]; then
    PARSER_ARGS+=(-Dsonar.swift.coverage.xcresultPaths=build/Coverage.xcresult)
elif [[ -f build/coverage.json ]]; then
    PARSER_ARGS+=(-Dsonar.swift.coverage.xccov.reportPaths=build/coverage.json)
fi

# Token if not set
TOKEN="${SONAR_TOKEN:-admin}"
HOST="${SONAR_HOST_URL:-http://localhost:9000}"

sonar-scanner \
    -Dsonar.host.url="$HOST" \
    -Dsonar.token="$TOKEN" \
    "${PARSER_ARGS[@]}" \
    "$@"

echo
echo "Done. Open $HOST/dashboard to inspect the results."
