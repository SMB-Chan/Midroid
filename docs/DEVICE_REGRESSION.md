# Device regression matrix

Unified physical-device checklist for release candidates. Copy
`docs/release-audits/TEMPLATE.md` per candidate and file the completed record
under `docs/release-audits/`. This matrix consolidates the release-candidate
checks in `RELEASING.md`, the 16-item media matrix in `MEDIA_COMPATIBILITY.md`,
and the push/API surface added since v0.1.4.

## Coverage targets

- At least **one physical Android device** per candidate (emulator alone is
  not sufficient for media, power, or WebView-provider behavior).
- API spread: one device near `minSdk` (26–28, emulator acceptable for
  install/launch only) plus one device on API 35/36 covering the full matrix.
- Record the WebView provider and version per device (`MidroidDiag`
  `webview=` field, or Settings → provider line). Multi-account cases need a
  provider with `MULTI_PROFILE` support; the unsupported-provider path needs
  one without it (or an emulator image where the feature flag is off).
- Benchmark linkage: power claims require the `BENCHMARKING.md` A/B/A protocol
  (each target ≥3 runs, same device/instance/brightness/network/temperature).
  Record benchmark labels in the audit file; do not paste raw dumps.

## Sanitization rules for records

Never include in audit files, issues, or benchmark labels:

- API tokens, MiAuth session ids, cookies, WebView storage contents;
- note IDs, notification contents, account names, instance-internal URLs
  (record `scheme://host` only, as `MidroidDiag` does);
- keystore passwords, signing material, full certificate blobs
  (SHA-256 fingerprints only);
- screenshots showing private timelines, DMs, or credentials.

## Matrix

Mark each case pass / fail / N/A with a one-line note. Failures block the
candidate; N/A needs a reason (e.g. "single-profile provider").

### 1. Install and first launch

- [ ] Fresh install of the candidate APK (signed preflight or release).
- [ ] Setup screen renders inside system bars / cutout / IME without overlap.
- [ ] Invalid instance URL is rejected with a visible error (try `http://`,
      empty string, garbage).
- [ ] Valid HTTPS instance opens the Misskey login page in-app.

### 2. Login persistence and lifecycle

- [ ] Sign in, background the app (HOME → `event=hidden` in `MidroidDiag`),
      foreground again: session retained, no re-login.
- [ ] Force-stop from system settings, relaunch: last same-origin URL
      restored, session retained.
- [ ] Rotate the device on timeline and on an open lightbox: no crash, no
      forced re-login, controls stay reachable.

### 3. Navigation boundary

- [ ] Same-origin Misskey links stay in-app (timeline → note → profile).
- [ ] Non-instance top-level links open the system browser/app chooser.
- [ ] Back from a note returns to the timeline; Back from `#pswp` lightbox
      closes/interrupts to the committed page, never to a blank view.
- [ ] `mailto:`/`tel:`/`geo:` intents resolve or show the safe fallback toast.

### 4. Settings and display

- [ ] Each power mode (Eco / Balanced / Performance) applies and persists
      across restart; Eco shows reduced motion and stops autoplay media.
- [ ] Each text scale (Auto / 100 / 115 / 130 / 145) applies; Auto adapts to
      the device without clipping Misskey controls.
- [ ] Each reaction scale (Standard / Large / Extra Large) applies to
      timeline, deck, and notification rows without avatar overlap.
- [ ] Copy-diagnostics output contains no path/query/fragment/token
      (eyeball the pasted text before discarding it).

### 5. Accounts and profiles

- [ ] Add a second account on the same instance: isolated profile, independent
      login, switcher shows both with the active marker.
- [ ] Add an account on a different instance: label shows the host.
- [ ] Switch accounts back and forth: each restores its own last URL.
- [ ] On a provider without `MULTI_PROFILE`: app stays on the default account
      with the recovery message; no blank screen, no crash.

### 6. Media and downloads (`MEDIA_COMPATIBILITY.md` full list)

- [ ] MP3 play/pause/seek in Eco, Balanced, Performance; M4A/AAC; Ogg/Opus;
      FLAC; WAV.
- [ ] For audio that fails in WebView, `Androidで再生` / `Play with Android`
      prepares, plays, pauses, seeks.
- [ ] Fallback button is absent on plain timelines and on image/video
      lightboxes (including after visiting an audio item first).
- [ ] Session-protected audio stays on the WebView path (native fallback may
      legitimately fail without browser credentials — record as expected).
- [ ] MP4/H.264 and WebM video play/pause/seek; one federated remote-instance
      attachment.
- [ ] PDF and ZIP download via DownloadManager notification; completed file
      opens in an installed viewer.
- [ ] Background the app during WebView and native playback: media pauses,
      no auto-resume on foreground.

### 7. Renderer recovery

- [ ] Trigger or simulate renderer reclaim (background the app under memory
      pressure, or `adb shell am kill-all` style pressure on a test device):
      app shows the restore toast and reloads the last committed URL.

### 8. Push permission and channels (no server needed)

- [ ] Fresh install: `POST_NOTIFICATIONS` is requested through the rationale
      flow only where the OS requires runtime permission (API 33+); on older
      APIs channels just exist.
- [ ] Deny path: denial toast shown, app stays fully usable, re-entry point
      documented (system settings).
- [ ] Grant path: both channels (`Misskey mentions`, `Misskey updates`)
      appear in system notification settings.
- [ ] Tap-routing logic: only verifiable with a real encrypted push today;
      otherwise verify by code path review that `handlePushIntent` rejects
      cross-origin targets and consumes the intent (see template notes).

### 9. Push keys and MiAuth units (no UI path yet — record as deferred)

- [ ] No UI enrolls push or MiAuth in this build; confirm no permission,
      service, or network call fires without explicit user action
      (logcat `MidroidDiag` shows no push events).
- [ ] Uninstall/reinstall: old `midroid_push*` / `midroid_miauth` state is
      gone (allowBackup=false); app starts at setup, no stale-account crash.

### 10. Update over the previous build

- [ ] Install the previous signed build, sign in, then install the candidate
      over it: Android accepts it as an update (no uninstall).
- [ ] Login/WebView data retained; reported version matches the candidate.
- [ ] `versionCode` strictly greater than the previous release asset;
      `apksigner --print-certs` SHA-256 fingerprint identical to the previous
      stable fingerprint.

### 11. Permissions and manifest

- [ ] `aapt dump permissions` / `apksigner` output shows no unexpected
      permissions versus `PRIVACY.md` (direct: INTERNET,
      POST_NOTIFICATIONS; merged library additions documented).
- [ ] `CHANGELOG.md`, `README.md`, `PRIVACY.md`, `SECURITY.md` describe
      current behavior.

## After the run

- Attach the completed `docs/release-audits/<version>.md` record.
- Attach `build/sbom/advisory-report.txt` (see `docs/SBOM.md`).
- Link benchmark labels for any power/memory claim.
- File follow-up issues for every failure; do not silently downgrade a case
  to N/A.
