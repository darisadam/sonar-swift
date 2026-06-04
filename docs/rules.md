# Rules

How rules are organized, declared, and what we're starting with.

## Anatomy of a rule

Every rule is **four artifacts** in lock-step:

1. **Java check class** — `io.sonarswift.plugin.rules.swift.ForceUnwrapCheck`
   extending `SwiftCheck`. Walks the AST and calls `reportIssue(...)`.
2. **Rule metadata JSON** — `resources/io/sonarswift/plugin/rules/swift/S1001.json`
   declares key, name, type (BUG/CODE_SMELL/VULNERABILITY/SECURITY_HOTSPOT),
   severity, SQALE remediation cost, tags.
3. **HTML description** — `resources/io/sonarswift/plugin/rules/swift/S1001.html`
   shown in the SonarQube UI. Sections: *Why is this an issue?*, *Noncompliant
   example*, *Compliant solution*, *See also*.
4. **Test fixture** — `src/test/resources/fixtures/swift/S1001/` with
   `Noncompliant.swift` and `Compliant.swift`. The check's unit test runs
   itself against the fixture and asserts which lines flagged.

The scaffolder skill `.claude/skills/add-rule` creates all four in one go.

## Rule numbering scheme

| Range            | Category           |
| ---------------- | ------------------ |
| `S1000-S1499`    | Swift code smells  |
| `S1500-S1799`    | Swift bugs         |
| `S1800-S1999`    | Swift vulnerabilities + security hotspots |
| `S2000-S2499`    | Objective-C code smells |
| `S2500-S2799`    | Objective-C bugs   |
| `S2800-S2999`    | Objective-C vulnerabilities + security hotspots |
| `S3000+`         | Cross-language (e.g. magic numbers, file length) |

Where a SwiftLint / OCLint rule has a 1:1 equivalent we keep their key under
an alias (`tags: [swiftlint:force_unwrapping]`) for grep-ability.

## Initial catalog (target for GA)

### Swift — Code smells (~70)

- S1001  **force-unwrap of optional**                          — `foo!`
- S1002  **implicitly unwrapped optional declaration**          — `var x: Foo!`
- S1003  **force-cast**                                          — `x as!`
- S1004  **force-try**                                           — `try!`
- S1005  **print(...) left in code**
- S1006  **NSLog(...) left in code**
- S1007  **TODO / FIXME / HACK comment**
- S1008  **function too long** (default > 40 lines)
- S1009  **file too long** (default > 400 lines)
- S1010  **type too long** (default > 250 lines)
- S1011  **type with too many properties** (default > 20)
- S1012  **function with too many parameters** (default > 5)
- S1013  **deeply nested code** (default > 3 levels)
- S1014  **magic number**
- S1015  **identifier name too short / too long**
- S1016  **type name not UpperCamelCase**
- S1017  **function name not lowerCamelCase**
- S1018  **constant not UpperCamelCase or lowerCamelCase**
- S1019  **redundant `return` in single-expression closure / function**
- S1020  **redundant `self.` access**
- S1021  **redundant type annotation**
- S1022  **trailing whitespace**
- S1023  **missing trailing newline**
- S1024  **mixed tabs and spaces**
- S1025  **empty type body**
- S1026  **empty closure body**
- S1027  **always-true / always-false condition**
- S1028  **`if let x = x { ... }` shadowing — prefer shorthand**
- S1029  **`switch` with single case — prefer `if case`**
- S1030  **string interpolation of single variable**
- S1031  **redundant string-concatenation operator chain**
- S1032  **enum case not lowerCamelCase**
- S1033  **`Array<Foo>` vs `[Foo]` mixed usage**
- S1034  **`Dictionary<Foo, Bar>` vs `[Foo: Bar]` mixed usage**
- S1035  **public symbol missing doc comment**
- S1036  **comment line preceding `//swiftlint:disable`**
- S1037  **vertical-whitespace excess** (> 2 blank lines)
- S1038  **opening-brace on next line**
- S1039  **closing-brace not at column 0 of its statement**
- S1040  **redundant `internal` keyword**
- S1041  **redundant `public` on extension members**
- S1042  **`fileprivate` used where `private` suffices**
- S1043  **`open` used where `public` suffices**
- S1044  **identifier name doesn't follow protocol-conformance pattern**
- S1045  **closure body too long**
- S1046  **function signature on one line (over column limit)**
- S1047  **`as?` followed by force-unwrap — collapse to `as!` or proper handling**
- S1048  **chained optional-chain followed by force-unwrap**
- S1049  **redundant `else` after `return` / `throw` / `continue`**
- S1050  **`switch` exhaustive without `default` — explicit `@unknown default`**
- S1051  **complex closure should be extracted**
- S1052  **`guard` body with side effects beyond control-flow**
- S1053  **redundant initializer call (`Foo.init(...)` — prefer `Foo(...)`)**
- S1054  **redundant parentheses around `if` / `while` condition**
- S1055  **redundant `get` in read-only computed property**
- S1056  **redundant `var` where `let` suffices (immutability)**
- S1057  **typealias `T = (Int) -> Void` should be named**
- S1058  **`Any` / `AnyObject` usage in API**
- S1059  **opaque-return-type vs `some` mixed usage**
- S1060  **`@available` missing version where required**
- S1061  **`@MainActor` violated by call site**
- S1062  **`async`-function called without `await` (when detectable)**
- S1063  **`Sendable` violation (closures captured across actors)**
- S1064  **dispatch-queue created on every call (move to property)**
- S1065  **`weak self` capture missing in escaping closure**
- S1066  **`unowned self` capture where `weak self` is safer**
- S1067  **observation of `NotificationCenter` without removal**
- S1068  **KVO without `removeObserver` paired**
- S1069  **`Combine` cancellable stored without `Set<AnyCancellable>`**
- S1070  **`Task { }` fire-and-forget on `@MainActor` without explicit isolation**

### Swift — Bugs (~30)

- S1500  **force-unwrap on chained call result**
- S1501  **comparing `Double` / `Float` with `==`**
- S1502  **integer-overflow-prone arithmetic**
- S1503  **`Array.first` followed by `[0]` indexing**
- S1504  **`switch` over `Bool` instead of `if`**
- S1505  **assignment in `if` / `while` condition**
- S1506  **unreachable code after `return` / `throw` / `fatalError`**
- S1507  **`fatalError` in non-test, non-debug code path**
- S1508  **division by zero possible**
- S1509  **infinite loop without break condition**
- S1510  **`Bool.toggle()` followed by re-reading the value in same expression**
- S1511  **mutating non-mutating struct method through `var` rebind**
- S1512  **`weak` reference to value type (warns)**
- S1513  **`@objc dynamic` on a static dispatch site**
- S1514  **`DispatchQueue.main.sync` from main thread**
- S1515  **leaking `self` from `init` via async dispatch**
- S1516  **`URLSession.shared.dataTask` started without `.resume()`**
- S1517  **timer scheduled without invalidation**
- S1518  **`Notification` payload key typo (vs declared name constant)**
- S1519  **`UIView` animation block mutating layer outside `layoutSubviews`**
- S1520  **`assert` used for release-mode validation**
- S1521  **`precondition` used for unchecked-builds validation**
- S1522  **`reduce` with `+` on `String` (use `joined`)**
- S1523  **`Set` membership check inside `Array.contains` loop**
- S1524  **`==` comparison between `URL` instances ignoring normalization**
- S1525  **`Date()` used for ordering across timezones**
- S1526  **`Decimal` ↔ `Double` round-trip precision loss**
- S1527  **`NSNumber` boolean ↔ integer ambiguity**
- S1528  **completion handler called multiple times in same code path**
- S1529  **completion handler never called on some code path**

### Swift — Vulnerabilities + Security Hotspots (~25)

- S1800  **hardcoded API key / token**                          [VULN]
- S1801  **hardcoded credential (entropy + identifier-name heuristic)** [VULN]
- S1802  **`http://` URL where `https://` is expected**         [VULN]
- S1803  **`NSAllowsArbitraryLoads = true` in Info.plist**      [VULN]
- S1804  **weak crypto: MD5**                                    [VULN]
- S1805  **weak crypto: SHA1 for non-checksum use**             [VULN]
- S1806  **weak crypto: ECB mode**                               [VULN]
- S1807  **`arc4random` used as CSPRNG**                         [VULN]
- S1808  **`UserDefaults` used for sensitive data**             [VULN]
- S1809  **`Keychain` with `kSecAttrAccessibleAlways`**          [VULN]
- S1810  **deeplink handler without source validation**         [VULN]
- S1811  **`UIWebView` (deprecated, vulnerable)**                [VULN]
- S1812  **`WKWebView` with `javaScriptEnabled = true` on untrusted content** [HOTSPOT]
- S1813  **file path constructed by string concatenation**      [HOTSPOT]
- S1814  **`String(contentsOf:)` over network URL**              [HOTSPOT]
- S1815  **clipboard read of sensitive UI text**                 [HOTSPOT]
- S1816  **`NSAppleScript` execution**                           [HOTSPOT]
- S1817  **`Process` / `NSTask` launched with shell**           [HOTSPOT]
- S1818  **`SSL pinning` not configured for sensitive host**    [HOTSPOT]
- S1819  **`URLSession` with `serverTrust` always-accept**       [VULN]
- S1820  **`AppTransportSecurity` exception for a domain**       [HOTSPOT]
- S1821  **`UNUserNotificationCenter` showing PII**              [HOTSPOT]
- S1822  **`UIPasteboard.general.string` write of PII**          [HOTSPOT]
- S1823  **`Logger.log` of PII tokens (entropy-detected)**       [VULN]
- S1824  **deeplink intent → in-app web load without allowlist** [VULN]

### Objective-C — Code smells (~25)

S2000-range. Naming, magic numbers, long methods, large classes, `goto` usage,
`#pragma mark` discipline, `id` over typed pointer, etc. — see
[`docs/rules/objc-codesmells.md`](rules/objc-codesmells.md).

### Objective-C — Bugs (~15)

S2500-range. NSNumber↔BOOL, retain-cycle in blocks, `self` capture in blocks
without `__weak`, comparing `NSNumber` with `==`, `dealloc` calling `[super
dealloc]` under ARC, etc.

### Objective-C — Vulnerabilities + Hotspots (~10)

S2800-range. Mirrors the Swift catalog: ATS, weak crypto, deeplinks,
keychain accessibility, etc.

### Cross-language (S3000+)

- S3000 **duplication exceeds threshold** (informational; CPD computes actual)
- S3001 **public function with no test coverage** (uses coverage data)
- S3002 **file with extremely low comment density on public API**
- S3003 **file present in target but not in Xcode project (orphan)**

## Tagging conventions

We tag every rule with one or more of:

- `apple-platforms`, `ios`, `macos`, `watchos`, `tvos`, `visionos`
- `swiftui`, `combine`, `concurrency`, `objc-runtime`
- `masvs-storage-1`, `masvs-crypto-1` … (MASVS chapter alignment)
- `cwe-XXX` for CWE-mapped findings
- `swiftlint:<rule>` / `oclint:<rule>` for cross-tool grepping
- `convention`, `pitfall`, `dead-store`, `unused`, `complexity`, `naming`

Quality gates can then enable/disable whole tag families.

## Severity defaults

| Severity   | When                                                                                |
| ---------- | ----------------------------------------------------------------------------------- |
| `BLOCKER`  | Crashes (force-unwrap on UI thread), security vulnerabilities with concrete exploit |
| `CRITICAL` | Likely-bug or vulnerability without concrete exploit                                |
| `MAJOR`    | Bugs that produce wrong results but rarely crash; major code smells                 |
| `MINOR`    | Style violations with semantic implication                                          |
| `INFO`     | Pure style, naming                                                                  |

## Activation in default profile

The *Sonar way* profile activates ~60% of rules — the high-signal ones with low
false-positive rate. *Strict* activates everything. *SwiftLint-compat* activates
the subset that has SwiftLint equivalents and uses SwiftLint severity defaults.
