package dev.midroid.app.web

import dev.midroid.app.config.ReactionScale

data class ReactionScaleMetrics(
    val buttonHeightCssPx: Int,
    val buttonFontPercent: Int,
    val horizontalPaddingCssPx: Int,
    val deckCellCssPx: Int,
    val deckEmojiCssPx: Int,
)

object ReactionScalePolicy {
    fun forScale(scale: ReactionScale): ReactionScaleMetrics {
        return when (scale) {
            ReactionScale.STANDARD -> ReactionScaleMetrics(
                buttonHeightCssPx = 52,
                buttonFontPercent = 200,
                horizontalPaddingCssPx = 8,
                deckCellCssPx = 48,
                deckEmojiCssPx = 32,
            )
            ReactionScale.LARGE -> ReactionScaleMetrics(
                buttonHeightCssPx = 60,
                buttonFontPercent = 230,
                horizontalPaddingCssPx = 10,
                deckCellCssPx = 56,
                deckEmojiCssPx = 40,
            )
            ReactionScale.EXTRA_LARGE -> ReactionScaleMetrics(
                buttonHeightCssPx = 68,
                buttonFontPercent = 260,
                horizontalPaddingCssPx = 12,
                deckCellCssPx = 64,
                deckEmojiCssPx = 48,
            )
        }
    }
}
