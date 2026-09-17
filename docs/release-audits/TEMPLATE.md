# Release audit record — TEMPLATE

Copy this file to `docs/release-audits/<version>.md` (e.g. `v0.2.0.md`) and
fill it during the candidate run. Follow `docs/DEVICE_REGRESSION.md` for case
definitions and sanitization rules. One file per candidate; never rewrite a
filed record — supersede it with a new one.

## Build

- candidate commit: `<sha>`
- CI run: `<url>`
- APK: `<preflight artifact name | release file name>`
- `versionName`: `<x.y.z>`
- `versionCode`: `<n>` (previous: `<m>`, strictly greater: yes/no)
- signing cert SHA-256: `<fingerprint>` (matches previous stable: yes/no/N/A-first-release)
- APK SHA-256: `<hex>`

## Devices

| # | model | Android/API | WebView provider + version | role |
|---|-------|-------------|----------------------------|------|
| 1 | e.g. Pixel 8 | 16 / 36 | Android System WebView 12x.x | full matrix |
| 2 | e.g. low-end | 8 / 26 | <provider + version> | install/launch only |

## Matrix results

Copy the case list from `docs/DEVICE_REGRESSION.md` sections 1–11. Mark each
case `pass` / `fail` / `N/A(reason)` with the device # it ran on.

```text
1. install/fresh-launch ............ pass (device 1)
1. setup-insets .................... pass (device 1)
...
5. unsupported-provider-recovery .... N/A (no legacy provider available)
8. push-tap-routing ................. deferred (no live push; code-path reviewed)
10. update-over-previous ............ pass (device 1, <prev> -> <cand>)
```

Failures (one line each, issue link required):

```text
- <case>: <symptom> -> #<issue>
```

## SBOM and advisories

- `tools/check_advisories.sh` run UTC: `<timestamp>`
- coordinates checked: `<n>`; result: clean / `<k>` need attention (link issues)
- report file: `build/sbom/advisory-report.txt` (attach or paste RESULT line)

## Benchmarks (only if claiming power/memory effects)

- protocol: A/B/A per `docs/BENCHMARKING.md`
- labels: `<midroid-eco-01, chrome-01, ...>` (raw dumps stay out of the repo)
- conclusion: `<one line, or "no claim made">`

## Verdict

- [ ] GO — all non-N/A cases pass, versionCode/cert verified, SBOM clean.
- [ ] NO-GO — failures listed above with follow-up issues.

Recorder: `<name>` — date (UTC): `<yyyy-mm-dd>`
