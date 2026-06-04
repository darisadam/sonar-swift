#!/usr/bin/env bash
# Sets up a local dev environment for working on sonar-swift.
#
# What it does:
#   - Checks/install prerequisites (Java 17, Maven, Swift, Docker)
#   - Pulls SonarQube docker image
#   - Builds the plugin
#   - Builds the Swift parser
#   - Starts local SonarQube via docker-compose
#   - Tells you the URL to open
#
# Idempotent: safe to re-run.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

say()  { printf '\033[1;34m[dev]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[dev]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[dev]\033[0m %s\n' "$*" >&2; exit 1; }

# ---- prereqs ----

command -v java   >/dev/null || die "Install JDK 17+ first: brew install temurin@17"
command -v mvn    >/dev/null || die "Install Maven first: brew install maven"
command -v swift  >/dev/null || warn "Swift not on PATH — the parser won't build, but the Java plugin will."
command -v docker >/dev/null || die "Install Docker Desktop first."

JAVA_MAJOR=$(java -version 2>&1 | awk -F\" '/version/{split($2,a,"."); print a[1]}')
[[ "$JAVA_MAJOR" =~ ^(17|18|19|20|21|22|23|24|25)$ ]] || die "Need JDK 17+; you have $JAVA_MAJOR"

# ---- build plugin ----

say "Building Java plugin…"
mvn -q -ntp -DskipTests package

# ---- build parser ----

if command -v swift >/dev/null; then
    say "Building Swift parser…"
    pushd sonar-swift-parser >/dev/null
    swift build -c release
    popd >/dev/null
fi

# ---- spin up SonarQube ----

say "Starting local SonarQube on http://localhost:9000…"
docker compose -f docker/docker-compose.yml up -d
say "Waiting for SonarQube to be UP…"
for _ in {1..30}; do
    if curl -sf http://localhost:9000/api/system/status 2>/dev/null | grep -q '"UP"'; then
        say "SonarQube is up."
        break
    fi
    sleep 5
done

say "Open http://localhost:9000  (default credentials: admin / admin)"
say "Plugin JAR loaded from: sonar-swift-plugin/target/"
say "Try: ./scripts/scan-local.sh examples/ios-sample-app"
