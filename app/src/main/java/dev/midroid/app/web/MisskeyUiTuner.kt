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
              const pickerRoot = supportsHas
                ? ':is(.omfetrab, [role="dialog"]:has(button._button.item), [class*="emoji"]:has(button._button.item))'
                : '.omfetrab';

              const markReactionGraphic = (container, marker) => {
                if (!(container instanceof HTMLElement)) return null;
                const graphic = container.querySelector(
                  '[style*="object-fit: contain"], img[alt^=":"], img[src*="/emoji/"], img[src*="emoji"]'
                );
                if (graphic instanceof HTMLElement) {
                  graphic.dataset[marker] = '1';
                  return graphic;
                }
                return null;
              };

              const markNotifications = () => {
                document.querySelectorAll('header time').forEach((time) => {
                  const header = time.closest('header');
                  if (!(header instanceof HTMLElement)) return;

                  const tail = header.parentElement;
                  const root = tail?.parentElement;
                  if (!(tail instanceof HTMLElement) || !(root instanceof HTMLElement)) return;
                  if (root.children.length !== 2) return;
                  if (root.lastElementChild !== tail || tail.firstElementChild !== header) return;

                  const head = root.firstElementChild;
                  if (!(head instanceof HTMLElement)) return;
                  if (head.children.length < 1 || head.children.length > 2) return;

                  root.dataset.midroidNotification = '1';
                  head.dataset.midroidNotificationHead = '1';
                  tail.dataset.midroidNotificationTail = '1';

                  const icon = head.firstElementChild;
                  if (icon instanceof HTMLElement) {
                    icon.dataset.midroidNotificationIcon = '1';
                    const groupGlyph = icon.querySelector('i.ti-plus, i.ti-heart, i.ti-repeat');
                    if (groupGlyph instanceof HTMLElement) {
                      groupGlyph.dataset.midroidNotificationGroupGlyph = '1';
                    }
                  }

                  const subIcon = head.children.item(1);
                  if (subIcon instanceof HTMLElement) {
                    subIcon.dataset.midroidNotificationSubicon = '1';
                    const reactionGraphic = markReactionGraphic(
                      subIcon,
                      'midroidNotificationReactionGraphic',
                    );
                    if (reactionGraphic) {
                      head.dataset.midroidNotificationReactionHead = '1';
                      subIcon.dataset.midroidNotificationReaction = '1';
                    } else {
                      delete subIcon.dataset.midroidNotificationReaction;
                    }
                  }

                  tail.querySelectorAll('div').forEach((item) => {
                    if (!(item instanceof HTMLElement) || item.children.length !== 2) return;
                    const avatar = item.children.item(0);
                    const reaction = item.children.item(1);
                    if (!(avatar instanceof HTMLElement) || !(reaction instanceof HTMLElement)) return;

                    const avatarImage = avatar.matches('img') ? avatar : avatar.querySelector('img');
                    if (!(avatarImage instanceof HTMLElement)) return;

                    const reactionGraphic = markReactionGraphic(
                      reaction,
                      'midroidGroupedReactionGraphic',
                    );
                    if (!reactionGraphic) return;

                    item.dataset.midroidGroupedReactionItem = '1';
                    avatar.dataset.midroidGroupedReactionAvatar = '1';
                    reaction.dataset.midroidGroupedReaction = '1';
                  });
                });
              };

              window.__midroidNotificationScan = markNotifications;
              if (!window.__midroidNotificationObserver) {
                let scanScheduled = false;
                const scheduleScan = () => {
                  if (scanScheduled) return;
                  scanScheduled = true;
                  window.requestAnimationFrame(() => {
                    scanScheduled = false;
                    window.__midroidNotificationScan?.();
                  });
                };
                window.__midroidNotificationObserver = new MutationObserver(scheduleScan);
                window.__midroidNotificationObserver.observe(document.documentElement, {
                  childList: true,
                  subtree: true,
                });
              }
              markNotifications();

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

              const notificationReadability = `
                [data-midroid-notification="1"] {
                  font-size: ${metrics.notificationFontPercent}% !important;
                  align-items: flex-start !important;
                  overflow: visible !important;
                }

                [data-midroid-notification-head="1"] {
                  width: ${metrics.notificationAvatarCssPx}px !important;
                  min-width: ${metrics.notificationAvatarCssPx}px !important;
                  max-width: ${metrics.notificationAvatarCssPx}px !important;
                  height: ${metrics.notificationAvatarCssPx}px !important;
                  min-height: ${metrics.notificationAvatarCssPx}px !important;
                  max-height: ${metrics.notificationAvatarCssPx}px !important;
                  overflow: visible !important;
                }

                [data-midroid-notification-icon="1"] {
                  width: 100% !important;
                  min-width: 100% !important;
                  max-width: 100% !important;
                  height: 100% !important;
                  min-height: 100% !important;
                  max-height: 100% !important;
                }

                [data-midroid-notification-group-glyph="1"] {
                  font-size: ${metrics.notificationGroupSymbolCssPx}px !important;
                  line-height: 1 !important;
                }

                [data-midroid-notification-subicon="1"]:not([data-midroid-notification-reaction="1"]) {
                  width: ${metrics.notificationStatusIconCssPx}px !important;
                  min-width: ${metrics.notificationStatusIconCssPx}px !important;
                  max-width: ${metrics.notificationStatusIconCssPx}px !important;
                  height: ${metrics.notificationStatusIconCssPx}px !important;
                  min-height: ${metrics.notificationStatusIconCssPx}px !important;
                  max-height: ${metrics.notificationStatusIconCssPx}px !important;
                  line-height: ${metrics.notificationStatusIconCssPx}px !important;
                  right: -4px !important;
                  bottom: -4px !important;
                  font-size: ${metrics.notificationStatusIconCssPx / 2}px !important;
                  box-shadow: 0 0 0 2px var(--MI_THEME-panel) !important;
                }

                [data-midroid-notification-head="1"][data-midroid-notification-reaction-head="1"] {
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

                [data-midroid-notification-head="1"][data-midroid-notification-reaction-head="1"]
                  > [data-midroid-notification-icon="1"] {
                  flex: 0 0 ${metrics.notificationAvatarCssPx}px !important;
                  width: ${metrics.notificationAvatarCssPx}px !important;
                  min-width: ${metrics.notificationAvatarCssPx}px !important;
                  max-width: ${metrics.notificationAvatarCssPx}px !important;
                  height: ${metrics.notificationAvatarCssPx}px !important;
                  min-height: ${metrics.notificationAvatarCssPx}px !important;
                  max-height: ${metrics.notificationAvatarCssPx}px !important;
                }

                [data-midroid-notification-reaction="1"] {
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

                [data-midroid-notification-reaction-graphic="1"] {
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

                [data-midroid-grouped-reaction-item="1"] {
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
                  margin-top: 10px !important;
                  margin-right: 12px !important;
                  overflow: visible !important;
                }

                [data-midroid-grouped-reaction-avatar="1"] {
                  flex: 0 0 ${metrics.notificationGroupAvatarCssPx}px !important;
                  width: ${metrics.notificationGroupAvatarCssPx}px !important;
                  min-width: ${metrics.notificationGroupAvatarCssPx}px !important;
                  max-width: ${metrics.notificationGroupAvatarCssPx}px !important;
                  height: ${metrics.notificationGroupAvatarCssPx}px !important;
                  min-height: ${metrics.notificationGroupAvatarCssPx}px !important;
                  max-height: ${metrics.notificationGroupAvatarCssPx}px !important;
                }

                [data-midroid-grouped-reaction="1"] {
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

                [data-midroid-grouped-reaction-graphic="1"] {
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
              `;

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
