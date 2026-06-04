# Local dashboard

A small Java-based control console that complements the SonarQube web UI. It
lives in the sonar-swift CLI jar — no extra runtime, no NPM, no Python — and
focuses on **tool-level** controls. The SonarQube UI on `:9000` is still where
you go to see code-analysis results.

## Start it

```bash
make dashboard                                  # foreground
make dashboard-bg                               # detached
sonar-swift dashboard --port 8080 --bind 127.0.0.1
```

Or via docker compose alongside SonarQube:

```bash
docker compose -f docker/docker-compose.yml up -d dashboard
open http://localhost:8080
```

## What it shows

| Page         | Purpose                                                                |
| ------------ | ---------------------------------------------------------------------- |
| `/`          | Overview — SonarQube health, latest local CI run, latest leak report.  |
| `/scans`     | Local CI run history (per-stage pass/fail/duration).                   |
| `/leaks`     | Memory leak reports + an "accept as new budget" button.                |
| `/precommit` | Pre-commit hook history.                                               |
| `/settings`  | Effective configuration (CLI flags + env vars resolved).               |

## API

The dashboard exposes JSON endpoints so you can script against it:

```
GET  /api/health
GET  /api/projects                         # proxies SQ /api/projects/search
GET  /api/ci/runs                          # last local CI run
GET  /api/leaks/latest
GET  /api/leaks/history
GET  /api/precommit/runs
GET  /api/config
POST /api/scan                             # kicks off `sonar-swift scan`
POST /api/leaks/budget/accept              # promotes latest to budget
```

## Why a custom dashboard

We considered:

- A SonarQube web extension — constrained by SonarQube's extension API, would
  bury tool-level controls inside the analysis UI.
- Embedding into Grafana / Prometheus — too much infrastructure for what is
  essentially "kick off a scan, see local state."
- A separate web app (React, Next.js, …) — would add a JS toolchain to a
  Java/Swift repo.

The chosen design (built-in JDK `HttpServer` + vanilla HTML/JS) keeps the
dependency footprint minimal and matches the user expectation of "a single CLI
that does the thing." See [ADR-0006](adr/0006-local-dashboard.md) for the full
decision record.

## Security

The dashboard binds to `127.0.0.1` by default — it's not on the network unless
you explicitly opt in via `--bind 0.0.0.0` or the docker compose port mapping.
There is no authentication; treat it as a localhost developer tool. Don't
expose it via a tunnel without an HTTP basic-auth proxy in front.
