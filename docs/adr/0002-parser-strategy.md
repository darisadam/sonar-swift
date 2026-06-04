# ADR-0002: Swift via swift-syntax; Objective-C via clang AST dump

## Status

Accepted — 2026-06-03. To be revisited when libclang-via-JavaCPP work begins
(post-1.0).

## Context

We need a syntactic + lightly-semantic AST for both languages. We do not need
full type resolution: those checks live in semantic-aware tools (SwiftLint
analyzer mode, Periphery) that we already integrate with.

## Decision

**Swift** — use [`swift-syntax`](https://github.com/swiftlang/swift-syntax)
(formerly Apple's SwiftSyntax). It is:

- Apple-maintained, tracks language evolution
- Pure Swift, no Xcode dependency at runtime
- Produces an exact lossless syntax tree
- Stable Swift Package API

We pin `swift-syntax` 600.0.x in `Package.swift` and update minor versions as
needed.

**Objective-C** — shell out to `clang -Xclang -ast-dump=json -fsyntax-only`,
parse the resulting JSON. Rationale:

- Apple's clang is the reference ObjC compiler
- AST dump is structured and stable enough for our purposes
- No binding library required
- Works with `xcrun --find clang` which finds the right toolchain per machine

## Consequences

### Swift

- **+** Always current with Swift language. Property wrappers, macros,
  result builders, parameter packs — all handled.
- **+** Tree is lossless so source-precise issue locations are trivial.
- **−** swift-syntax major-version bumps may break our parser. We pin and
  test.

### Objective-C

- **+** No native bindings to maintain.
- **+** Compatible with any clang on `$PATH`.
- **−** AST dump is verbose. A medium ObjC file can produce 10 MB of JSON.
  We stream-parse with Jackson, never materialize the whole tree.
- **−** clang's AST JSON schema is not formally stable. We pin a minimum
  clang version (15) and have golden-file tests that fail loudly if shape
  changes.
- **−** Slow: ~50-200ms per file just for clang to dump. Parallelize across
  files in the sensor.

## What this is NOT

We deliberately don't pull in:

- **SourceKit / SourceKit-LSP**: powerful but designed for interactive editing.
  Spinning it up per-scan is heavy; using it as a daemon adds a third process
  to coordinate. Defer to post-1.0 when we add cross-file type resolution.
- **libclang** (the C library): would give us semantic info for ObjC, but
  needs JNI/JavaCPP bindings + native libs distributed per-platform. We may
  revisit for performance in a 1.1 ADR.

## Alternatives considered

- **Tree-sitter for both languages**: parser exists, fast, no native deps in
  Java (via tree-sitter-java bindings). Trade-off: tree-sitter is good
  enough for syntax highlighting but its Swift grammar is community-maintained
  and lags official Swift; same for ObjC. Rejected.
- **ANTLR grammars**: maintenance burden of our own grammars; rejected.
- **Custom hand-written parser**: rejected without further discussion.
