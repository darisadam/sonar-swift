---
name: run-local-sonar
description: Spin up a local SonarQube via Docker with the freshly-built plugin pre-loaded, ready for development scans. Use when the user wants to "test the plugin locally", "see what changes look like in SonarQube", "spin up Sonar".
---

# run-local-sonar

Brings up a local SonarQube + Postgres pair on the developer's machine with
the locally-built plugin JAR auto-loaded from `sonar-swift-plugin/target/`.

## Steps

1. **Pre-flight**:
   - Verify Docker daemon is running: `docker info >/dev/null`
   - Verify the plugin has been built. If not, build it:
     `mvn -q -ntp -DskipTests package`
   - Verify port 9000 is free: `lsof -ti :9000 || echo free`. If something
     is already on 9000, ask the user before killing it.

2. **Start the stack**:
   ```bash
   docker compose -f docker/docker-compose.yml up -d
   ```

3. **Wait for SonarQube to come up** (takes ~60s on first run):
   ```bash
   for i in {1..30}; do
       status=$(curl -sf http://localhost:9000/api/system/status 2>/dev/null || echo '{}')
       if echo "$status" | grep -q '"UP"'; then
           echo "SonarQube up"
           break
       fi
       sleep 5
   done
   ```

4. **Report**:
   - URL: <http://localhost:9000>
   - Default credentials: `admin / admin` (force-change on first login)
   - Plugin loaded from: `sonar-swift-plugin/target/sonar-swift-plugin-*.jar`
   - Stop command: `docker compose -f docker/docker-compose.yml down`

5. **Optional**: offer to run the sample scan immediately:
   `./scripts/scan-local.sh examples/ios-sample-app`

## When the plugin doesn't appear in the UI

1. Confirm the JAR exists in `sonar-swift-plugin/target/`.
2. Check `docker logs sonar-swift-sq | tail -100` for class-loader errors.
3. The compose file bind-mounts the target dir — confirm the volume mount
   resolved correctly: `docker exec sonar-swift-sq ls /opt/sonarqube/extensions/plugins`.
4. If the plugin is listed but doesn't show languages in the UI, the
   `RulesDefinition` likely threw at startup — check the SQ container's
   `sonar.log`.

## When to escalate

- SonarQube refuses to start with `Elasticsearch` bootstrap errors → most
  likely the host's `vm.max_map_count` is too low on Linux. Set it via
  `sudo sysctl -w vm.max_map_count=262144`.
- Container OOMs → bump Docker Desktop's memory allocation to ≥ 4 GB.
