# SwiftSonarParser

The Swift-side companion binary spawned by the Java SonarQube plugin.

Reads parse requests from stdin (newline-delimited JSON) and writes
parse responses to stdout. Built on Apple's
[swift-syntax](https://github.com/swiftlang/swift-syntax).

## Build

```bash
swift build -c release
# Binary at .build/release/SwiftSonarParser
```

## Usage

```bash
# Serve mode (used by the Java plugin)
./SwiftSonarParser --serve

# One-shot mode (CLI debugging)
./SwiftSonarParser parse path/to/MyFile.swift | jq
```

## NDJSON protocol

### Request

```json
{
    "reqId": "uuid-here",
    "op": "parse",
    "path": "/absolute/path/to/File.swift",
    "source": "let x = 1  // optional — if omitted, parser reads from `path`"
}
```

### Response (success)

```json
{
    "reqId": "uuid-here",
    "ok": true,
    "data": {
        "kind": "SourceFile",
        "path": "/absolute/path/to/File.swift",
        "range": {"startLine": 1, "startCol": 1, "endLine": 1, "endCol": 10},
        "children": [
            {"kind": "ClassDecl", "name": "Foo", "modifiers": ["public"], "range": {...}},
            {"kind": "FuncDecl", "name": "bar", "paramNames": [], "isAsync": false, "range": {...}}
        ],
        "tokens": [
            {"kind": "KEYWORD", "text": "public", "line": 1, "col": 1, "endLine": 1, "endCol": 7}
        ]
    }
}
```

### Response (error)

```json
{
    "reqId": "uuid-here",
    "ok": false,
    "error": "human-readable reason"
}
```

### Other ops

- `ping` — health check; responds with `{"pong": true}`
- `shutdown` — exits the process cleanly

## Adding new AST node types

1. Add an `override func visit(...)` to `SonarVisitor.swift`
2. Emit a dict with a unique `kind` value
3. Mirror that on the Java side in `SwiftAst.java` with a matching `@JsonSubTypes.Type`
4. Add an integration test under `Tests/`
