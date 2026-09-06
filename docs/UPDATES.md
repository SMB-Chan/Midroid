# Update-compatible APKs

Android preserves an app's private data directory across an in-place package update when the package name and signing identity remain compatible. Midroid relies on that normal platform behavior for its saved instance settings, WebView cookies, site storage and other login state.

## Stable update signing

GitHub Actions supports a dedicated update-compatible signing track. Configure these repository secrets once:

- `MIDROID_SIGNING_KEYSTORE_B64`: base64 of a dedicated Midroid JKS/PKCS12 keystore
- `MIDROID_SIGNING_STORE_PASSWORD`: keystore password
- `MIDROID_SIGNING_KEY_ALIAS`: key alias
- `MIDROID_SIGNING_KEY_PASSWORD`: key password

When all four are available, CI signs the debug APK with that stable key and publishes an additional `Midroid-update-apk` artifact. The normal `Midroid-ci-debug-apk` artifact is still produced for verification. If the secrets are absent, the update-compatible artifact is deliberately skipped.

The signing key must be retained permanently for the lifetime of the update channel. Losing or replacing it means Android will not accept the new APK as an update to an installation signed by the old key.

Do not commit the private keystore to the repository. Keep it in a password manager/offline backup and store only its base64 form and passwords in GitHub Actions Secrets.

## Versioning

CI supplies a monotonically increasing `versionCode` from the GitHub Actions workflow run number and a matching `0.1.0-ci.<run>` version name. Local builds without those environment variables keep the project defaults.

## First migration

Historical Midroid CI debug APKs were signed by the runner's temporary debug signing identity. Therefore the first move onto the stable update channel can require uninstalling the old CI-debug installation before installing the first `Midroid-update-apk`.

That first uninstall removes Android app data, including the existing login state. After the stable update APK is installed and the user signs in again, later `Midroid-update-apk` builds can be installed over it without clearing app data.

Do not uninstall the stable-track app during ordinary updates. Install the newer APK directly over the existing package. Keep the application ID `dev.midroid.app` and the stable signing key unchanged.

## Artifact identity

Every CI APK artifact includes:

- `app-debug.apk`
- `app-debug.apk.sha256`
- `app-debug.signing.txt`

The signing report contains the APK signing certificate fingerprint. For update-track builds, this fingerprint should remain constant across releases while the APK SHA-256 naturally changes as the app changes.
