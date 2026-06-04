# Swift Concurrency

Eight rules covering the most-stepped-on landmines in Swift Concurrency.

| Key   | Title                                                              | Type           | Severity |
| ----- | ------------------------------------------------------------------ | -------------- | -------- |
| S1900 | Reference types crossing concurrency boundaries should be Sendable | BUG            | Critical |
| S1901 | `@unchecked Sendable` should be reviewed                           | SECURITY_HOTSPOT | Minor  |
| S1902 | Mutating actor-isolated state must use `await`                     | BUG            | Blocker  |
| S1903 | UI APIs must be touched from a `@MainActor` context                | BUG            | Critical |
| S1904 | `Task { ... }` closures should not capture `self` strongly         | BUG            | Major    |
| S1905 | `Task.detached` should be used sparingly                           | CODE_SMELL     | Major    |
| S1906 | Calls to `async` functions inside an `async` body should be awaited| BUG            | Critical |
| S1907 | `static var` outside an `actor` is unsafe under concurrency        | BUG            | Critical |
| S1908 | `nonisolated(unsafe)` should be reviewed                           | SECURITY_HOTSPOT | Minor  |

All eight ship in **Sonar way** and **Strict** profiles (S1906 only in Strict —
needs AST-mode for low false-positive rates).

## Configuration

| Property                                            | Default | Effect                                                     |
| --------------------------------------------------- | ------- | ---------------------------------------------------------- |
| `sonar.swift.concurrency.enabled`                   | `true`  | Master switch for the rule pack.                           |
| `sonar.swift.concurrency.strictActorIsolation`      | `false` | Apply Swift 6 strict-mode-style analysis (broader scope).  |

## How it works

The token-based checks fire on syntactic patterns:

- **S1904** matches `Task { ... }` literals whose closure body references `self`
  and whose capture list does not include `[weak self]` or `[unowned self]`.
- **S1907** looks for `static var` declarations whose enclosing scope walks back
  to a non-`actor` declaration.
- **S1901 / S1908** flag `@unchecked Sendable` and `nonisolated(unsafe)` for
  human review (security hotspots).

The AST-mode checks (when the SwiftSyntax sidecar is enabled) extend the
analysis with attribute / isolation information emitted by the parser:

```json
{
  "kind": "FuncDecl",
  "name": "loadProfile",
  "attributes": ["@MainActor"],
  "isAsync": true,
  "actorIsolation": "MainActor"
}
```

This lets S1903 (UI off-main) and S1906 (missing await) avoid false positives
that token-mode can't always disambiguate.

## Suppressing findings

```swift
// sonar-swift:disable-next-line S1904
Task { self.refresh() }

// or for a block, per-rule, file-wide:
// sonar-swift:disable S1907 S1908
```

SwiftLint-style directives are also recognised where a mapping exists:

```swift
// swiftlint:disable:next force_unwrapping    // honored as S1001
let value = optional!
```

See [`SuppressionRegistry.java`](../sonar-swift-plugin/src/main/java/io/sonarswift/plugin/rules/SuppressionRegistry.java)
for the full set of recognised forms.
