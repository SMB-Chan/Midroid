# Update-compatible APKs

Android preserves an app's private data directory across an in-place package update when the package name and signing identity remain compatible. Midroid relies on that normal platform behavior for its saved instance settings, WebView cookies, site storage and other login state.

## Stable update signing

GitHub Actions supports a dedicated update-compatible **release** track. Configure these repository secrets once:

- `MIDROID_SIGNING_KEYSTORE_B64`: base64 of a dedicated Midroid JKS/PKCS12 keystore
- `MIDROID_SIGNING_STORE_PASSWORD`: keystore password
- `MIDROID_SIGNING_KEY_ALIAS`: key alias
- `MIDROID_SIGNING_KEY_PASSWORD`: key password

When all four are available, CI signs `assembleRelease` with that stable key and publishes `Midroid-update-apk`. The update artifact is `debuggable=false` with R8/resource shrinking enabled. The long-lived update key is never assigned to the debug build type.

The normal `Midroid-ci-debug-apk` artifact remains a developer/verification artifact signed with the ordinary ephemeral debug identity. It must not be used as the long-term update track. If the four stable signing secrets are absent, the release build is still compiled for verification but the update-compatible artifact is deliberately skipped.

The signing key must be retained permanently for the lifetime of the update channel. Losing or replacing it means Android will not accept the new APK as an update to an installation signed by the old key.

Do not commit the private keystore to the repository. Keep it in a password manager/offline backup and store only its base64 form and passwords in GitHub Actions Secrets.

## One-time bootstrap

On a trusted local machine with JDK `keytool` and authenticated GitHub CLI (`gh`) installed, run:

```text
bash tools/bootstrap_update_signing.sh
```

The script generates a 4096-bit RSA JKS under `~/.midroid/midroid-update.jks` by default, prompts for passwords without echoing them, and streams the four required values directly into `gh secret set`. It refuses to overwrite an existing key. The keystore stays on the local machine and must be backed up separately.

You can change the destination with `MIDROID_SIGNING_KEYSTORE_PATH`, the GitHub repository with `MIDROID_GITHUB_REPO`, the alias with `MIDROID_SIGNING_KEY_ALIAS`, and the certificate DN with `MIDROID_SIGNING_DNAME`.

Private-key file extensions are ignored by `.gitignore`, but that is only a final guardrail: keep signing material outside the repository working tree.

## Versioning

CI supplies a monotonically increasing `versionCode` from the GitHub Actions workflow run number and a matching `0.1.0-ci.<run>` version name. Local builds without those environment variables keep the project defaults.

## First migration

Historical Midroid CI debug APKs were signed by temporary debug signing identities. Therefore the first move onto the stable release update channel can require uninstalling the old CI-debug installation before installing the first `Midroid-update-apk`.

That first uninstall removes Android app data, including the existing login state. After the stable release APK is installed and the user signs in again, later `Midroid-update-apk` builds can be installed over it without clearing app data.

Do not uninstall the stable-track app during ordinary updates. Install the newer APK directly over the existing package. Keep the application ID `dev.midroid.app` and the stable signing key unchanged.

## Artifact identity

`Midroid-ci-debug-apk` contains:

- `app-debug.apk`
- `app-debug.apk.sha256`
- `app-debug.signing.txt`

`Midroid-update-apk` contains:

- `app-release.apk`
- `app-release.apk.sha256`
- `app-release.signing.txt`

The signing report contains the APK signing certificate fingerprint. For update-track builds, this fingerprint must remain constant across releases while the APK SHA-256 naturally changes as the app changes.
