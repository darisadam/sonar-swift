---
name: add-rule
description: Scaffold a new SonarQube rule for Swift or Objective-C end-to-end — check class, JSON metadata, HTML description, test fixtures, registry updates, and quality-profile updates. Use when the user asks to add a rule, implement a check, or flag a code pattern.
---

# add-rule

Adds a new rule to sonar-swift in one consistent change. Always delegates the
heavy lifting to the `sonar-rule-author` agent so the work is uniform with
existing rules.

## When to invoke

- "Add a rule for X"
- "Implement a check that flags Y"
- "I want to detect Z in our Swift code"
- "Create a SonarQube rule for …"

## Steps you take

1. **Clarify** what should fire and what should NOT fire. Ask for:
   - One **Noncompliant** code snippet (must fire)
   - One **Compliant** snippet (must not fire)
   - Whether this is a **bug**, **code smell**, **vulnerability**, or
     **security hotspot**
   - What language: Swift, Objective-C, or both
   - If the user mentions an existing tool's rule (SwiftLint, OCLint), capture
     the tool's rule name so we can cross-tag.

2. **Pick the next free rule key** by running:
   ```bash
   grep -rh '"sqKey"' sonar-swift-plugin/src/main/resources/io/sonarswift/plugin/rules/swift/*.json \
       | grep -oE 'S[0-9]+' | sort -V | tail -1
   ```
   then add 1 in the appropriate range:
   - S1000-S1499 Swift code smells
   - S1500-S1799 Swift bugs
   - S1800-S1999 Swift vuln + hotspots
   - S2000-S2499 ObjC code smells
   - S2500-S2799 ObjC bugs
   - S2800-S2999 ObjC vuln + hotspots
   - S3000+ cross-language

3. **Invoke the `sonar-rule-author` agent** with the gathered info. The agent
   produces the four artifacts + the registry updates.

4. **Verify the build** by running `mvn -q -ntp -DskipTests package` after
   the agent finishes. If it fails, the agent forgot to update a registry —
   point them at the failure and re-invoke.

5. **Report to the user**:
   - The rule key
   - File paths touched
   - The quality profiles it was added to (Sonar way / Strict / SwiftLint-compat)
   - The suppression syntax: `// sonar-disable-next-line:SXXXX`
   - Any caveats (e.g. "currently token-based — may produce false positives
     on heavily-formatted code")

## Anti-patterns

- Don't write the four files yourself piecemeal — `sonar-rule-author` exists
  precisely to keep them coherent.
- Don't add a rule whose Noncompliant example the user can't articulate.
- Don't add a rule that 100% overlaps an existing SwiftLint/OCLint rule
  unless we have a clear refinement.
