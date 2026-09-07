package dev.midroid.app.web

import dev.midroid.app.config.ReactionScale

data class ReactionScaleMetrics(
    val buttonHeightCssPx: Int,
    val buttonFontPercent: Int,
    val horizontalPaddingCssPx: Int,
    val noteEmojiCssPx: Int,
    val deckCellCssPx: Int,
    val deckEmojiCssPx: Int,
    val notificationReactionCssPx: Int,
    val notificationAvatarCssPx: Int,
    val notificationGroupAvatarCssPx: Int,
    val notificationReactionGapCssPx: Int,
    val notificationFontPercent: Int,
) {
    val notificationHeadLaneCssPx: Int
        get() = notificationAvatarCssPx + notificationReactionGapCssPx + notificationReactionCssPx

    val notificationGroupLaneCssPx: Int
        get() = notificationGroupAvatarCssPx + notificationReactionGapCssPx + notificationReactionCssPx
}

object ReactionScalePolicy {
    fun forScale(scale: ReactionScale): ReactionScaleMetrics {
        return when (scale) {
            ReactionScale.STANDARD -> ReactionScaleMetrics(
                buttonHeightCssPx = 52,
                buttonFontPercent = 200,
                horizontalPaddingCssPx = 8,
                noteEmojiCssPx = 36,
                deckCellCssPx = 48,
                deckEmojiCssPx = 32,
                notificationReactionCssPx = 32,
                notificationAvatarCssPx = 44,
                notificationGroupAvatarCssPx = 44,
                notificationReactionGapCssPx = 6,
                notificationFontPercent = 100,
            )
            ReactionScale.LARGE -> ReactionScaleMetrics(
                buttonHeightCssPx = 60,
                buttonFontPercent = 230,
                horizontalPaddingCssPx = 10,
                noteEmojiCssPx = 44,
                deckCellCssPx = 56,
                deckEmojiCssPx = 40,
                notificationReactionCssPx = 40,
                notificationAvatarCssPx = 50,
                notificationGroupAvatarCssPx = 50,
                notificationReactionGapCssPx = 8,
                notificationFontPercent = 108,
            )
            ReactionScale.EXTRA_LARGE -> ReactionScaleMetrics(
                buttonHeightCssPx = 68,
                buttonFontPercent = 260,
                horizontalPaddingCssPx = 12,
                noteEmojiCssPx = 52,
                deckCellCssPx = 64,
                deckEmojiCssPx = 48,
                notificationReactionCssPx = 48,
                notificationAvatarCssPx = 58,
                notificationGroupAvatarCssPx = 58,
                notificationReactionGapCssPx = 8,
                notificationFontPercent = 116,
            )
        }
    }
}
