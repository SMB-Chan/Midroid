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

              const supportsHas = !!window.CSS?.supports?.('selector(:has(*))');
              const pickerSelectors = [
                '.omfetrab',
                '[role="dialog"]:has(button._button.item)',
                '[class*="emoji"]:has(button._button.item)',
              ];
              const pickerRoot = supportsHas
                ? pickerSelectors.find((selector) => {
                    try { return document.querySelector(selector); } catch (_) { return false; }
                  }) ?? null
                : '.omfetrab';

              const hasRules = supportsHas ? `
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
                  display: inline-flex !important;
                  align-items: center !important;
                  justify-content: center !important;
                  flex: 0 0 ${metrics.noteEmojiCssPx}px !important;
                  width: ${metrics.noteEmojiCssPx}px !important;
                  min-width: ${metrics.noteEmojiCssPx}px !important;
                  max-width: ${metrics.noteEmojiCssPx}px !important;
                  height: ${metrics.noteEmojiCssPx}px !important;
                  min-height: ${metrics.noteEmojiCssPx}px !important;
                  max-height: ${metrics.noteEmojiCssPx}px !important;
                  font-size: ${metrics.noteEmojiCssPx}px !important;
                  line-height: 1 !important;
                  object-fit: contain !important;
                }

                button._button:has(> [style*="pointer-events: none"] + span)
                  > span:last-child:not([alt]) {
                  line-height: 1 !important;
                  font-size: 0.65em !important;
                  margin-left: 6px !important;
                }

                :is(div, span):has(> [style*="width: 100%"][style*="height: 100%"][style*="object-fit: contain"]) {
                  width: ${metrics.notificationEmojiCssPx}px !important;
                  min-width: ${metrics.notificationEmojiCssPx}px !important;
                  height: ${metrics.notificationEmojiCssPx}px !important;
                  min-height: ${metrics.notificationEmojiCssPx}px !important;
                  line-height: ${metrics.notificationEmojiCssPx}px !important;
                  overflow: visible !important;
                }
              ` : '';

              const notificationFallback = `
                [style*="width: 100%"][style*="height: 100%"][style*="object-fit: contain"] {
                  display: inline-flex !important;
                  align-items: center !important;
                  justify-content: center !important;
                  width: ${metrics.notificationEmojiCssPx}px !important;
                  min-width: ${metrics.notificationEmojiCssPx}px !important;
                  max-width: ${metrics.notificationEmojiCssPx}px !important;
                  height: ${metrics.notificationEmojiCssPx}px !important;
                  min-height: ${metrics.notificationEmojiCssPx}px !important;
                  max-height: ${metrics.notificationEmojiCssPx}px !important;
                  font-size: ${metrics.notificationEmojiCssPx}px !important;
                  line-height: 1 !important;
                  object-fit: contain !important;
                }
              `;

              let pickerRules = '';
              if (pickerRoot) {
                const root = pickerRoot;
                const gridRule = supportsHas
                  ? root + ` :has(> button._button.item) {
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
                    }`
                  : '';

                pickerRules = root + ` {
                    box-sizing: border-box !important;
                    width: min(100%, calc(100dvw - 16px - env(safe-area-inset-left, 0px) - env(safe-area-inset-right, 0px))) !important;
                    max-width: calc(100dvw - 16px - env(safe-area-inset-left, 0px) - env(safe-area-inset-right, 0px)) !important;
                    margin-left: auto !important;
                    margin-right: auto !important;
                    overflow-x: hidden !important;
                    overscroll-behavior-x: none !important;
                  }\n` +
                  root + ` * {
                    box-sizing: border-box !important;
                    max-width: 100%;
                  }\n` +
                  gridRule + '\n' +
                  root + ` button._button.item {
                    width: ${metrics.deckCellCssPx}px !important;
                    min-width: 0 !important;
                    max-width: ${metrics.deckCellCssPx}px !important;
                    height: ${metrics.deckCellCssPx}px !important;
                    min-height: ${metrics.deckCellCssPx}px !important;
                    padding: 4px !important;
                    box-sizing: border-box !important;
                  }\n` +
                  root + ` button._button.item > .emoji {
                    width: ${metrics.deckEmojiCssPx}px !important;
                    max-width: ${metrics.deckEmojiCssPx}px !important;
                    height: ${metrics.deckEmojiCssPx}px !important;
                    max-height: ${metrics.deckEmojiCssPx}px !important;
                    font-size: ${metrics.deckEmojiCssPx}px !important;
                    object-fit: contain !important;
                  }`;
              }

              style.textContent = hasRules + notificationFallback + pickerRules;
            })();
            """.trimIndent(),
            null,
        )
    }
}
