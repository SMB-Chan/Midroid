package dev.midroid.app.web

import dev.midroid.app.config.ReactionScale

data class ReactionScaleMetrics(
    val buttonHeightCssPx: Int,
    val buttonFontPercent: Int,
    val horizontalPaddingCssPx: Int,
    val noteEmojiCssPx: Int,
    val deckCellCssPx: Int,
    val deckEmojiCssPx: Int,
    val notificationEmojiCssPx: Int,
    val notificationAvatarCssPx: Int,
    val notificationGroupItemCssPx: Int,
    val notificationFontPercent: Int,
)

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
                notificationEmojiCssPx = 24,
                notificationAvatarCssPx = 44,
                notificationGroupItemCssPx = 44,
                notificationFontPercent = 100,
            )
            ReactionScale.LARGE -> ReactionScaleMetrics(
                buttonHeightCssPx = 60,
                buttonFontPercent = 230,
                horizontalPaddingCssPx = 10,
                noteEmojiCssPx = 44,
                deckCellCssPx = 56,
                deckEmojiCssPx = 40,
                notificationEmojiCssPx = 30,
                notificationAvatarCssPx = 50,
                notificationGroupItemCssPx = 50,
                notificationFontPercent = 108,
            )
            ReactionScale.EXTRA_LARGE -> ReactionScaleMetrics(
                buttonHeightCssPx = 68,
                buttonFontPercent = 260,
                horizontalPaddingCssPx = 12,
                noteEmojiCssPx = 52,
                deckCellCssPx = 64,
                deckEmojiCssPx = 48,
                notificationEmojiCssPx = 36,
                notificationAvatarCssPx = 58,
                notificationGroupItemCssPx = 58,
                notificationFontPercent = 116,
            )
        }
    }
}
