# Duplication detection (CPD)

Token-based copy-paste detection for Swift and Objective-C.

## How it works

The plugin emits a normalized token stream per file to SonarQube's built-in
CPD engine. SonarQube finds matching sub-sequences across files and reports:

- `duplicated_lines` — lines belonging to *any* matching block
- `duplicated_blocks` — number of distinct matching blocks
- `duplicated_files` — files containing at least one block
- `duplicated_lines_density` — duplicated / total lines, ×100

## What's a token, for our purposes

We don't hand SonarQube the raw lexer tokens — we *normalize* first, so
trivially-different copies are still detected:

| Source token             | Normalized as |
| ------------------------ | ------------- |
| Identifier (variable)    | `IDENT`       |
| String literal           | `STR`         |
| Integer literal          | `INT`         |
| Float literal            | `FLOAT`       |
| Bool literal             | `BOOL`        |
| `nil` / `NULL`           | `NIL`         |
| Keyword (`if`, `func`, …)| kept as-is    |
| Operator                 | kept as-is    |
| Punctuation              | kept as-is    |

This means renaming variables does not hide duplication. Restructuring control
flow does.

## What's filtered out

Per-language exclusions to avoid false positives:

**Swift**:
- `import` statements
- `// MARK:`, `// MARK: -` section markers
- `@available(...)` attributes
- Top-level Doc comments (`/// ...`)

**Objective-C**:
- `#import`, `#include`
- `@class` forward declarations
- `#pragma mark`
- Header-file declarations (`.h` files contribute tokens but with higher
  thresholds — see below)

## Thresholds

SonarQube CPD has two thresholds; both configurable per project:

| Property                                  | Default | What it controls                                  |
| ----------------------------------------- | :-----: | ------------------------------------------------- |
| `sonar.cpd.swift.minimumTokens`           | `100`   | Min token count for a match                       |
| `sonar.cpd.swift.minimumLines`            | `10`    | Min line count for a match                        |
| `sonar.cpd.objc.minimumTokens`            | `120`   | ObjC is more verbose — raised default             |
| `sonar.cpd.objc.minimumLines`             | `10`    | Same                                              |

Header-only matches (in `.h` files) require an additional `+50%` on the token
threshold by default to avoid common-declaration noise.

## Exclusions

Standard SonarQube exclusion props work:

```properties
sonar.cpd.exclusions=**/Generated/**/*.swift,**/*+Codable.swift
```

Per-file inline:

```swift
// sonar-cpd:off
let lookup: [Int: String] = [
    0: "zero",
    1: "one",
    /* … 100 lines of data … */
]
// sonar-cpd:on
```

## Tuning advice

- If your "duplications" metric is dominated by `Codable` boilerplate, exclude
  generated files (`**/Generated/**`) and Codable extensions.
- Network-model directories (lots of struct fields) trip CPD often. Bump
  `minimumTokens` to `150` for `**/Networking/Models/**` via per-module config.
- Tests deliberately repeat structure (arrange/act/assert). Exclude
  `**/*Tests.swift` / `**/*Test.swift` if your team doesn't dedupe test setup.

## Implementation pointers

- Token emission: `parser/SwiftTokens.java#emitForCpd(InputFile, NewCpdTokens)`
- Normalization: `parser/CpdTokenNormalizer.java`
- Filtering: `parser/CpdTokenFilter.java`
