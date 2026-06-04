# Security analysis

What the plugin can detect, how we organize security findings, and how to
configure suppressions.

## Findings split: vulnerabilities vs. hotspots

SonarQube distinguishes:

- **Vulnerability** (`VULNERABILITY`) — "this is bad and you should fix it."
  Auto-raised, must be resolved.
- **Security hotspot** (`SECURITY_HOTSPOT`) — "this *might* be bad, a human
  needs to look." Doesn't fail the gate by default; goes through a *review*
  workflow.

We use both. As a rule of thumb:

- Concrete misuse (hardcoded secret, `NSAllowsArbitraryLoads=true`, MD5 used
  for password hashing) → **Vulnerability**.
- Dual-use API (shell exec, WKWebView `javaScriptEnabled`, URL handling) →
  **Hotspot**.

## Framework alignments

Every security rule is tagged with at least one of:

- **CWE** — Common Weakness Enumeration (e.g. `cwe-798` hardcoded credentials)
- **OWASP MASVS** — Mobile App Security Verification Standard, chapters
  `masvs-storage`, `masvs-crypto`, `masvs-network`, `masvs-platform`,
  `masvs-code`, `masvs-resilience`
- **OWASP Mobile Top 10** — `owasp-mobile-m1` … `m10`

Quality gates can fail on "any unresolved finding tagged `masvs-crypto-1`",
which is how regulated teams roll.

## Rule catalog (security-focused subset)

Full list lives in [`rules.md`](rules.md), tagged. Below is just the security
slice grouped by MASVS chapter:

### MASVS-STORAGE (sensitive data storage)

- S1808 **`UserDefaults` used for sensitive data**                 [VULN]
- S1809 **`Keychain` with `kSecAttrAccessibleAlways`**             [VULN]
- S1815 **clipboard read of sensitive UI text**                     [HOTSPOT]
- S1822 **`UIPasteboard.general.string` write of PII**              [HOTSPOT]
- S1823 **`Logger.log` of PII tokens (entropy-detected)**           [VULN]

### MASVS-CRYPTO

- S1804 **weak crypto: MD5**                                        [VULN]
- S1805 **weak crypto: SHA1 for non-checksum use**                  [VULN]
- S1806 **weak crypto: ECB mode**                                   [VULN]
- S1807 **`arc4random` used as CSPRNG**                             [VULN]
- S1819 **`URLSession` with `serverTrust` always-accept**           [VULN]

### MASVS-NETWORK

- S1802 **`http://` URL where `https://` is expected**              [VULN]
- S1803 **`NSAllowsArbitraryLoads = true` in Info.plist**           [VULN]
- S1818 **`SSL pinning` not configured for sensitive host**         [HOTSPOT]
- S1820 **`AppTransportSecurity` exception for a domain**           [HOTSPOT]

### MASVS-PLATFORM

- S1810 **deeplink handler without source validation**              [VULN]
- S1811 **`UIWebView` (deprecated, vulnerable)**                    [VULN]
- S1812 **`WKWebView` with `javaScriptEnabled=true` on untrusted content** [HOTSPOT]
- S1824 **deeplink intent → in-app web load without allowlist**     [VULN]
- S1817 **`Process` / `NSTask` launched with shell**                [HOTSPOT]

### MASVS-CODE / hardcoded secrets

- S1800 **hardcoded API key / token**                               [VULN]
- S1801 **hardcoded credential (entropy + identifier-name heuristic)** [VULN]

## Hardcoded secret detection — how it works

Two pipelines:

1. **Regex catalog** — known patterns (AWS access keys `AKIA[0-9A-Z]{16}`,
   GitHub PATs `ghp_[0-9a-zA-Z]{36}`, Slack tokens `xox[baprs]-…`, JWTs,
   private keys, etc.). The catalog ships in
   `resources/io/sonarswift/plugin/security/secrets.json` and is upgradeable
   without rebuilding the plugin.
2. **Identifier heuristic + entropy** — Shannon entropy on string literals
   assigned to identifiers whose name matches `(?i)key|secret|token|pwd|password|auth`.
   Entropy threshold defaults to `4.5` bits/char, configurable via
   `sonar.swift.security.secrets.entropyThreshold`.

To suppress a false positive:

```swift
// sonar-secret:keep — this is a public sample key documented in our README
let SampleAPIKey = "AKIA0000000000000000"
```

## Info.plist analysis

The plugin reads `Info.plist` (XML or binary) and raises:

- S1803 if `NSAllowsArbitraryLoads = YES`
- S1820 for each domain under `NSExceptionDomains` with weaker config
- S2900 (ObjC range) for `UISupportedInterfaceOrientations` misconfig
  ([example only — quality issue, not security])

## Suppression hierarchy

Same as everything else in SonarQube, in priority order:

1. Inline marker comment on the line / block of the finding
   - Swift: `// sonar-disable:S1800` on the line above, or
     `// sonar-disable-next-line:S1800`
   - ObjC: same syntax
2. `sonar.issue.ignore.multicriteria` rules in `sonar-project.properties`
3. Sonar UI "Mark as won't fix" / "Mark as false positive"

We deliberately do **not** support whole-file or whole-class disable comments
for security rules — they're too easy to apply too broadly. If a whole class
needs an exemption, put it in `sonar-project.properties` so it's reviewed in
code-review.

## SAST honesty disclaimer

We do AST-level pattern matching, not interprocedural taint analysis. We
catch the *clear-cut* misuses (a hardcoded key, an MD5 call) reliably. We
will not catch:

- A user-controlled string flowing through 3 helpers and into `eval`-like API
- Crypto weaknesses that depend on data dependencies we can't track

For full taint-based SAST on Apple platforms, complement this plugin with a
specialized tool. We document this honestly so teams don't take false
assurance.
