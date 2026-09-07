package dev.midroid.app.web

import dev.midroid.app.config.ReactionScale
import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionScalePolicyTest {
    @Test
    fun standardUsesComfortableTouchTarget() {
        assertEquals(
            ReactionScaleMetrics(52, 200, 8, 48, 32),
            ReactionScalePolicy.forScale(ReactionScale.STANDARD),
        )
    }

    @Test
    fun largeAndExtraLargeAreClearlySeparated() {
        assertEquals(
            ReactionScaleMetrics(60, 230, 10, 56, 40),
            ReactionScalePolicy.forScale(ReactionScale.LARGE),
        )
        assertEquals(
            ReactionScaleMetrics(68, 260, 12, 64, 48),
            ReactionScalePolicy.forScale(ReactionScale.EXTRA_LARGE),
        )
    }

    @Test
    fun reactionDeckCellsGrowWithDisplayMode() {
        val standard = ReactionScalePolicy.forScale(ReactionScale.STANDARD)
        val large = ReactionScalePolicy.forScale(ReactionScale.LARGE)
        val extraLarge = ReactionScalePolicy.forScale(ReactionScale.EXTRA_LARGE)

        assertEquals(true, standard.deckCellCssPx < large.deckCellCssPx)
        assertEquals(true, large.deckCellCssPx < extraLarge.deckCellCssPx)
        assertEquals(true, standard.deckEmojiCssPx < large.deckEmojiCssPx)
        assertEquals(true, large.deckEmojiCssPx < extraLarge.deckEmojiCssPx)
    }

    @Test
    fun missingPreferenceDefaultsToLarge() {
        assertEquals(ReactionScale.LARGE, ReactionScale.fromKey(null))
        assertEquals(ReactionScale.LARGE, ReactionScale.fromKey("unknown"))
    }

    @Test
    fun persistedKeysResolveToExpectedModes() {
        assertEquals(ReactionScale.STANDARD, ReactionScale.fromKey("standard"))
        assertEquals(ReactionScale.LARGE, ReactionScale.fromKey("large"))
        assertEquals(ReactionScale.EXTRA_LARGE, ReactionScale.fromKey("extra_large"))
    }
}
