package dev.midroid.app.web

import android.webkit.WebView
import dev.midroid.app.config.ReactionScale

class MisskeyUiTuner {
    fun applyReactionScale(webView: WebView, scale: ReactionScale) {
        val metrics = ReactionScalePolicy.forScale(scale)
        // Structural selector for a notification row. Interpolated by Kotlin (contains no
        // `$`), so JS below can reference it without relying on generated CSS-module names.
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

                /* Timeline reactions are height-driven. A fixed square made wide custom emoji
                   shrink until their text was tiny. Preserve their intrinsic aspect ratio and
                   only cap exceptionally wide artwork. */
                button._button:has(> [style*="pointer-events: none"] + span)
                  > [style*="pointer-events: none"] {
                  display: inline-block !important;
                  flex: 0 1 auto !important;
                  width: auto !important;
                  min-width: 0 !important;
                  max-width: ${metrics.noteEmojiMaxWidthCssPx}px !important;
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

              // Notification list readability. MkNotification always renders a head followed by
              // a tail whose first child is a header. The head contains the primary icon/avatar
              // followed by a sub-icon. Reaction sub-icons are moved below the avatar and wide
              // reaction artwork keeps its aspect ratio. Grouped reaction items use the same
              // vertical non-overlapping layout. On WebViews without :has() support we fail
              // closed and leave Misskey's stock geometry untouched.
              const notificationReadability = supportsHas ? `
                ${notifRoot} {
                  font-size: ${metrics.notificationFontPercent}% !important;
                  align-items: flex-start !important;
                }

                ${notifRoot} > :first-child {
                  width: ${metrics.notificationAvatarCssPx}px !important;
                  min-width: ${metrics.notificationAvatarCssPx}px !important;
                  max-width: ${metrics.notificationAvatarCssPx}px !important;
                  height: ${metrics.notificationAvatarCssPx}px !important;
                  min-height: ${metrics.notificationAvatarCssPx}px !important;
                  max-height: ${metrics.notificationAvatarCssPx}px !important;
                }

                ${notifRoot} > :first-child > :first-child {
                  width: 100% !important;
                  height: 100% !important;
                  max-width: 100% !important;
                  max-height: 100% !important;
                }

                /* Grouped + / heart / renote header symbols use a fixed 15px glyph upstream.
                   Scale the symbol with the Midroid mode and let the colored circle fill head. */
                ${notifRoot} > :first-child > div:first-child {
                  width: 100% !important;
                  height: 100% !important;
                  max-width: 100% !important;
                  max-height: 100% !important;
                  font-size: ${metrics.notificationGroupSymbolCssPx}px !important;
                }

                /* Normal status sub-icons (login, token, renote, reply, etc.) are 20px upstream.
                   Enlarge them independently from reaction artwork. Empty sub-icons remain hidden
                   because Misskey's :empty rule still applies. */
                ${notifRoot} > :first-child > :nth-child(2) {
                  width: ${metrics.notificationStatusIconCssPx}px !important;
                  min-width: ${metrics.notificationStatusIconCssPx}px !important;
                  max-width: ${metrics.notificationStatusIconCssPx}px !important;
                  height: ${metrics.notificationStatusIconCssPx}px !important;
                  min-height: ${metrics.notificationStatusIconCssPx}px !important;
                  max-height: ${metrics.notificationStatusIconCssPx}px !important;
                  line-height: ${metrics.notificationStatusIconCssPx}px !important;
                  right: -3px !important;
                  bottom: -3px !important;
                  font-size: ${metrics.notificationStatusIconCssPx / 2}px !important;
                  box-shadow: 0 0 0 2px var(--MI_THEME-panel) !important;
                }

                /* A reaction notification is the head whose second child contains MkReactionIcon.
                   Stack reaction below avatar so even a wide emoji never covers the face or steals
                   horizontal space from the notification text more than its own natural width. */
                ${notifRoot} > :first-child:has(
                  > :nth-child(2) > [style*="object-fit: contain"]
                ) {
                  display: inline-flex !important;
                  flex-direction: column !important;
                  align-items: center !important;
                  width: max-content !important;
                  min-width: ${metrics.notificationAvatarCssPx}px !important;
                  max-width: ${metrics.notificationReactionMaxWidthCssPx}px !important;
                  height: auto !important;
                  min-height: ${metrics.notificationAvatarCssPx + metrics.notificationReactionGapCssPx + metrics.notificationReactionCssPx}px !important;
                  max-height: none !important;
                  margin-right: 10px !important;
                  overflow: visible !important;
                }

                ${notifRoot} > :first-child:has(
                  > :nth-child(2) > [style*="object-fit: contain"]
                ) > :first-child {
                  flex: 0 0 ${metrics.notificationAvatarCssPx}px !important;
                  width: ${metrics.notificationAvatarCssPx}px !important;
                  min-width: ${metrics.notificationAvatarCssPx}px !important;
                  max-width: ${metrics.notificationAvatarCssPx}px !important;
                  height: ${metrics.notificationAvatarCssPx}px !important;
                  min-height: ${metrics.notificationAvatarCssPx}px !important;
                  max-height: ${metrics.notificationAvatarCssPx}px !important;
                }

                ${notifRoot} > :first-child:has(
                  > :nth-child(2) > [style*="object-fit: contain"]
                ) > :nth-child(2) {
                  position: static !important;
                  inset: auto !important;
                  display: inline-flex !important;
                  flex: 0 1 auto !important;
                  align-items: center !important;
                  justify-content: center !important;
                  width: auto !important;
                  min-width: 0 !important;
                  max-width: ${metrics.notificationReactionMaxWidthCssPx}px !important;
                  height: ${metrics.notificationReactionCssPx}px !important;
                  min-height: ${metrics.notificationReactionCssPx}px !important;
                  max-height: ${metrics.notificationReactionCssPx}px !important;
                  margin: ${metrics.notificationReactionGapCssPx}px 0 0 0 !important;
                  padding: 0 !important;
                  line-height: ${metrics.notificationReactionCssPx}px !important;
                  border-radius: 0 !important;
                  background: transparent !important;
                  box-shadow: none !important;
                  overflow: visible !important;
                }

                ${notifRoot} > :first-child:has(
                  > :nth-child(2) > [style*="object-fit: contain"]
                ) > :nth-child(2) > [style*="object-fit: contain"] {
                  display: block !important;
                  width: auto !important;
                  min-width: 0 !important;
                  max-width: ${metrics.notificationReactionMaxWidthCssPx}px !important;
                  height: ${metrics.notificationReactionCssPx}px !important;
                  min-height: ${metrics.notificationReactionCssPx}px !important;
                  max-height: ${metrics.notificationReactionCssPx}px !important;
                  font-size: ${metrics.notificationReactionCssPx}px !important;
                  line-height: 1 !important;
                  object-fit: contain !important;
                }

                /* reaction:grouped item = first child avatar + second child reaction wrapper.
                   Select by child order instead of generated module class names. */
                ${notifRoot} > div:last-child
                  div:has(> :nth-child(2) > [style*="object-fit: contain"]) {
                  display: inline-flex !important;
                  flex-direction: column !important;
                  align-items: center !important;
                  justify-content: flex-start !important;
                  vertical-align: top !important;
                  position: relative !important;
                  width: max-content !important;
                  min-width: ${metrics.notificationGroupAvatarCssPx}px !important;
                  max-width: ${metrics.notificationReactionMaxWidthCssPx}px !important;
                  height: auto !important;
                  min-height: ${metrics.notificationGroupAvatarCssPx + metrics.notificationReactionGapCssPx + metrics.notificationReactionCssPx}px !important;
                  max-height: none !important;
                  margin-top: 8px !important;
                  margin-right: 12px !important;
                  overflow: visible !important;
                }

                ${notifRoot} > div:last-child
                  div:has(> :nth-child(2) > [style*="object-fit: contain"])
                  > :first-child {
                  flex: 0 0 ${metrics.notificationGroupAvatarCssPx}px !important;
                  width: ${metrics.notificationGroupAvatarCssPx}px !important;
                  min-width: ${metrics.notificationGroupAvatarCssPx}px !important;
                  max-width: ${metrics.notificationGroupAvatarCssPx}px !important;
                  height: ${metrics.notificationGroupAvatarCssPx}px !important;
                  min-height: ${metrics.notificationGroupAvatarCssPx}px !important;
                  max-height: ${metrics.notificationGroupAvatarCssPx}px !important;
                }

                ${notifRoot} > div:last-child
                  div:has(> :nth-child(2) > [style*="object-fit: contain"])
                  > :nth-child(2) {
                  position: static !important;
                  inset: auto !important;
                  display: inline-flex !important;
                  flex: 0 1 auto !important;
                  align-items: center !important;
                  justify-content: center !important;
                  width: auto !important;
                  min-width: 0 !important;
                  max-width: ${metrics.notificationReactionMaxWidthCssPx}px !important;
                  height: ${metrics.notificationReactionCssPx}px !important;
                  min-height: ${metrics.notificationReactionCssPx}px !important;
                  max-height: ${metrics.notificationReactionCssPx}px !important;
                  margin: ${metrics.notificationReactionGapCssPx}px 0 0 0 !important;
                  padding: 0 !important;
                  border-radius: 0 !important;
                  background: transparent !important;
                  box-shadow: none !important;
                  overflow: visible !important;
                }

                ${notifRoot} > div:last-child
                  div:has(> :nth-child(2) > [style*="object-fit: contain"])
                  > :nth-child(2) > [style*="object-fit: contain"] {
                  display: block !important;
                  width: auto !important;
                  min-width: 0 !important;
                  max-width: ${metrics.notificationReactionMaxWidthCssPx}px !important;
                  height: ${metrics.notificationReactionCssPx}px !important;
                  min-height: ${metrics.notificationReactionCssPx}px !important;
                  max-height: ${metrics.notificationReactionCssPx}px !important;
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
