---
name: sync-swiftlint
description: Pull the current SwiftLint default rule list and update the SwiftLint-compat quality profile in this plugin so it stays aligned. Use when the user says "sync swiftlint", "update SwiftLint-compat profile", "SwiftLint released a new version".
---

# sync-swiftlint

Keeps the *SwiftLint-compat* built-in quality profile in sync with SwiftLint's
upstream defaults.

## Why this matters

The *SwiftLint-compat* profile is the migration path for teams moving from
SwiftLint to SonarQube. It only includes rules that have a SwiftLint
equivalent, mirroring SwiftLint's default activation. When SwiftLint adds /
removes / re-defaults a rule, our profile drifts.

## Steps

1. **Get the current SwiftLint version**:
   ```bash
   swiftlint version
   ```

2. **Get its default rule activation**:
   ```bash
   swiftlint rules --json > /tmp/swiftlint-rules.json
   ```
   The JSON includes each rule's `identifier`, `is_opt_in`, and severity.
   Default-on rules are `"is_opt_in": false`.

3. **Compare to our profile** at
   `sonar-swift-plugin/src/main/resources/io/sonarswift/plugin/profiles/SwiftLint_compat_profile_swift.json`.
   For each SwiftLint default-on rule, check whether we have a corresponding
   sonar-swift rule tagged `swiftlint:<rule_identifier>`. List:
   - **Missing** — SwiftLint has it, we don't have a counterpart rule
   - **Mismatched** — we have a counterpart but it's not in our profile
   - **Removed-upstream** — we have a rule in the profile that SwiftLint no
     longer activates by default

4. **Propose changes**:
   - For *Missing*: optionally invoke `add-rule` to create the missing rule.
     Cross-tag it `swiftlint:<identifier>` and add to *SwiftLint-compat*.
   - For *Mismatched*: add the rule key to the profile's `ruleKeys`.
   - For *Removed-upstream*: remove the key from our profile.

5. **Document the version we synced against** at the top of the file:
   ```
   // synced against SwiftLint 0.57.0 on 2026-06-03 by @username
   ```
   (since JSON doesn't support comments, we use a parallel
   `SwiftLint_compat_profile_swift.metadata.json` file or a comment in the
   plugin's CHANGELOG.)

6. **Bump CHANGELOG.md** with a "Synced SwiftLint-compat to SwiftLint X.Y.Z"
   entry.

## What you should NOT do

- Don't change the *Sonar way* profile to match SwiftLint defaults — those
  profiles serve different audiences.
- Don't activate stylistic SwiftLint rules in *Strict* that we explicitly
  decided against (e.g. opening-brace placement). Strict is "everything we
  ship that fires", not "everything anyone ever flagged."
