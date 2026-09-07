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
            )
            ReactionScale.LARGE -> ReactionScaleMetrics(
                buttonHeightCssPx = 60,
                buttonFontPercent = 230,
                horizontalPaddingCssPx = 10,
                noteEmojiCssPx = 44,
                deckCellCssPx = 56,
                deckEmojiCssPx = 40,
                notificationEmojiCssPx = 30,
            )
            ReactionScale.EXTRA_LARGE -> ReactionScaleMetrics(
                buttonHeightCssPx = 68,
                buttonFontPercent = 260,
                horizontalPaddingCssPx = 12,
                noteEmojiCssPx = 52,
                deckCellCssPx = 64,
                deckEmojiCssPx = 48,
                notificationEmojiCssPx = 36,
            )
        }
    }
}
