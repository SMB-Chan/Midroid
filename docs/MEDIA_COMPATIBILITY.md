# Misskey attachment and media compatibility

This document records Midroid's attachment-viewing boundary against the current Misskey frontend.

## Inline-preview path

Misskey previews browser-safe image, video, and audio MIME types inside `MkMediaList` / `MkLightbox`.
Midroid does not intercept media requests, so HTTP range requests, buffering, seeking, and decoding stay in Android System WebView.

### Audio expected to preview

- `audio/mpeg`
- `audio/mp4`
- `audio/x-m4a`
- `audio/aac`
- `audio/ogg`
- `audio/opus`
- `audio/webm`
- `audio/flac`, `audio/x-flac`
- `audio/wav`, `audio/vnd.wave`

Actual decoding remains dependent on the installed Android System WebView and device codec stack.
A container/MIME being allowed by Misskey does not guarantee every codec profile inside that container is decodable on every Android device.

### Video expected to preview

- `video/mp4`
- `video/x-m4v`
- `video/quicktime`
- `video/3gpp`, `video/3gpp2`
- `video/mpeg`
- `video/webm`
- `video/ogg`

Actual codec support is device/WebView-dependent.

## Eco media policy

Midroid must not set `WebSettings.mediaPlaybackRequiresUserGesture=true`.
Misskey opens its lightbox asynchronously and can call `HTMLMediaElement.play()` after the original pointer event task has ended. Android WebView's gesture gate can therefore reject legitimate user-initiated Misskey audio/video playback.

Eco mode instead saves power by:

- preferring 60 Hz,
- reducing animation/transition work,
- stopping `audio[autoplay]` and `video[autoplay]`, and
- pausing all `<audio>` / `<video>` elements when the activity moves to the background.

This preserves explicit Misskey media playback while keeping background/autoplay work conservative.

## Non-previewable attachments

Misskey renders other files with `MkMediaBanner` as a download link rather than an inline viewer. This includes, depending on MIME, PDF, Office documents, plain/binary documents, archives, executables, and other arbitrary Drive files.

Midroid's `DownloadListener` routes HTTPS downloads to Android `DownloadManager`, forwarding MIME type, user agent, and current cookies. The system download notification is then responsible for handing the completed file to an installed viewer.

Midroid intentionally does not render arbitrary downloaded documents inside its privileged/login WebView.

## Security boundaries

- Main Misskey instance must be HTTPS.
- Mixed HTTP content is blocked.
- WebView filesystem access is disabled.
- Third-party cookies are disabled.
- Camera/microphone Web permissions are denied in the MVP; this does not affect playback of already-attached media.
- Arbitrary files are not loaded into a custom in-app document renderer.

## Known edge cases

- `application/ogg` is in Misskey's browser-safe MIME list but current `isPreviewable()` only promotes MIME strings starting with `image`, `video`, or `audio`; it can therefore fall back to the download banner.
- A browser-safe container may contain a codec/profile unsupported by the installed Android System WebView.
- Cleartext (`http://`) attachment resources are intentionally not loaded inside Midroid.
- Remote/federated media that depends on third-party cookies can fail; normal public Misskey media should not require them.
- Android camera/microphone capture from web content remains intentionally unsupported in the MVP.

## Manual regression matrix

Test at least the following on a real device after media-policy changes:

1. MP3 audio, play/pause/seek in Eco, Balanced, Performance.
2. M4A/AAC audio.
3. Ogg/Opus audio.
4. FLAC and WAV audio.
5. MP4/H.264 video, play/pause/seek.
6. WebM video.
7. Sensitive audio/video reveal then playback.
8. A federated remote-instance audio/video attachment.
9. PDF download and open from the Android download notification.
10. ZIP or other arbitrary attachment download.
11. Background Midroid during playback; verify media pauses.
12. Return to foreground; verify playback does not restart unexpectedly.
