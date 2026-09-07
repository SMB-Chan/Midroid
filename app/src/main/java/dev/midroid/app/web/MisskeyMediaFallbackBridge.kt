package dev.midroid.app.web

import android.webkit.WebView

class MisskeyMediaFallbackBridge {
    fun install(webView: WebView) {
        webView.evaluateJavascript(
            """
            (() => {
              const key = '__midroid_native_audio_fallback_v2';
              const oldButtonId = '__midroid_native_audio_fallback';
              const buttonId = '__midroid_native_audio_fallback_v2_button';

              const normalizeHttps = (raw) => {
                if (!raw) return null;
                try {
                  const resolved = new URL(raw, document.baseURI);
                  return resolved.protocol === 'https:' ? resolved.href : null;
                } catch (_) {
                  return null;
                }
              };

              const sourceFor = (audio) => {
                if (!(audio instanceof HTMLAudioElement)) return null;
                const values = [
                  audio.currentSrc,
                  audio.src,
                  audio.getAttribute('src'),
                ];
                for (const source of audio.querySelectorAll('source')) {
                  values.push(source.src, source.getAttribute('src'));
                }
                for (const value of values) {
                  const normalized = normalizeHttps(value);
                  if (normalized) return normalized;
                }
                return null;
              };

              let lastAudio = null;

              const remember = (audio) => {
                if (audio instanceof HTMLAudioElement && audio.isConnected) {
                  lastAudio = audio;
                }
              };

              const audioNearEvent = (event) => {
                const path = typeof event?.composedPath === 'function' ? event.composedPath() : [];
                for (const node of path) {
                  if (node instanceof HTMLAudioElement) return node;
                  if (!(node instanceof Element)) continue;
                  const nested = node.querySelector?.('audio');
                  if (nested instanceof HTMLAudioElement) return nested;
                }
                return null;
              };

              const selectAudio = () => {
                if (lastAudio?.isConnected && sourceFor(lastAudio)) return lastAudio;

                const candidates = Array.from(document.querySelectorAll('audio'))
                  .filter((audio) => sourceFor(audio));
                if (candidates.length === 0) return null;

                const playing = candidates.find((audio) => !audio.paused && !audio.ended);
                if (playing) {
                  lastAudio = playing;
                  return playing;
                }

                const ready = candidates
                  .filter((audio) => audio.readyState > HTMLMediaElement.HAVE_NOTHING)
                  .at(-1);
                const selected = ready ?? candidates.at(-1) ?? null;
                if (selected) lastAudio = selected;
                return selected;
              };

              const removeButton = () => {
                document.getElementById(oldButtonId)?.remove();
                document.getElementById(buttonId)?.remove();
              };

              const openNative = (event) => {
                event?.preventDefault?.();
                event?.stopPropagation?.();
                const audio = selectAudio();
                const source = sourceFor(audio);
                if (!source) return false;

                const target = new URL('midroid-audio://play');
                target.searchParams.set('url', source);
                const title = audio?.getAttribute('aria-label') ||
                  audio?.getAttribute('title') ||
                  document.title ||
                  'Misskey audio';
                target.searchParams.set('title', title);
                window.location.href = target.toString();
                return true;
              };

              const refresh = () => {
                const audio = selectAudio();
                const source = sourceFor(audio);
                if (!source) {
                  removeButton();
                  return false;
                }

                document.getElementById(oldButtonId)?.remove();
                let button = document.getElementById(buttonId);
                if (!button) {
                  button = document.createElement('button');
                  button.id = buttonId;
                  button.type = 'button';
                  button.textContent = (navigator.language || '').toLowerCase().startsWith('ja')
                    ? 'Androidで再生'
                    : 'Play with Android';
                  button.title = (navigator.language || '').toLowerCase().startsWith('ja')
                    ? 'WebViewで再生できない場合にAndroidのネイティブプレイヤーを使用します'
                    : 'Use the Android native player if WebView playback fails';
                  button.style.cssText = `
                    position: fixed;
                    right: 16px;
                    bottom: 160px;
                    z-index: 2147483646;
                    min-height: 46px;
                    padding: 0 15px;
                    border: 0;
                    border-radius: 999px;
                    background: var(--MI_THEME-accent, #86b300);
                    color: var(--MI_THEME-fgOnAccent, #fff);
                    font: inherit;
                    font-size: 14px;
                    font-weight: 700;
                    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.32);
                    touch-action: manipulation;
                  `;
                  button.addEventListener('click', openNative, true);
                  document.documentElement.appendChild(button);
                }
                return true;
              };

              if (window[key]) {
                window[key].refresh();
                return;
              }

              const scheduleRefresh = () => {
                setTimeout(refresh, 0);
                setTimeout(refresh, 150);
                setTimeout(refresh, 600);
                setTimeout(refresh, 1500);
              };

              const onAudioEvent = (event) => {
                if (event.target instanceof HTMLAudioElement) {
                  remember(event.target);
                  scheduleRefresh();
                }
              };

              document.addEventListener('click', (event) => {
                const nearby = audioNearEvent(event);
                if (nearby) remember(nearby);
                scheduleRefresh();
              }, true);
              for (const type of ['loadstart', 'loadedmetadata', 'canplay', 'play', 'error']) {
                document.addEventListener(type, onAudioEvent, true);
              }
              window.addEventListener('hashchange', scheduleRefresh);
              window.addEventListener('popstate', scheduleRefresh);

              const observer = new MutationObserver((records) => {
                for (const record of records) {
                  for (const node of record.addedNodes) {
                    if (!(node instanceof Element)) continue;
                    if (node.matches?.('audio, source') || node.querySelector?.('audio, source')) {
                      scheduleRefresh();
                      return;
                    }
                  }
                }
              });
              observer.observe(document.documentElement, { childList: true, subtree: true });

              window[key] = { refresh, openNative };
              scheduleRefresh();
            })();
            """.trimIndent(),
            null,
        )
    }

    fun requestPlayback(webView: WebView, onResult: (Boolean) -> Unit) {
        webView.evaluateJavascript(
            """
            (() => {
              const existing = window['__midroid_native_audio_fallback_v2'];
              if (existing && typeof existing.openNative === 'function') {
                return existing.openNative();
              }

              const normalizeHttps = (raw) => {
                if (!raw) return null;
                try {
                  const resolved = new URL(raw, document.baseURI);
                  return resolved.protocol === 'https:' ? resolved.href : null;
                } catch (_) {
                  return null;
                }
              };

              const sourceFor = (audio) => {
                const values = [audio.currentSrc, audio.src, audio.getAttribute('src')];
                for (const source of audio.querySelectorAll('source')) {
                  values.push(source.src, source.getAttribute('src'));
                }
                for (const value of values) {
                  const normalized = normalizeHttps(value);
                  if (normalized) return normalized;
                }
                return null;
              };

              const candidates = Array.from(document.querySelectorAll('audio'))
                .filter((audio) => sourceFor(audio));
              const audio = candidates.find((candidate) => !candidate.paused && !candidate.ended) ||
                candidates.filter((candidate) => candidate.readyState > HTMLMediaElement.HAVE_NOTHING).at(-1) ||
                candidates.at(-1) ||
                null;
              if (!audio) return false;

              const source = sourceFor(audio);
              if (!source) return false;

              const target = new URL('midroid-audio://play');
              target.searchParams.set('url', source);
              target.searchParams.set(
                'title',
                audio.getAttribute('aria-label') ||
                  audio.getAttribute('title') ||
                  document.title ||
                  'Misskey audio',
              );
              window.location.href = target.toString();
              return true;
            })();
            """.trimIndent(),
        ) { rawResult ->
            onResult(rawResult == "true")
        }
    }
}
