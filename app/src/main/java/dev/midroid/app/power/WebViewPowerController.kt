package dev.midroid.app.power

import android.app.Activity
import android.os.Build
import android.webkit.WebView

class WebViewPowerController {
    fun configure(activity: Activity, webView: WebView, mode: PowerMode) {
        webView.settings.mediaPlaybackRequiresUserGesture = mode == PowerMode.ECO

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

        val params = activity.window.attributes
        params.preferredRefreshRate = if (mode == PowerMode.ECO) 60f else 0f
        activity.window.attributes = params
    }

    fun onForeground(webView: WebView, mode: PowerMode) {
        webView.resumeTimers()
        webView.onResume()
        applyDocumentPolicy(webView, mode)
    }

    fun onBackground(webView: WebView) {
        pauseMedia(webView)
        webView.onPause()
        webView.pauseTimers()
    }

    fun onPageReady(webView: WebView, mode: PowerMode) {
        applyDocumentPolicy(webView, mode)
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
                document.querySelectorAll('video[autoplay], audio[autoplay]').forEach((media) => {
                  media.autoplay = false;
                  try { media.pause(); } catch (_) {}
                });
              } else if (style) {
                style.remove();
              }
            })();
            """.trimIndent(),
            null,
        )
    }
}
