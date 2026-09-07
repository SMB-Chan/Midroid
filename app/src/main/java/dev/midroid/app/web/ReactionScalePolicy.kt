package dev.midroid.app.web

import dev.midroid.app.config.ReactionScale

data class ReactionScaleMetrics(
    val buttonHeightCssPx: Int,
    val buttonFontPercent: Int,
    val horizontalPaddingCssPx: Int,
)

object ReactionScalePolicy {
    fun forScale(scale: ReactionScale): ReactionScaleMetrics {
        return when (scale) {
            ReactionScale.STANDARD -> ReactionScaleMetrics(
                buttonHeightCssPx = 52,
                buttonFontPercent = 200,
                horizontalPaddingCssPx = 8,
            )
            ReactionScale.LARGE -> ReactionScaleMetrics(
                buttonHeightCssPx = 60,
                buttonFontPercent = 230,
                horizontalPaddingCssPx = 10,
            )
            ReactionScale.EXTRA_LARGE -> ReactionScaleMetrics(
                buttonHeightCssPx = 68,
                buttonFontPercent = 260,
                horizontalPaddingCssPx = 12,
            )
        }
    }
}
