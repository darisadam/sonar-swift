# Top-level conveniences. Most heavy lifting lives in scripts/.

.PHONY: help build test parser scan-sample up down logs clean precommit precommit-install \
        ci ci-fast ci-legacy ci-act dashboard dashboard-bg leak-check leak-accept leak-rebudget \
        pipeline-init pipeline-validate pipeline-list pipeline-builtins

help:                  ## Show this help
	@awk 'BEGIN{FS=":.*##"} /^[a-zA-Z_-]+:.*##/ {printf "  %-18s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

# ---- builds ----
build:                 ## Build the Java plugin + CLI
	mvn -q -ntp -DskipTests package

test:                  ## Run all Java tests
	mvn -q -ntp test

parser:                ## Build the Swift parser binary
	cd sonar-swift-parser && swift build -c release

# ---- run / scan ----
scan-sample: build     ## Scan the example iOS project
	./scripts/scan-local.sh examples/ios-sample-app

up:                    ## Start local SonarQube + dashboard (Docker)
	docker compose -f docker/docker-compose.yml up -d

down:                  ## Stop local stack
	docker compose -f docker/docker-compose.yml down

logs:                  ## Tail SonarQube logs
	docker compose -f docker/docker-compose.yml logs -f sonarqube

clean:                 ## Remove build artifacts
	mvn -q -ntp clean
	rm -rf sonar-swift-parser/.build
	rm -rf .sonar/ci .sonar/leaks/leak-*.json

# ---- dashboard ----
dashboard: build       ## Start the local dashboard in the foreground
	java -jar sonar-swift-cli/target/sonar-swift-cli.jar dashboard

dashboard-bg: build    ## Start the dashboard detached (logs to .sonar/dashboard.log)
	mkdir -p .sonar
	nohup java -jar sonar-swift-cli/target/sonar-swift-cli.jar dashboard \
		>.sonar/dashboard.log 2>&1 &
	@echo "Dashboard PID $$! — open http://127.0.0.1:8080"

# ---- pre-commit ----
precommit-install:     ## Install the pre-commit framework + hooks
	@which pre-commit >/dev/null 2>&1 || pip install --user pre-commit
	pre-commit install

precommit:             ## Run pre-commit on all files
	pre-commit run --all-files

# ---- local CI / pipeline ----
ci: build              ## Run the YAML-defined pipeline locally
	java -jar sonar-swift-cli/target/sonar-swift-cli.jar pipeline run

ci-fast: build         ## Run only the `lint` stage
	java -jar sonar-swift-cli/target/sonar-swift-cli.jar pipeline run --stage lint

ci-legacy:             ## The old hardcoded shell runner (kept as fallback)
	./scripts/ci/ci-local.sh

ci-act:                ## Run CI via nektos/act (Docker-based GH Actions emulator)
	./scripts/ci/ci-act.sh

pipeline-init: build   ## Drop a default pipeline.yml in the repo
	java -jar sonar-swift-cli/target/sonar-swift-cli.jar pipeline init

pipeline-validate: build ## Validate the current pipeline.yml schema
	java -jar sonar-swift-cli/target/sonar-swift-cli.jar pipeline validate

pipeline-list: build   ## List stages of the current pipeline
	java -jar sonar-swift-cli/target/sonar-swift-cli.jar pipeline list

pipeline-builtins: build ## List all built-in step types and their params
	java -jar sonar-swift-cli/target/sonar-swift-cli.jar pipeline list --builtins

# ---- leak detection ----
leak-check:            ## Run the leak budget compare
	./scripts/leak/leak-check.sh --mode=compare

leak-accept:           ## Accept the latest measurement as the new leak budget
	./scripts/leak/leak-check.sh --mode=accept

leak-rebudget: leak-check ## Alias — refresh the budget after a known increase
	./scripts/leak/leak-check.sh --mode=accept
