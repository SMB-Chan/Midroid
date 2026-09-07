package dev.midroid.app.web

import android.webkit.WebView
import dev.midroid.app.config.ReactionScale

class MisskeyUiTuner {
    fun applyReactionScale(webView: WebView, scale: ReactionScale) {
        val metrics = ReactionScalePolicy.forScale(scale)
        webView.evaluateJavascript(
            """
            (() => {
              const id = '__midroid_reaction_scale';
              let style = document.getElementById(id);
              if (!style) {
                style = document.createElement('style');
                style.id = id;
                document.documentElement.appendChild(style);
              }

              style.textContent = `
                button._button:has(> [style*="pointer-events: none"] + span) {
                  height: ${metrics.buttonHeightCssPx}px !important;
                  min-height: ${metrics.buttonHeightCssPx}px !important;
                  padding-left: ${metrics.horizontalPaddingCssPx}px !important;
                  padding-right: ${metrics.horizontalPaddingCssPx}px !important;
                  font-size: ${metrics.buttonFontPercent}% !important;
                  border-radius: 8px !important;
                  align-items: center !important;
                }

                button._button:has(> [style*="pointer-events: none"] + span)
                  > [style*="pointer-events: none"] {
                  max-height: calc(${metrics.buttonHeightCssPx}px - 10px) !important;
                }

                button._button:has(> [style*="pointer-events: none"] + span)
                  > span:last-child:not([alt]) {
                  line-height: 1 !important;
                  font-size: 0.65em !important;
                  margin-left: 6px !important;
                }
              `;
            })();
            """.trimIndent(),
            null,
        )
    }
}
