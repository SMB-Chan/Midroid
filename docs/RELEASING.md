# Public release runbook

This document defines the minimum checks for publishing Midroid APKs through GitHub Releases.

## One-time setup

1. Keep the repository private until source, history and release metadata have been reviewed for secrets and private data.
2. Generate the dedicated update-signing identity on a trusted machine with:

   ```text
   bash tools/bootstrap_update_signing.sh
   ```

3. Back up the generated keystore and passwords separately. Losing the key breaks in-place updates for users on that signing identity.
4. Confirm these GitHub Actions Secrets exist:
   - `MIDROID_SIGNING_KEYSTORE_B64`
   - `MIDROID_SIGNING_STORE_PASSWORD`
   - `MIDROID_SIGNING_KEY_ALIAS`
   - `MIDROID_SIGNING_KEY_PASSWORD`
5. Before making the repository public, enable private vulnerability reporting / GitHub Security Advisories if available.
6. Review repository visibility, default branch and branch-protection settings.

Never upload the keystore as a repository file, issue attachment, Actions artifact or Release asset.

## Release candidate checks

Before tagging a public version:

- CI on `main` is green.
- `gradle lint testDebugUnitTest assembleDebug assembleRelease` passes.
- The candidate has been installed and exercised on at least one supported physical Android device.
- App launch, login persistence, timeline navigation, settings, account switching, downloads and Back behavior have been checked.
- System bars / display cutouts / IME do not obscure Midroid or Misskey controls.
- Upgrade from the previous stable signed APK succeeds without clearing app data.
- No unexpected permissions were added.
- `CHANGELOG.md`, `README.md`, `PRIVACY.md` and `SECURITY.md` still describe current behavior.

For battery/performance claims, record reproducible evidence using `tools/benchmark_android.sh` rather than relying on subjective impressions.

## Versioning

Public release tags use:

```text
vMAJOR.MINOR.PATCH
```

Pre-release identifiers are allowed, for example `v0.1.0-rc.1`.

The release workflow derives `versionName` from the tag and uses the GitHub Actions run number as a monotonically increasing Android `versionCode`.

## Publish

After the release commit is on `main`, create and push the signed version tag. The `Public APK Release` workflow will:

1. require all four signing secrets;
2. install JDK 17, Android SDK 36 and Gradle 9.6.0;
3. run lint and JVM tests;
4. build the release APK with the persistent update key;
5. verify the APK signature;
6. generate SHA-256 and certificate-report files;
7. create a GitHub Release and attach all three files.

The workflow intentionally fails instead of publishing an unsigned or debug-signed APK.

## Post-release verification

From the GitHub Release page:

- download the APK and checksum;
- verify the SHA-256 locally;
- compare the signing certificate SHA-256 fingerprint with the previous stable release;
- install over the previous stable APK and confirm Android accepts it as an update;
- verify the app reports the expected version and retains login/application state.

If the certificate fingerprint unexpectedly changes, do not publish or instruct users to install the APK until the signing-key discrepancy is understood.

## Emergency response

For a release with a serious regression or vulnerability:

- do not replace an existing release asset silently;
- publish a new patch version with a higher `versionCode`;
- document the affected versions and mitigation;
- use the security-advisory process for vulnerabilities where disclosure should be coordinated.
