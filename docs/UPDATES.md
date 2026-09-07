# Update-compatible APKs

Android preserves an app's private data directory across an in-place package update when the package name and signing identity remain compatible. Midroid relies on that normal platform behavior for saved instance settings, WebView cookies/site storage, account metadata and other login state.

## Stable update signing

GitHub Actions supports a dedicated update-compatible **release** track. Configure these repository secrets once:

- `MIDROID_SIGNING_KEYSTORE_B64`: base64 of a dedicated Midroid JKS/PKCS12 keystore
- `MIDROID_SIGNING_STORE_PASSWORD`: keystore password
- `MIDROID_SIGNING_KEY_ALIAS`: key alias
- `MIDROID_SIGNING_KEY_PASSWORD`: key password

When all four are available, ordinary Android CI signs `assembleRelease` with that stable key and uploads a maintainer artifact named `Midroid-update-apk`. The update artifact is `debuggable=false` with R8/resource shrinking enabled. The long-lived update key is never assigned to the debug build type.

The normal `Midroid-ci-debug-apk` artifact remains a developer/verification artifact signed with the ordinary ephemeral debug identity. It must not be used as the long-term update track. If the four stable signing secrets are absent, ordinary CI can still compile the release build for verification but deliberately skips the update-compatible artifact.

The signing key must be retained permanently for the lifetime of the update channel. Losing or replacing it means Android will not accept the new APK as an update to an installation signed by the old key.

Do not commit the private keystore to the repository. Keep it in a password manager/offline backup and store only its base64 form and passwords in GitHub Actions Secrets.

## Public GitHub Releases

`.github/workflows/release.yml` is the public distribution gate. A version tag such as `v0.1.0` triggers the workflow.

Unlike ordinary CI, the public release workflow treats missing signing configuration as a hard failure. It will not publish an unsigned APK or substitute a debug signing identity.

For a successful tag, the workflow:

1. validates the version tag;
2. requires all four stable signing secrets;
3. runs lint and JVM tests;
4. builds the non-debuggable, minified release APK;
5. verifies the APK with `apksigner`;
6. generates an APK SHA-256 file and signing-certificate report;
7. creates the GitHub Release and attaches the APK, checksum and certificate report.

Users should prefer these GitHub Release assets over Actions artifacts. See `docs/RELEASING.md` for the maintainer checklist.

## One-time bootstrap

On a trusted local machine with JDK `keytool` and authenticated GitHub CLI (`gh`) installed, run:

```text
bash tools/bootstrap_update_signing.sh
```

The script generates a 4096-bit RSA JKS under `~/.midroid/midroid-update.jks` by default, prompts for passwords without echoing them, and streams the four required values directly into `gh secret set`. It refuses to overwrite an existing key. The keystore stays on the local machine and must be backed up separately.

You can change the destination with `MIDROID_SIGNING_KEYSTORE_PATH`, the GitHub repository with `MIDROID_GITHUB_REPO`, the alias with `MIDROID_SIGNING_KEY_ALIAS`, and the certificate DN with `MIDROID_SIGNING_DNAME`.

Private-key file extensions and common local signing configuration files are ignored by `.gitignore`, but that is only a final guardrail: keep signing material outside the repository working tree.

## Versioning

Ordinary CI supplies a monotonically increasing `versionCode` from the GitHub Actions workflow run number and a matching CI-oriented version name.

For a public tag, the release workflow derives `versionName` from the tag (for example `v0.1.0` becomes `0.1.0`) and uses that release workflow's run number as the Android `versionCode`.

The Android application ID remains `dev.midroid.app`. Changing that ID or the stable signing key would create a different update identity.

## First migration

Historical Midroid CI debug APKs were signed by temporary debug signing identities. Therefore the first move onto the stable release update channel can require uninstalling the old CI-debug installation before installing the first stable-signed release APK.

That first uninstall removes Android app data, including the existing login state. After the stable release APK is installed and the user signs in again, later stable-signed Midroid APKs can be installed over it without clearing app data.

Do not uninstall the stable-track app during ordinary updates. Install the newer APK directly over the existing package. Keep the application ID `dev.midroid.app` and the stable signing key unchanged.

## Artifact identity

Ordinary CI `Midroid-ci-debug-apk` contains:

- `app-debug.apk`
- `app-debug.apk.sha256`
- `app-debug.signing.txt`

Ordinary CI `Midroid-update-apk` contains:

- `app-release.apk`
- `app-release.apk.sha256`
- `app-release.signing.txt`

Public GitHub Releases use versioned names:

- `Midroid-X.Y.Z.apk`
- `Midroid-X.Y.Z.apk.sha256`
- `Midroid-X.Y.Z.signing.txt`

The signing report contains the APK signing certificate fingerprint. For stable update builds, this fingerprint must remain constant across releases while the APK SHA-256 naturally changes as the app changes.

If a public release unexpectedly has a different signing-certificate fingerprint, treat that as a release-blocking incident until the cause is understood.
