# Architecture

```
                                    ┌──────────────────────────────────┐
                                    │       SonarQube Server (JVM)     │
                                    │   - stores issues, measures      │
                                    │   - exposes UI, web API          │
                                    │   - quality gates                │
                                    └──────────────────────────────────┘
                                                  ▲
                                                  │ HTTP (push analysis report)
                                                  │
┌─────────────────────────────────────────────────┴───────────────────────────────────────┐
│                               SonarScanner CLI / Gradle / Maven                          │
│                                                                                          │
│   ┌──────────────────────────── sonar-swift-plugin.jar ─────────────────────────────┐    │
│   │                                                                                  │    │
│   │   SwiftPlugin                                                                    │    │
│   │     ├── Language: Swift     (.swift)                                             │    │
│   │     ├── Language: ObjC      (.h .m .mm)                                          │    │
│   │     ├── QualityProfiles     (Sonar way / Strict / SwiftLint-compat)              │    │
│   │     ├── RulesDefinition     (HTML descriptions in resources/)                    │    │
│   │     │                                                                            │    │
│   │     ├── Sensors                                                                  │    │
│   │     │     ├── SwiftSensor          ─── native AST checks (drives parser proc)   │    │
│   │     │     ├── ObjectiveCSensor     ─── native AST checks (drives clang)         │    │
│   │     │     ├── SwiftLintSensor      ─── parses SwiftLint JSON                    │    │
│   │     │     ├── OCLintSensor         ─── parses OCLint PMD-XML                    │    │
│   │     │     ├── PerigerySensor       ─── parses Periphery JSON (dead code)        │    │
│   │     │     ├── CoverageSensor       ─── xccov / Slather / Cobertura / generic    │    │
│   │     │     └── CpdSensor            ─── emits tokens to SonarQube CPD            │    │
│   │     │                                                                            │    │
│   │     ├── Parser Bridge                                                            │    │
│   │     │     ├── SwiftSyntaxClient    ─── owns subprocess, NDJSON over stdio       │    │
│   │     │     └── ClangClient          ─── shells out to clang -Xclang -ast-dump    │    │
│   │     │                                                                            │    │
│   │     ├── Metrics                                                                  │    │
│   │     │     ├── Lines / NCLOC / comments                                           │    │
│   │     │     ├── Cyclomatic complexity                                              │    │
│   │     │     ├── Cognitive complexity                                               │    │
│   │     │     └── Structural metrics (#functions, #classes, #statements)             │    │
│   │     │                                                                            │    │
│   │     └── Coverage Parsers                                                         │    │
│   │           ├── XccovJsonParser                                                    │    │
│   │           ├── SlatherXmlParser                                                   │    │
│   │           ├── CoberturaXmlParser                                                 │    │
│   │           └── GenericSonarXmlParser                                              │    │
│   └──────────────────────────────────────────────────────────────────────────────────┘    │
│                  │                              │                                          │
│                  ▼                              ▼                                          │
│       spawn ───────── stdin ─────►  ╔════════════════════╗     spawn ─────────►  clang     │
│                                     ║ SwiftSonarParser   ║                                 │
│                                     ║ (Swift binary)     ║                                 │
│                                     ║   uses swift-      ║                                 │
│                                     ║   syntax 600+      ║                                 │
│                                     ╚════════════════════╝                                 │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

## Process model

Everything runs as part of one `sonar-scanner` invocation. The JVM hosts the
plugin; the plugin spawns two helper processes on demand:

- **`SwiftSonarParser`** (Swift) — long-lived. The Java client writes one
  request per line as `{"reqId":N,"path":"…","source":"…"}\n` and reads one
  response per line as `{"reqId":N,"ast":…,"tokens":…,"diagnostics":…}\n`.
  Multiple files in flight via `reqId` correlation. Restarted on crash.
- **`clang`** (system-provided) — short-lived per file. We capture stdout +
  stderr and parse the AST dump.

Both helpers are *optional*: if `sonar.swift.parser.path` / `sonar.objc.clang.path`
isn't set we fall back to **external-tool-only mode** (SwiftLint + OCLint +
Periphery), which gives 70-80% of the value with zero native dependencies.
This is the "low-friction" install path — useful for first-time users.

## Data flow per file

```
File on disk
   │
   ▼
SonarScanner discovers it
   │
   ▼
SwiftSensor.execute(InputFile)
   │
   ├─► SwiftSyntaxClient.parse(path)
   │     │  (sends JSON, awaits response)
   │     ▼
   │   SwiftAst + Tokens + Diagnostics
   │
   ├─► For each check in active profile:
   │     check.visit(ast) → List<Issue>
   │     context.newIssue().forRule(...).at(...).save()
   │
   ├─► LinesCalculator(tokens)        → saveMeasure(ncloc, …)
   ├─► ComplexityCalculator(ast)      → saveMeasure(complexity, …)
   └─► CpdSensor(tokens)              → context.newCpdTokens()…save()
```

External-tool sensors (SwiftLint, OCLint, Periphery) work the other direction:
they read a pre-existing report file, map each finding to a rule key, and emit
an issue. They never spawn the tool themselves unless explicitly asked
(`sonar.swift.swiftlint.autorun=true`).

## Module layout

```
sonar-swift/
├── pom.xml                     parent POM
├── sonar-swift-plugin/         the JAR loaded by SonarQube
│   └── src/main/java/io/sonarswift/plugin/
│       ├── SwiftPlugin.java
│       ├── language/           {Swift, ObjectiveC}
│       ├── profile/            built-in quality profiles
│       ├── rules/              checks + RulesDefinition
│       │   ├── swift/          one class per Swift rule
│       │   └── objc/           one class per ObjC rule
│       ├── parser/             Swift/Clang AST clients + AST POJOs
│       ├── sensors/            Sensor implementations
│       ├── metrics/            metric calculators
│       ├── coverage/           coverage parsers
│       ├── issues/             issue + quick-fix helpers
│       └── external/           SwiftLint / OCLint / Periphery adapters
├── sonar-swift-parser/         Swift binary using SwiftSyntax
│   ├── Package.swift
│   └── Sources/SwiftSonarParser/
│       ├── main.swift          NDJSON loop
│       ├── Parser.swift        SwiftSyntax → our compact AST JSON
│       ├── Tokens.swift        tokens for CPD/LOC
│       └── Diagnostics.swift   diagnostics passthrough
├── sonar-swift-cli/            tiny CLI: scan + report subcommands
├── integration-tests/          real-world projects analyzed end-to-end
├── examples/                   sample iOS app with deliberate findings
├── docker/                     dev container + docker-compose for local SQ
├── .github/workflows/          CI
├── docs/                       this directory
└── scripts/                    setup-dev / package-plugin / run-local-sonar
```

## Tech stack & rationale

| Layer                | Choice                                | Why                                                                                       |
| -------------------- | ------------------------------------- | ----------------------------------------------------------------------------------------- |
| Plugin language      | **Java 17**                           | SonarQube plugin API is Java; 17 is the floor for current LTS                              |
| Build                | **Maven 3.9**                         | Sonar's own plugins use Maven; their `sonar-packaging-maven-plugin` is the canonical path |
| Plugin API           | `sonar-plugin-api` 10.x               | Targets SonarQube 10.x LTS and 25.x                                                       |
| Swift AST            | **swift-syntax 600+**                 | Apple-official, exact, supports all Swift language features                                |
| ObjC AST             | **clang -Xclang -ast-dump** (v1) / **libclang via JavaCPP** (v2) | Subprocess first for portability; libclang later for speed     |
| External-tool ingest | SwiftLint JSON, OCLint PMD-XML, Periphery JSON | Stable, documented formats                                                       |
| Coverage             | xccov JSON, Slather XML, Cobertura, Sonar generic | Cover every Apple-stack CI setup                                              |
| CI                   | GitHub Actions                        | Matrix builds across SonarQube versions + Swift versions                                  |
| Distribution         | GitHub Releases (`.jar`) + Sonar Marketplace later | Don't gate releases on marketplace review                                    |

ADR-level decisions: see [`docs/adr/`](adr/).
