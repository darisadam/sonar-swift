# ADR-0006 — Local dashboard / console

Date: 2026-06-04
Status: Accepted

## Context

Users (iOS engineers + their leads) asked for a single localhost web surface
to manage sonar-swift scans, see the latest leak report, kick off scans, and
view pre-commit history. SonarQube itself already has a UI at `:9000` that
covers code-analysis results, but it doesn't (and shouldn't) expose
tool-level controls like "run a leak budget check" or "promote the latest
measurement to the new baseline."

## Options considered

1. **SonarQube web extension** — add a sub-tab inside SonarQube's UI via the
   web-extension API. Pros: single URL, looks unified. Cons: limited to the
   constraints of SonarQube's web-ext sandbox; can't shell out to scripts;
   can't bind a port; gates tool-level UX behind SonarQube login.

2. **External web app (React / Next.js / SvelteKit)** — separate codebase,
   modern toolchain. Pros: modern dev experience. Cons: adds a JS toolchain
   to a Java + Swift repo; requires Node at build time; more deps to audit.

3. **Built into the CLI jar** (chosen) — small JDK `HttpServer` + static
   HTML/CSS/JS classpath resources. Pros: zero extra runtime, single jar to
   ship, runs on any JDK 17 box. Cons: hand-rolled JSON / templating; no SSR.

## Decision

We ship the dashboard inside the existing `sonar-swift-cli` jar, served by
`com.sun.net.httpserver.HttpServer` (built into the JDK). Pages are static
HTML with vanilla JS; assets live under `src/main/resources/dashboard/`. The
JSON layer is hand-rolled (`JsonWriter`) so the CLI stays free of Jackson.

The dashboard binds to `127.0.0.1` by default — not on the network unless the
operator opts in via `--bind 0.0.0.0` (used by the Docker compose service).

## Consequences

- No new runtime dependencies. The CLI jar remains small (under 1 MB).
- We accept some hand-rolling effort: a tiny JSON writer, no JSX, no SSR. The
  pages have ~6 routes total — this isn't a SaaS dashboard.
- If the dashboard grows beyond simple Map/List/primitive responses, replace
  `JsonWriter` with Jackson via `maven-shade-plugin` (the CLI is already a
  fat-jar candidate). Recheck this decision when the JS bundle exceeds 50 KB.
- Templates use a trivial `{{baseUrl}}` replacement, no escaping — that's
  fine because the templates are not user-controlled.
- Auth: deliberately none. Treat the dashboard as a localhost developer tool;
  for shared-environment deployments, put basic-auth in front.

## Related

- [`docs/dashboard.md`](../dashboard.md) — user-facing docs
- [`sonar-swift-cli/.../dashboard/`](../../sonar-swift-cli/src/main/java/io/sonarswift/cli/dashboard/) — implementation
