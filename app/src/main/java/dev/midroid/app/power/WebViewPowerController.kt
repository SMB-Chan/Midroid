package dev.midroid.app.power

import android.app.Activity
import android.os.Build
import android.view.View
import android.webkit.WebView

class WebViewPowerController {
    fun configure(activity: Activity, webView: WebView, mode: PowerMode) {
        // Misskey opens its lightbox asynchronously and starts audio/video only after the
        // viewer component has been mounted. Requiring a WebView user gesture here can lose
        // the original tap activation before HTMLMediaElement.play() runs, especially in Eco.
        // Keep WebView playback available and enforce Eco policy at the document/lifecycle
        // layer instead: autoplay-tagged media is stopped and all media is paused onStop().
        webView.settings.mediaPlaybackRequiresUserGesture = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (mode) {
                PowerMode.ECO -> webView.setRendererPriorityPolicy(
                    WebView.RENDERER_PRIORITY_BOUND,
                    true,
                )
                PowerMode.BALANCED -> webView.setRendererPriorityPolicy(
                    WebView.RENDERER_PRIORITY_IMPORTANT,
                    true,
                )
                PowerMode.PERFORMANCE -> webView.setRendererPriorityPolicy(
                    WebView.RENDERER_PRIORITY_IMPORTANT,
                    false,
                )
            }
        }

        applyRefreshRatePreference(activity, webView, mode)
    }

    fun onForeground(activity: Activity, webView: WebView, mode: PowerMode) {
        webView.resumeTimers()
        webView.onResume()
        applyRefreshRatePreference(activity, webView, mode)
        applyDocumentPolicy(webView, mode)
    }

    fun onBackground(webView: WebView) {
        pauseMedia(webView)
        webView.onPause()
        webView.pauseTimers()
    }

    fun onPageReady(activity: Activity, webView: WebView, mode: PowerMode) {
        // WebView creates part of its view hierarchy lazily. Reapply the hint after the
        // document is ready so API 36 propagation reaches that hierarchy as well.
        applyRefreshRatePreference(activity, webView, mode)
        applyDocumentPolicy(webView, mode)
    }

    private fun applyRefreshRatePreference(activity: Activity, webView: WebView, mode: PowerMode) {
        if (Build.VERSION.SDK_INT >= 36) {
            val requestedRate = if (mode == PowerMode.ECO) {
                60f
            } else {
                View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
            }
            webView.propagateRequestedFrameRate(requestedRate, true)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val requestedRate = if (mode == PowerMode.ECO) {
                60f
            } else {
                View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
            }
            webView.setRequestedFrameRate(requestedRate)
            return
        }

        @Suppress("DEPRECATION")
        val params = activity.window.attributes
        @Suppress("DEPRECATION")
        run {
            params.preferredRefreshRate = if (mode == PowerMode.ECO) 60f else 0f
            activity.window.attributes = params
        }
    }

    private fun pauseMedia(webView: WebView) {
        webView.evaluateJavascript(
            """
            (() => {
              document.querySelectorAll('video, audio').forEach((media) => {
                try { media.pause(); } catch (_) {}
              });
            })();
            """.trimIndent(),
            null,
        )
    }

    private fun applyDocumentPolicy(webView: WebView, mode: PowerMode) {
        val modeKey = mode.key
        webView.evaluateJavascript(
            """
            (() => {
              const id = '__midroid_power_policy';
              let style = document.getElementById(id);
              if ('$modeKey' === 'eco') {
                if (!style) {
                  style = document.createElement('style');
                  style.id = id;
                  document.documentElement.appendChild(style);
                }
                style.textContent = `
                  *, *::before, *::after {
                    animation-duration: 0.001ms !important;
                    animation-iteration-count: 1 !important;
                    transition-duration: 0.001ms !important;
                    scroll-behavior: auto !important;
                  }
                `;
                const neutralizeAutoplay = (root) => {
                  const candidates = [];
                  if (root instanceof HTMLMediaElement) candidates.push(root);
                  if (root && root.querySelectorAll) {
                    root.querySelectorAll('video[autoplay], audio[autoplay]').forEach((media) => candidates.push(media));
                  }
                  candidates.forEach((media) => {
                    if (!media.hasAttribute('autoplay') && !media.autoplay) return;
                    media.autoplay = false;
                    media.removeAttribute('autoplay');
                    try { media.pause(); } catch (_) {}
                  });
                };

                neutralizeAutoplay(document);
                if (!window.__midroidEcoMediaObserver) {
                  const observer = new MutationObserver((mutations) => {
                    mutations.forEach((mutation) => {
                      mutation.addedNodes.forEach((node) => {
                        if (node.nodeType === Node.ELEMENT_NODE) neutralizeAutoplay(node);
                      });
                    });
                  });
                  observer.observe(document.documentElement, { childList: true, subtree: true });
                  window.__midroidEcoMediaObserver = observer;
                }
              } else {
                if (style) style.remove();
                if (window.__midroidEcoMediaObserver) {
                  window.__midroidEcoMediaObserver.disconnect();
                  delete window.__midroidEcoMediaObserver;
                }
              }
            })();
            """.trimIndent(),
            null,
        )
    }
}
