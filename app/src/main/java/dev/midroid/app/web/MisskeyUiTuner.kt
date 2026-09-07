package dev.midroid.app.web

import android.webkit.WebView
import dev.midroid.app.config.ReactionScale

class MisskeyUiTuner {
    fun applyReactionScale(webView: WebView, scale: ReactionScale) {
        val metrics = ReactionScalePolicy.forScale(scale)
        // Structural selector for a notification row. Interpolated by Kotlin (contains no
        // `$`), so JS below can reference it via `${'$'}{notifRoot}` without a JS variable.
        val notifRoot = "div:has(> div > header:has(time))"
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
              // Keep the legacy generated class as a compatibility fallback, but do not depend
              // on it exclusively. The structural selectors remain dormant until a matching
              // picker is present, so Misskey updates fail closed instead of reshaping the page.
              const pickerRoot = supportsHas
                ? ':is(.omfetrab, [role="dialog"]:has(button._button.item), [class*="emoji"]:has(button._button.item))'
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
              ` : '';

              // Notification list readability. Misskey uses generated CSS-module class names,
              // so these rules key off the stable MkNotification structure. Reaction badges are
              // deliberately removed from their original absolute overlay and placed in a
              // dedicated lane beside the avatar. This lets the reaction grow without covering
              // the user's face. On WebViews without :has() support we fail closed and leave the
              // stock Misskey notification geometry untouched.
              const notificationReadability = supportsHas ? `
                ${notifRoot} {
                  font-size: ${metrics.notificationFontPercent}% !important;
                  align-items: flex-start !important;
                }

                ${notifRoot} > :first-child {
                  width: ${metrics.notificationAvatarCssPx}px !important;
                  min-width: ${metrics.notificationAvatarCssPx}px !important;
                  height: ${metrics.notificationAvatarCssPx}px !important;
                  min-height: ${metrics.notificationAvatarCssPx}px !important;
                }

                ${notifRoot} > :first-child:has(
                  > :is(div, span):has(
                    > [style*="width: 100%"][style*="height: 100%"][style*="object-fit: contain"]
                  )
                ) {
                  display: flex !important;
                  flex-direction: row !important;
                  align-items: center !important;
                  width: ${metrics.notificationHeadLaneCssPx}px !important;
                  min-width: ${metrics.notificationHeadLaneCssPx}px !important;
                  max-width: ${metrics.notificationHeadLaneCssPx}px !important;
                  height: ${metrics.notificationAvatarCssPx}px !important;
                  min-height: ${metrics.notificationAvatarCssPx}px !important;
                  margin-right: 10px !important;
                }

                ${notifRoot} > :first-child:has(
                  > :is(div, span):has(
                    > [style*="width: 100%"][style*="height: 100%"][style*="object-fit: contain"]
                  )
                ) > :first-child {
                  flex: 0 0 ${metrics.notificationAvatarCssPx}px !important;
                  width: ${metrics.notificationAvatarCssPx}px !important;
                  min-width: ${metrics.notificationAvatarCssPx}px !important;
                  max-width: ${metrics.notificationAvatarCssPx}px !important;
                  height: ${metrics.notificationAvatarCssPx}px !important;
                  min-height: ${metrics.notificationAvatarCssPx}px !important;
                  max-height: ${metrics.notificationAvatarCssPx}px !important;
                }

                ${notifRoot} > :first-child
                  > :is(div, span):has(
                    > [style*="width: 100%"][style*="height: 100%"][style*="object-fit: contain"]
                  ) {
                  position: static !important;
                  inset: auto !important;
                  flex: 0 0 ${metrics.notificationReactionCssPx}px !important;
                  width: ${metrics.notificationReactionCssPx}px !important;
                  min-width: ${metrics.notificationReactionCssPx}px !important;
                  max-width: ${metrics.notificationReactionCssPx}px !important;
                  height: ${metrics.notificationReactionCssPx}px !important;
                  min-height: ${metrics.notificationReactionCssPx}px !important;
                  max-height: ${metrics.notificationReactionCssPx}px !important;
                  margin: 0 0 0 ${metrics.notificationReactionGapCssPx}px !important;
                  padding: 0 !important;
                  line-height: ${metrics.notificationReactionCssPx}px !important;
                  border-radius: 0 !important;
                  background: transparent !important;
                  box-shadow: none !important;
                  overflow: visible !important;
                }

                ${notifRoot} > :first-child
                  > :is(div, span):has(
                    > [style*="width: 100%"][style*="height: 100%"][style*="object-fit: contain"]
                  )
                  > [style*="width: 100%"][style*="height: 100%"][style*="object-fit: contain"] {
                  display: block !important;
                  width: 100% !important;
                  min-width: 100% !important;
                  max-width: 100% !important;
                  height: 100% !important;
                  min-height: 100% !important;
                  max-height: 100% !important;
                  font-size: ${metrics.notificationReactionCssPx}px !important;
                  line-height: 1 !important;
                  object-fit: contain !important;
                }

                ${notifRoot} > div:last-child
                  div:has(
                    > div > [style*="width: 100%"][style*="height: 100%"][style*="object-fit: contain"]
                  ) {
                  display: inline-flex !important;
                  align-items: center !important;
                  vertical-align: top !important;
                  width: ${metrics.notificationGroupLaneCssPx}px !important;
                  min-width: ${metrics.notificationGroupLaneCssPx}px !important;
                  max-width: ${metrics.notificationGroupLaneCssPx}px !important;
                  height: ${metrics.notificationGroupAvatarCssPx}px !important;
                  min-height: ${metrics.notificationGroupAvatarCssPx}px !important;
                  max-height: ${metrics.notificationGroupAvatarCssPx}px !important;
                  overflow: visible !important;
                }

                ${notifRoot} > div:last-child
                  div:has(
                    > div > [style*="width: 100%"][style*="height: 100%"][style*="object-fit: contain"]
                  ) > :first-child {
                  flex: 0 0 ${metrics.notificationGroupAvatarCssPx}px !important;
                  width: ${metrics.notificationGroupAvatarCssPx}px !important;
                  min-width: ${metrics.notificationGroupAvatarCssPx}px !important;
                  max-width: ${metrics.notificationGroupAvatarCssPx}px !important;
                  height: ${metrics.notificationGroupAvatarCssPx}px !important;
                  min-height: ${metrics.notificationGroupAvatarCssPx}px !important;
                  max-height: ${metrics.notificationGroupAvatarCssPx}px !important;
                }

                ${notifRoot} > div:last-child
                  div:has(
                    > div > [style*="width: 100%"][style*="height: 100%"][style*="object-fit: contain"]
                  )
                  > div:has(
                    > [style*="width: 100%"][style*="height: 100%"][style*="object-fit: contain"]
                  ) {
                  position: static !important;
                  inset: auto !important;
                  flex: 0 0 ${metrics.notificationReactionCssPx}px !important;
                  width: ${metrics.notificationReactionCssPx}px !important;
                  min-width: ${metrics.notificationReactionCssPx}px !important;
                  max-width: ${metrics.notificationReactionCssPx}px !important;
                  height: ${metrics.notificationReactionCssPx}px !important;
                  min-height: ${metrics.notificationReactionCssPx}px !important;
                  max-height: ${metrics.notificationReactionCssPx}px !important;
                  margin: 0 0 0 ${metrics.notificationReactionGapCssPx}px !important;
                  padding: 0 !important;
                  border-radius: 0 !important;
                  background: transparent !important;
                  box-shadow: none !important;
                  overflow: visible !important;
                }

                ${notifRoot} > div:last-child
                  div:has(
                    > div > [style*="width: 100%"][style*="height: 100%"][style*="object-fit: contain"]
                  )
                  > div:has(
                    > [style*="width: 100%"][style*="height: 100%"][style*="object-fit: contain"]
                  )
                  > [style*="width: 100%"][style*="height: 100%"][style*="object-fit: contain"] {
                  display: block !important;
                  width: 100% !important;
                  min-width: 100% !important;
                  max-width: 100% !important;
                  height: 100% !important;
                  min-height: 100% !important;
                  max-height: 100% !important;
                  font-size: ${metrics.notificationReactionCssPx}px !important;
                  line-height: 1 !important;
                  object-fit: contain !important;
                }
              ` : '';

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

              const pickerRules = root + ` {
                  box-sizing: border-box !important;
                  width: min(100%, calc(100dvw - 16px - env(safe-area-inset-left, 0px) - env(safe-area-inset-right, 0px))) !important;
                  max-width: calc(100dvw - 16px - env(safe-area-inset-left, 0px) - env(safe-area-inset-right, 0px)) !important;
                  margin-left: auto !important;
                  margin-right: auto !important;
                  overflow-x: hidden !important;
                  overscroll-behavior-x: none !important;
                }
              ` + root + ` * {
                  box-sizing: border-box !important;
                  max-width: 100%;
                }
              ` + gridRule + `
              ` + root + ` button._button.item {
                  width: ${metrics.deckCellCssPx}px !important;
                  min-width: 0 !important;
                  max-width: ${metrics.deckCellCssPx}px !important;
                  height: ${metrics.deckCellCssPx}px !important;
                  min-height: ${metrics.deckCellCssPx}px !important;
                  padding: 4px !important;
                  box-sizing: border-box !important;
                }
              ` + root + ` button._button.item > .emoji {
                  width: ${metrics.deckEmojiCssPx}px !important;
                  max-width: ${metrics.deckEmojiCssPx}px !important;
                  height: ${metrics.deckEmojiCssPx}px !important;
                  max-height: ${metrics.deckEmojiCssPx}px !important;
                  font-size: ${metrics.deckEmojiCssPx}px !important;
                  object-fit: contain !important;
                }`;

              style.textContent = hasRules + notificationReadability + pickerRules;
            })();
            """.trimIndent(),
            null,
        )
    }
}
