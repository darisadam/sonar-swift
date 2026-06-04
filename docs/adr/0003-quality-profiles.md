# ADR-0003: Three built-in quality profiles

## Status

Accepted — 2026-06-03.

## Context

Teams have radically different tolerances for noise. A startup shipping daily
wants signal — they'll ignore minor warnings. A regulated team wants
everything-on. A team migrating from SwiftLint wants familiar defaults.

## Decision

Ship three built-in quality profiles:

1. **Sonar way** (default) — Opinionated, signal-first. Activates ~60% of
   rules. Mirrors Apple's
   [Swift API Design Guidelines](https://swift.org/documentation/api-design-guidelines/)
   and high-confidence security rules. All `BLOCKER` and `CRITICAL` rules
   active; `MAJOR`/`MINOR` selectively activated.

2. **Strict** — Every rule active, including stylistic ones. Aimed at teams
   that want zero-style-debt or are using sonar-swift as a release gate.

3. **SwiftLint-compat** — Subset that mirrors SwiftLint's default rules with
   matching severity. Migration path for teams moving from SwiftLint as their
   primary lint authority.

Built-in profiles are immutable. Teams that want to customize copy *Sonar
way* and edit the copy. Standard SonarQube workflow.

## Consequences

- **+** Teams get a sensible default without configuration.
- **+** Migration story for SwiftLint shops.
- **−** Three profiles to keep coherent as the rule catalog grows. Each new
  rule's PR must declare its activation status in all three.
- **−** SwiftLint-compat needs to stay in sync with SwiftLint's defaults.
  We snapshot SwiftLint's default ruleset per minor release and update.

## Notes on rule activation policy

When a *new* rule is added in a minor release:

- Activated in *Strict* (always)
- Activated in *Sonar way* only if it's `BLOCKER`/`CRITICAL` or high-precision
  `MAJOR`. Otherwise: ships disabled, can be opted in.
- Activated in *SwiftLint-compat* only if SwiftLint has an equivalent rule.

This prevents the "your CI started failing on Monday because the plugin
updated" surprise.

## Alternatives considered

- **One profile only**: too one-size-fits-all.
- **One profile per platform (iOS / macOS / watchOS)**: not useful — most
  rules are language-level, not platform-level. The 5 platform-specific
  rules can be toggled in copies.
