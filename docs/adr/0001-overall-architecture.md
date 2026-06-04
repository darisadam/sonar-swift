# ADR-0001: Java SonarQube plugin + sidecar Swift parser process

## Status

Accepted — 2026-06-03.

## Context

A SonarQube plugin must be a `.jar` loaded by the SonarQube server. We need
to analyze Swift and Objective-C source code, but the JVM has no native Swift
parser, and porting Swift's parser to Java is a non-starter (months of work,
ongoing maintenance burden as Swift evolves).

## Decision

Ship the plugin as a standard Maven-built `.jar` (Java 17, SonarQube
`sonar-plugin-api` 10.x). For Swift analysis, bundle a separately-built Swift
binary (`SwiftSonarParser`) using `swift-syntax`. The plugin spawns this
binary as a long-lived subprocess and communicates over NDJSON on stdin/stdout.

For Objective-C, shell out to the system `clang -Xclang -ast-dump=json` per
file. This is short-lived and stateless.

## Consequences

- **+** No Java port of Swift's parser. We rely on Apple's official
  `swift-syntax`, which always reflects current Swift.
- **+** We can iterate on the parser independently of the plugin.
- **+** Loose coupling. If we later add a libclang-via-JavaCPP backend for
  ObjC, the same interface fits.
- **−** Two artifacts to build and version together. We pin the parser
  version in the plugin and unit-test the protocol on every build.
- **−** Process-spawn overhead. We amortize via subprocess reuse +
  request batching.
- **−** Distributing a native binary inside a JAR is unusual. We extract on
  startup to a temp dir, mark executable, exec. Standard pattern.
- **−** Linux SonarQube servers need the Linux build of `SwiftSonarParser`.
  We ship a fat JAR with macOS-arm64, macOS-x86_64, linux-arm64, linux-x86_64
  binaries inside and pick at runtime.

## Alternatives considered

1. **Pure-Java parser ported from swift-syntax**: rejected — re-implementation
   cost + churn risk.
2. **SourceKit-LSP only**: too high-latency for batch analysis; designed for
   editor interaction.
3. **External-tool-only (SwiftLint + OCLint + Periphery)**: shipping. We
   support this as a fallback mode (`sonar.swift.parser.enabled=false`) but
   it cannot compute cognitive complexity / structural metrics, so it's not
   the default.
4. **Embedded native parser via JNI**: feasible but introduces classloader
   and CPU-architecture nightmares. Subprocess is simpler.
