package dev.midroid.app.web

import dev.midroid.app.config.ReactionScale

data class ReactionScaleMetrics(
    val buttonHeightCssPx: Int,
    val buttonFontPercent: Int,
    val horizontalPaddingCssPx: Int,
    val noteEmojiCssPx: Int,
    val noteEmojiMaxWidthCssPx: Int,
    val deckCellCssPx: Int,
    val deckEmojiCssPx: Int,
    val notificationReactionCssPx: Int,
    val notificationReactionMaxWidthCssPx: Int,
    val notificationAvatarCssPx: Int,
    val notificationGroupAvatarCssPx: Int,
    val notificationStatusIconCssPx: Int,
    val notificationGroupSymbolCssPx: Int,
    val notificationReactionGapCssPx: Int,
    val notificationFontPercent: Int,
)

object ReactionScalePolicy {
    fun forScale(scale: ReactionScale): ReactionScaleMetrics {
        return when (scale) {
            ReactionScale.STANDARD -> ReactionScaleMetrics(
                buttonHeightCssPx = 52,
                buttonFontPercent = 200,
                horizontalPaddingCssPx = 8,
                noteEmojiCssPx = 40,
                noteEmojiMaxWidthCssPx = 160,
                deckCellCssPx = 48,
                deckEmojiCssPx = 32,
                notificationReactionCssPx = 36,
                notificationReactionMaxWidthCssPx = 108,
                notificationAvatarCssPx = 46,
                notificationGroupAvatarCssPx = 46,
                notificationStatusIconCssPx = 28,
                notificationGroupSymbolCssPx = 24,
                notificationReactionGapCssPx = 6,
                notificationFontPercent = 102,
            )
            ReactionScale.LARGE -> ReactionScaleMetrics(
                buttonHeightCssPx = 60,
                buttonFontPercent = 230,
                horizontalPaddingCssPx = 10,
                noteEmojiCssPx = 48,
                noteEmojiMaxWidthCssPx = 192,
                deckCellCssPx = 56,
                deckEmojiCssPx = 40,
                notificationReactionCssPx = 46,
                notificationReactionMaxWidthCssPx = 138,
                notificationAvatarCssPx = 54,
                notificationGroupAvatarCssPx = 54,
                notificationStatusIconCssPx = 34,
                notificationGroupSymbolCssPx = 30,
                notificationReactionGapCssPx = 8,
                notificationFontPercent = 110,
            )
            ReactionScale.EXTRA_LARGE -> ReactionScaleMetrics(
                buttonHeightCssPx = 68,
                buttonFontPercent = 260,
                horizontalPaddingCssPx = 12,
                noteEmojiCssPx = 58,
                noteEmojiMaxWidthCssPx = 232,
                deckCellCssPx = 64,
                deckEmojiCssPx = 48,
                notificationReactionCssPx = 58,
                notificationReactionMaxWidthCssPx = 174,
                notificationAvatarCssPx = 64,
                notificationGroupAvatarCssPx = 64,
                notificationStatusIconCssPx = 42,
                notificationGroupSymbolCssPx = 38,
                notificationReactionGapCssPx = 8,
                notificationFontPercent = 120,
            )
        }
    }
}
