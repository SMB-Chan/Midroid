package dev.midroid.app.web

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.midroid.app.config.InstanceConfig
import dev.midroid.app.media.NativeAudioRequest

class MisskeyMediaFallbackBridge {
    companion object {
        private const val JS_OBJECT_NAME = "MidroidNativeAudio"
    }

    fun attach(
        webView: WebView,
        instance: InstanceConfig,
        onNativeAudioRequested: (NativeAudioRequest) -> Unit,
    ): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            return false
        }

        WebViewCompat.addWebMessageListener(
            webView,
            JS_OBJECT_NAME,
            setOf(instance.origin),
            WebViewCompat.WebMessageListener { sourceWebView, message, sourceOrigin, isMainFrame, _ ->
                if (
                    sourceWebView === webView &&
                    isMainFrame &&
                    instance.owns(sourceOrigin.toString())
                ) {
                    message.data
                        ?.let(NativeAudioRequest::parse)
                        ?.let(onNativeAudioRequested)
                }
            },
        )
        return true
    }

    fun install(webView: WebView) {
        webView.evaluateJavascript(
            """
            (() => {
              const key = '__midroid_native_audio_fallback_v4';
              const oldKeys = [
                '__midroid_native_audio_fallback_v3',
                '__midroid_native_audio_fallback_v2',
                '__midroid_native_audio_fallback',
              ];
              const oldButtonIds = [
                '__midroid_native_audio_fallback_v3_button',
                '__midroid_native_audio_fallback_v2_button',
                '__midroid_native_audio_fallback',
              ];
              const buttonId = '__midroid_native_audio_fallback_v4_button';

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

              const activeLightboxRoot = () => {
                if (window.location.hash !== '#pswp') return null;
                const backgrounds = Array.from(document.querySelectorAll('._modalBg'));
                for (let index = backgrounds.length - 1; index >= 0; index -= 1) {
                  const root = backgrounds[index]?.parentElement;
                  if (root?.isConnected) return root;
                }
                return null;
              };

              const isAudioInActiveItem = (audio, root) => {
                if (!(audio instanceof HTMLAudioElement) || !(root instanceof Element)) return false;
                if (!audio.isConnected || !root.contains(audio)) return false;

                const centerX = window.innerWidth / 2;
                const centerY = window.innerHeight / 2;
                let node = audio;
                while (node && node !== root) {
                  if (node instanceof Element) {
                    const rect = node.getBoundingClientRect();
                    if (
                      rect.width >= Math.max(1, window.innerWidth * 0.6) &&
                      rect.height >= Math.max(1, window.innerHeight * 0.5)
                    ) {
                      return (
                        rect.left <= centerX &&
                        rect.right >= centerX &&
                        rect.top <= centerY &&
                        rect.bottom >= centerY
                      );
                    }
                  }
                  node = node.parentElement;
                }
                return false;
              };

              let lastAudio = null;
              let refreshScheduled = false;
              let settleTimer = null;

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
                const root = activeLightboxRoot();
                if (!root) return null;

                if (
                  lastAudio?.isConnected &&
                  sourceFor(lastAudio) &&
                  isAudioInActiveItem(lastAudio, root)
                ) {
                  return lastAudio;
                }

                const candidates = Array.from(root.querySelectorAll('audio'))
                  .filter((audio) => sourceFor(audio) && isAudioInActiveItem(audio, root));
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

              const removeButtons = () => {
                for (const id of oldButtonIds) document.getElementById(id)?.remove();
                document.getElementById(buttonId)?.remove();
              };

              const buildNativeUrl = () => {
                const audio = selectAudio();
                const source = sourceFor(audio);
                if (!source) return null;

                const target = new URL('midroid-audio://play');
                target.searchParams.set('url', source);
                const title = audio?.getAttribute('aria-label') ||
                  audio?.getAttribute('title') ||
                  document.title ||
                  'Misskey audio';
                target.searchParams.set('title', title);
                return target.toString();
              };

              const openNative = (event) => {
                event?.preventDefault?.();
                event?.stopPropagation?.();
                const target = buildNativeUrl();
                if (!target) return false;

                const bridge = window.MidroidNativeAudio;
                if (bridge && typeof bridge.postMessage === 'function') {
                  bridge.postMessage(target);
                  return true;
                }

                // Compatibility fallback for WebView implementations without WEB_MESSAGE_LISTENER.
                // MidroidWebViewClient consumes this scheme and only honors main-frame requests.
                window.location.assign(target);
                return true;
              };

              const refresh = () => {
                if (!activeLightboxRoot()) {
                  removeButtons();
                  return false;
                }

                const audio = selectAudio();
                const source = sourceFor(audio);
                if (!source) {
                  removeButtons();
                  return false;
                }

                for (const id of oldButtonIds) document.getElementById(id)?.remove();
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

              const scheduleRefresh = () => {
                if (!refreshScheduled) {
                  refreshScheduled = true;
                  requestAnimationFrame(() => {
                    refreshScheduled = false;
                    refresh();
                  });
                }

                if (settleTimer !== null) clearTimeout(settleTimer);
                settleTimer = setTimeout(() => {
                  settleTimer = null;
                  refresh();
                }, 250);
              };

              if (window[key]) {
                window[key].refresh();
                return;
              }

              for (const oldKey of oldKeys) {
                try { delete window[oldKey]; } catch (_) {}
              }

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
                    if (node.matches?.('audio, source, ._modalBg') || node.querySelector?.('audio, source, ._modalBg')) {
                      scheduleRefresh();
                      return;
                    }
                  }
                  for (const node of record.removedNodes) {
                    if (!(node instanceof Element)) continue;
                    if (node.matches?.('audio, source, ._modalBg') || node.querySelector?.('audio, source, ._modalBg')) {
                      scheduleRefresh();
                      return;
                    }
                  }
                  if (
                    record.type === 'attributes' &&
                    (record.target instanceof HTMLAudioElement || record.target instanceof HTMLSourceElement)
                  ) {
                    scheduleRefresh();
                    return;
                  }
                }
              });
              observer.observe(document.documentElement, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['src'],
              });

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
              const existing = window['__midroid_native_audio_fallback_v4'];
              if (existing && typeof existing.openNative === 'function') {
                return existing.openNative();
              }
              return false;
            })();
            """.trimIndent(),
        ) { rawResult ->
            onResult(rawResult == "true")
        }
    }
}
