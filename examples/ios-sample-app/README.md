# iOS sample app

A bug-ridden sample iOS project used to demonstrate the plugin end-to-end.
Every numbered comment in source refers to a rule the scanner should flag.

## Scan it

```bash
cd ../../          # repo root
./scripts/setup-dev.sh                          # one-time
./scripts/scan-local.sh examples/ios-sample-app
```

Then open <http://localhost:9000> and look for project
`io.sonarswift.examples.ios-sample`. You should see ~9 issues across 2 files,
including 1 BLOCKER (`S1800` hardcoded credential), 5 CRITICAL bugs
(force-unwrap, force-cast, force-try, MD5, MD5), and 3 minor smells (print(),
TODO, http://).

## Expected metrics

| Metric             | Expected value      |
| ------------------ | ------------------- |
| Files              | 2 (main) + 1 (test) |
| NCLOC              | ~45                 |
| Bugs               | 5                   |
| Code smells        | 3                   |
| Vulnerabilities    | 2                   |
| Duplicated blocks  | 1 (Profile.swift)   |
