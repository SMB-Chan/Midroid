package dev.midroid.app.web

import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionScalePolicyTest {
    @Test
    fun keepsDefaultReactionsAtComfortableTouchTarget() {
        assertEquals(
            ReactionScaleMetrics(52, 200, 8),
            ReactionScalePolicy.forTextZoom(100),
        )
    }

    @Test
    fun scalesReactionsWithLargerTextModes() {
        assertEquals(
            ReactionScaleMetrics(55, 210, 8),
            ReactionScalePolicy.forTextZoom(115),
        )
        assertEquals(
            ReactionScaleMetrics(58, 220, 9),
            ReactionScalePolicy.forTextZoom(130),
        )
        assertEquals(
            ReactionScaleMetrics(62, 230, 10),
            ReactionScalePolicy.forTextZoom(145),
        )
    }

    @Test
    fun autoZoomBetweenStepsUsesNextComfortableBucket() {
        assertEquals(
            ReactionScaleMetrics(55, 210, 8),
            ReactionScalePolicy.forTextZoom(117),
        )
    }
}
