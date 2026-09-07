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

                /*
                 * Misskey's picker/deck was designed around its original emoji cell size.
                 * Enlarging only the cells can make the popup wider than the visual viewport.
                 * Keep the picker itself bounded by the dynamic viewport and let item rows
                 * reflow to fewer columns instead of overflowing horizontally.
                 */
                .omfetrab {
                  box-sizing: border-box !important;
                  width: min(100%, calc(100dvw - 16px - env(safe-area-inset-left, 0px) - env(safe-area-inset-right, 0px))) !important;
                  max-width: calc(100dvw - 16px - env(safe-area-inset-left, 0px) - env(safe-area-inset-right, 0px)) !important;
                  margin-left: auto !important;
                  margin-right: auto !important;
                  overflow-x: hidden !important;
                  overscroll-behavior-x: none !important;
                }

                .omfetrab * {
                  box-sizing: border-box !important;
                  max-width: 100%;
                }

                .omfetrab :has(> button._button.item) {
                  display: grid !important;
                  grid-template-columns: repeat(auto-fill, minmax(${metrics.deckCellCssPx}px, ${metrics.deckCellCssPx}px)) !important;
                  justify-content: space-around !important;
                  justify-items: center !important;
                  align-items: center !important;
                  gap: 4px !important;
                  width: 100% !important;
                  min-width: 0 !important;
                  max-width: 100% !important;
                  overflow-x: hidden !important;
                }

                .omfetrab button._button.item {
                  width: ${metrics.deckCellCssPx}px !important;
                  min-width: 0 !important;
                  max-width: ${metrics.deckCellCssPx}px !important;
                  height: ${metrics.deckCellCssPx}px !important;
                  min-height: ${metrics.deckCellCssPx}px !important;
                  padding: 4px !important;
                  box-sizing: border-box !important;
                }

                .omfetrab button._button.item > .emoji {
                  width: ${metrics.deckEmojiCssPx}px !important;
                  max-width: ${metrics.deckEmojiCssPx}px !important;
                  height: ${metrics.deckEmojiCssPx}px !important;
                  max-height: ${metrics.deckEmojiCssPx}px !important;
                  font-size: ${metrics.deckEmojiCssPx}px !important;
                  object-fit: contain !important;
                }

                @media (max-width: 420px) {
                  .omfetrab {
                    width: calc(100dvw - 12px - env(safe-area-inset-left, 0px) - env(safe-area-inset-right, 0px)) !important;
                    max-width: calc(100dvw - 12px - env(safe-area-inset-left, 0px) - env(safe-area-inset-right, 0px)) !important;
                  }

                  .omfetrab :has(> button._button.item) {
                    justify-content: space-evenly !important;
                    column-gap: 2px !important;
                  }
                }
              `;
            })();
            """.trimIndent(),
            null,
        )
    }
}
