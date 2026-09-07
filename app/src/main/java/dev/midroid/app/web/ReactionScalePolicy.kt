package dev.midroid.app.web

data class ReactionScaleMetrics(
    val buttonHeightCssPx: Int,
    val buttonFontPercent: Int,
    val horizontalPaddingCssPx: Int,
)

object ReactionScalePolicy {
    fun forTextZoom(textZoomPercent: Int): ReactionScaleMetrics {
        return when {
            textZoomPercent >= 145 -> ReactionScaleMetrics(
                buttonHeightCssPx = 62,
                buttonFontPercent = 230,
                horizontalPaddingCssPx = 10,
            )
            textZoomPercent >= 130 -> ReactionScaleMetrics(
                buttonHeightCssPx = 58,
                buttonFontPercent = 220,
                horizontalPaddingCssPx = 9,
            )
            textZoomPercent >= 115 -> ReactionScaleMetrics(
                buttonHeightCssPx = 55,
                buttonFontPercent = 210,
                horizontalPaddingCssPx = 8,
            )
            else -> ReactionScaleMetrics(
                buttonHeightCssPx = 52,
                buttonFontPercent = 200,
                horizontalPaddingCssPx = 8,
            )
        }
    }
}
