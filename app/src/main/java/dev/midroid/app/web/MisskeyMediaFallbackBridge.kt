package dev.midroid.app.web

import android.webkit.WebView

class MisskeyMediaFallbackBridge {
    fun install(webView: WebView) {
        webView.evaluateJavascript(
            """
            (() => {
              const key = '__midroid_native_audio_fallback_v1';
              const buttonId = '__midroid_native_audio_fallback';

              const selectAudio = () => {
                const candidates = Array.from(document.querySelectorAll('audio[src]'))
                  .filter((audio) => (audio.currentSrc || audio.src));
                if (candidates.length === 0) return null;
                return candidates.find((audio) => audio.offsetParent !== null) ?? candidates[candidates.length - 1];
              };

              const removeButton = () => {
                document.getElementById(buttonId)?.remove();
              };

              const openNative = (event) => {
                event?.preventDefault?.();
                event?.stopPropagation?.();
                const audio = selectAudio();
                const source = audio?.currentSrc || audio?.src;
                if (!source) return;

                const target = new URL('midroid-audio://play');
                target.searchParams.set('url', source);
                target.searchParams.set('title', document.title || 'Misskey audio');
                window.location.href = target.toString();
              };

              const refresh = () => {
                const audio = selectAudio();
                if (!audio) {
                  removeButton();
                  return;
                }

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
                    bottom: 104px;
                    z-index: 2147483646;
                    min-height: 44px;
                    padding: 0 14px;
                    border: 0;
                    border-radius: 999px;
                    background: var(--MI_THEME-accent, #86b300);
                    color: var(--MI_THEME-fgOnAccent, #fff);
                    font: inherit;
                    font-size: 14px;
                    font-weight: 700;
                    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.28);
                    touch-action: manipulation;
                  `;
                  button.addEventListener('click', openNative, true);
                  document.documentElement.appendChild(button);
                }
              };

              if (window[key]) {
                window[key].refresh();
                return;
              }

              const scheduleRefresh = () => {
                setTimeout(refresh, 0);
                setTimeout(refresh, 250);
                setTimeout(refresh, 900);
              };

              document.addEventListener('click', scheduleRefresh, true);
              document.addEventListener('error', (event) => {
                if (event.target instanceof HTMLAudioElement) scheduleRefresh();
              }, true);
              window.addEventListener('hashchange', scheduleRefresh);
              window.addEventListener('popstate', scheduleRefresh);

              window[key] = { refresh };
              refresh();
            })();
            """.trimIndent(),
            null,
        )
    }
}
