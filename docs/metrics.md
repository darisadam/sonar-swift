# Metrics

Every metric SonarQube exposes for Swift / Objective-C through this plugin,
plus the algorithm we use to compute it.

## Size

| Key                     | What it counts                                                | How                                                                                |
| ----------------------- | ------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| `lines`                 | Physical lines including blank, comments, code                | line count of source                                                               |
| `ncloc`                 | Lines containing at least one code token                      | tokenizer classifies each line; line counted if any token is `CODE`                |
| `comment_lines`         | Lines containing at least one comment token, no code          | tokenizer; line counted if `COMMENT` and no `CODE`                                 |
| `comment_lines_density` | `comment_lines / (ncloc + comment_lines) * 100`               | derived                                                                            |
| `files`                 | Files analyzed                                                | one per `InputFile`                                                                |
| `directories`           | Directories containing analyzed files                         | aggregated                                                                         |

**Edge cases**:
- doc comments (`///`, `/** */`) count as `comment_lines`.
- A line like `let x = 1 // explain` is `ncloc=1, comment_lines=0` (code present
  dominates).
- `#if DEBUG ... #endif` blocks: lines inside `#if` count normally.
- Strings spanning multiple lines (multi-line string literals): only lines with
  the literal *opener* / *closer* count as `ncloc`; pure-content lines are
  `lines` only. (This is the same as SonarJava's rule for Java text blocks.)

## Structural

| Key             | What it counts                                          |
| --------------- | ------------------------------------------------------- |
| `classes`       | `class`, `struct`, `enum`, `actor`, `protocol`, ObjC `@interface` |
| `functions`     | `func`, `init`, ObjC `-`/`+` methods, computed-property accessors |
| `statements`    | Top-level statements + statements inside function bodies |

We count `protocol` declarations as classes for parity with SonarJava's count
of interfaces. `extension` blocks aren't counted as new classes; their members
roll up to the file.

## Complexity

| Key                    | What it measures                                                              |
| ---------------------- | ----------------------------------------------------------------------------- |
| `complexity`           | Cyclomatic complexity, summed per file                                        |
| `function_complexity`  | Average cyclomatic complexity per function                                    |
| `cognitive_complexity` | Cognitive complexity, summed per file (Sonar's specification)                 |

### Cyclomatic — what increments

- `if`, `else if`
- `for`, `while`, `repeat-while`, `for-in`
- `case` in a `switch` (each case)
- `catch` clause
- `?:` ternary
- `&&`, `||` short-circuit operators
- `guard` (counts the negative branch — same as `if !`)

`else` (no condition) does NOT increment.
`default` in a `switch` does NOT increment.

Function baseline = 1. Property accessors with non-trivial bodies count as
their own functions.

### Cognitive — what increments

We implement
[Cognitive Complexity (Sonar 2017)](https://www.sonarsource.com/docs/CognitiveComplexity.pdf)
faithfully:

- `if`, `else if`, `else`, ternary, `for`, `while`, `repeat`, `case`, `catch`:
  +1
- Nesting adds extra: an `if` at depth N adds `N` instead of 1
- `&&`, `||`: +1 for the *sequence* of consecutive same operators, not per
  occurrence
- Recursion: +1
- `break label`, `continue label`, `goto`: +1
- Macros that introduce control flow (in Swift: `#if`, `#elseif`, `#else`,
  `#endif`): +1

## Issues / debt

| Key                            | What it measures                                                         |
| ------------------------------ | ------------------------------------------------------------------------ |
| `violations`                   | Total issues raised                                                      |
| `bugs`                         | `BUG`-type issues                                                        |
| `code_smells`                  | `CODE_SMELL`-type issues                                                 |
| `vulnerabilities`              | `VULNERABILITY`-type issues                                              |
| `security_hotspots`            | `SECURITY_HOTSPOT`-type findings                                         |
| `sqale_index`                  | Sum of `remediation_cost` across all open issues                          |
| `reliability_remediation_effort` | Sum of remediation cost for `BUG` issues                                |
| `security_remediation_effort`  | Sum of remediation cost for `VULNERABILITY` issues                       |

Remediation cost is declared per-rule in the JSON metadata (e.g. `5min`,
`30min`, `1h`). SonarQube does the rest.

## Duplications

| Key                       | What it measures                                          |
| ------------------------- | --------------------------------------------------------- |
| `duplicated_lines`        | Lines part of any duplicated block                        |
| `duplicated_lines_density`| `duplicated_lines / lines * 100`                          |
| `duplicated_blocks`       | Number of duplicated blocks                               |
| `duplicated_files`        | Number of files containing at least one duplicated block  |

We hand SonarQube the tokens; SonarQube runs CPD. Per-language token filtering:

- **Swift**: skip `import` statements, `// MARK:` lines, `@available` attributes
- **Objective-C**: skip `#import`, `#pragma`, `@class` forward decls

Default detection thresholds (overridable):
- min tokens: 100
- min lines: 10

## Coverage

| Key                       | What it measures                                          |
| ------------------------- | --------------------------------------------------------- |
| `coverage`                | Combined line + branch                                    |
| `line_coverage`           | Covered statement lines / executable lines                |
| `branch_coverage`         | Covered branches / total branches                         |
| `uncovered_lines`         | Lines not covered                                         |
| `uncovered_conditions`    | Branches not covered                                      |
| `new_coverage`            | Coverage on new code (used by quality gate)                |
| `tests`                   | Total tests run                                           |
| `test_failures`           | Tests that failed                                         |
| `test_errors`             | Tests that errored                                        |
| `skipped_tests`           | Tests that were skipped                                   |
| `test_execution_time`     | Total test execution time in ms                           |

Sourced from one of: `xccov`, Slather, Cobertura, generic SonarQube XML.
See [`coverage.md`](coverage.md).

## Documentation

| Key                  | What it measures                              |
| -------------------- | --------------------------------------------- |
| `public_api`         | Count of `public` / `open` declarations       |
| `public_documented_api_density` | Fraction documented with `///` or `/** */` |
| `public_undocumented_api`        | Count of undocumented public symbols |

## Reliability / security ratings

SonarQube computes these from the issues we raise. We don't push them directly;
we make sure the issue types and severities are accurate so the A-E ratings
fall where they should.

## Implementation pointers

- `LinesCalculator` — `parser/SwiftTokens.java`, `parser/ClangTokens.java`
- `ComplexityCalculator` — `metrics/CyclomaticComplexity.java`
- `CognitiveComplexityCalculator` — `metrics/CognitiveComplexity.java`
- `StructuralMetrics` — `metrics/StructuralMetrics.java`
- `CpdSensor` — emits via `context.newCpdTokens()`
