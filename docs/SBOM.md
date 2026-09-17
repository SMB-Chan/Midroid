# Midroid SBOM and advisory procedure

This document closes the 2026-09-07 audit gap: only 3 direct dependencies had
been checked against the GitHub Advisory Database, with no transitive SBOM
matching and no WebView-provider coverage. The procedure below covers the full
resolved `releaseRuntimeClasspath` closure on every check.

## Scope

- **In scope:** every Maven coordinate resolved into the release runtime
  classpath (direct + transitive), as dumped by Gradle itself.
- **Out of scope (documented):** the Android System WebView provider APK and
  the Android OS. They are separate trust domains updated by the device vendor;
  Midroid records the provider/version it was tested against in device records
  (`MidroidDiag` `webview=` field) instead of claiming SBOM coverage over them.
- **Also out of scope:** debug-only and test-only configurations. The shipped
  surface is the minified release APK.

## Inventory method

The closure is produced from the build, not hand-maintained:

```text
export JAVA_HOME=<jdk-17>
export ANDROID_HOME=<sdk-root> ANDROID_SDK_ROOT=<sdk-root>
./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath
```

`tools/check_advisories.sh` automates this: it resolves the configuration,
extracts unique `group:name:version` coordinates into
`build/sbom/release-closure.txt`, then queries each against OSV
(`https://api.osv.dev/v1/query`, `ecosystem: Maven`).

```text
bash tools/check_advisories.sh            # resolve + query (needs network)
MIDROID_SBOM_OUT=/tmp/sbom bash tools/check_advisories.sh
bash tools/check_advisories.sh --offline  # resolve only, no queries
```

The script fails closed: empty closures, unparseable responses, empty
responses, and any reported vulnerability all exit non-zero. Per-coordinate
results land in `build/sbom/advisory-report.txt` (gitignored build output).

## Current closure (2026-09-17, all clean per OSV)

Direct release dependencies (from `app/build.gradle.kts`):

- `androidx.activity:activity:1.13.0`
- `androidx.webkit:webkit:1.17.0`
- `androidx.media3:media3-exoplayer:1.11.0`
- `org.jetbrains.kotlin:kotlin-stdlib:2.2.10` (via AGP/Kotlin plugin)

Resolved closure at check time: **75 unique coordinates, 0 OSV hits**
(report: `generated_utc=2026-09-17T02:27:45Z`). The live run output above is
the evidence; the count will change with dependency updates, which is why the
script regenerates it rather than freezing it here. Notable transitive families:

- `androidx.core:core:1.18.0` / `core-ktx:1.18.0`, `annotation:1.9.1`,
  `collection:1.4.2`, `lifecycle:2.6.2`, `savedstate:1.2.1`,
  `profileinstaller:1.4.0`, `startup-runtime:1.1.1`, `tracing:1.2.0`;
- `androidx.media3:media3-{common,container,database,datasource,decoder,exoplayer,extractor}:1.11.0`;
- `androidx.webkit:webkit:1.17.0` (single artifact, no transitive deps);
- `org.jetbrains.kotlinx:kotlinx-coroutines-{android,core,core-jvm}:1.9.0`;
- `com.google.guava:guava:33.3.1-android` (pulled by media3 database;
  `listenablefuture` resolves to the empty `9999.0-empty-to-avoid-conflict-with-guava` stub);
- `org.jspecify:jspecify:1.0.0`, `org.jetbrains:annotations:23.0.0`.

The full pinned list is regenerated on demand; do not copy it into release
notes by hand. Re-run the script after any dependency change and before each
public release candidate.

## Advisory sources and cadence

- Primary: OSV Maven queries via the script (covers GitHub Advisory, PyPA-style
  cross-ecosystem aliases, and direct Maven reports where OSV ingests them).
- Secondary for release candidates: spot-check the three direct artifacts in
  the GitHub Advisory Database UI (Maven ecosystem), matching the original
  audit method.
- Cadence: on every dependency change, on each release candidate, and on
  demand when a new AndroidX/Media3/Kotlin advisory is published.
- A `VULNERABLE` result blocks the release candidate until the coordinate is
  upgraded and re-checked, or a documented risk acceptance lands in
  `docs/release-audits/`.

## CI integration

`android.yml` validates the script (`bash -n`) on every run. Live OSV queries
run in a dedicated non-blocking step: advisory data is informational on
per-commit CI (network-flaky runners must not red failed builds), but the
same script exit code **gates** release candidates and the signed preflight —
run it manually and attach `advisory-report.txt` to the release-audit record.

## Limitations

- OSV coverage is point-in-time; a clean report is not a guarantee.
- The script queries resolved versions only; version ranges and future
  resolutions are not modeled.
- WebView provider CVEs remain the vendor's domain; Midroid's mitigation is
  keeping `minSdk`/target current, refusing cleartext, and recording the
  tested provider version per device record.
