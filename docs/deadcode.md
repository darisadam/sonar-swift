# Dead code detection

How we find code that's defined but never used.

## Two strategies

| Language    | Tool                     | Why                                                                                                                  |
| ----------- | ------------------------ | -------------------------------------------------------------------------------------------------------------------- |
| Swift       | **Periphery** (external) | Industry-standard, accurate cross-module dead-code analysis; we wrap its JSON output                                  |
| Objective-C | **Native check**         | Periphery doesn't cover ObjC well; we do best-effort symbol-graph analysis ourselves                                  |

## Swift via Periphery

[Periphery](https://github.com/peripheryapp/periphery) is an Apple-platform
dead-code analyzer. It resolves symbols across the build graph (via SourceKit)
and reports unused declarations.

### Setup

```bash
brew install periphery
```

### Generating a report

Periphery needs the project to build. Once:

```bash
periphery scan \
    --workspace MyApp.xcworkspace \
    --schemes MyApp \
    --targets MyApp,MyAppKit \
    --format json \
    --output build/periphery.json
```

### Feeding to the scanner

```bash
sonar-scanner -Dsonar.swift.periphery.reportPath=build/periphery.json
```

The `PerigerySensor` reads the report and raises issues against rules in the
`S3100-S3199` range:

- S3100 **unused class**
- S3101 **unused struct / enum**
- S3102 **unused protocol**
- S3103 **unused function / method**
- S3104 **unused initializer**
- S3105 **unused property**
- S3106 **unused parameter**
- S3107 **redundant `public` access modifier** (Periphery's "assignOnlyProperty")
- S3108 **unused type alias**
- S3109 **redundant `protocol` conformance**

Severity defaults to `MINOR`. Bump per-rule in your profile if dead code is a
deal-breaker for your team.

### Optional: let the scanner run Periphery

Set `sonar.swift.periphery.autorun=true` and the plugin will invoke
`periphery scan ...` itself before reading the report. Requires Periphery on
`$PATH` and a buildable workspace. The scan adds 30s-2min depending on
project size, so we default this off and recommend running Periphery as a
separate CI step that caches its result.

### Tuning

- `sonar.swift.periphery.retainPublic=true|false` — Periphery's option to
  treat all `public` symbols as roots. Default `false` for app projects,
  `true` for libraries.
- `sonar.swift.periphery.retainObjcAccessible=true` — keep ObjC-accessible
  symbols alive. Recommended for mixed projects.
- Suppress specific symbols with Periphery's
  [comment markers](https://github.com/peripheryapp/periphery#known-issues)
  (`// periphery:ignore`).

## Objective-C — native check

For ObjC we don't have Periphery. We do a best-effort analysis ourselves:

### What we find

- Unused `@interface` / `@implementation` (defined but never imported nor
  forward-declared in any other file)
- Unused selectors (declared methods never called via direct send or
  `@selector(...)`)
- Unused C functions in `.m` files (static functions with no in-file call)
- Unused `static` variables
- Unused `#define` macros

### What we miss

- Selectors invoked dynamically via `performSelector:` / `NSSelectorFromString`
  / runtime introspection — these show as unused (false positives).
- Symbols accessed only via KVC / KVO — false positive.
- Symbols exported to Swift via `@objc` and used from Swift — we cross-check
  against the Swift AST to suppress this, but we only see what's in the same
  Sonar project.

### Suppressing false positives

Use a structured comment we recognize:

```objc
// sonar-dead-code:keep — invoked via performSelector from FeatureFlagBridge.m
- (void)debugDumpState {
    ...
}
```

### Implementation pointers

- Swift adapter: `external/PerigerySensor.java`
- ObjC native check: `rules/objc/UnusedSelectorCheck.java`,
  `rules/objc/UnusedFunctionCheck.java`, `rules/objc/UnusedMacroCheck.java`
- Cross-language Swift↔ObjC bridge:
  `rules/CrossLanguageReferenceTracker.java`
