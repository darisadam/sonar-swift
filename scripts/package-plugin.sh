#!/usr/bin/env bash
# Produce a distributable plugin JAR. Used by the release workflow.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

mvn -q -ntp -DskipTests package
echo "Built: sonar-swift-plugin/target/$(ls -1 sonar-swift-plugin/target/sonar-swift-plugin-*.jar | xargs -n1 basename)"
