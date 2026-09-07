package dev.midroid.app.web

import dev.midroid.app.config.ReactionScale
import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionScalePolicyTest {
    @Test
    fun standardUsesComfortableTouchTarget() {
        assertEquals(
            ReactionScaleMetrics(52, 200, 8, 36, 48, 32, 32, 44, 44, 6, 100),
            ReactionScalePolicy.forScale(ReactionScale.STANDARD),
        )
    }

    @Test
    fun largeAndExtraLargeAreClearlySeparated() {
        assertEquals(
            ReactionScaleMetrics(60, 230, 10, 44, 56, 40, 40, 50, 50, 8, 108),
            ReactionScalePolicy.forScale(ReactionScale.LARGE),
        )
        assertEquals(
            ReactionScaleMetrics(68, 260, 12, 52, 64, 48, 48, 58, 58, 8, 116),
            ReactionScalePolicy.forScale(ReactionScale.EXTRA_LARGE),
        )
    }

    @Test
    fun allReactionSurfacesGrowWithDisplayMode() {
        val standard = ReactionScalePolicy.forScale(ReactionScale.STANDARD)
        val large = ReactionScalePolicy.forScale(ReactionScale.LARGE)
        val extraLarge = ReactionScalePolicy.forScale(ReactionScale.EXTRA_LARGE)

        assertEquals(true, standard.noteEmojiCssPx < large.noteEmojiCssPx)
        assertEquals(true, large.noteEmojiCssPx < extraLarge.noteEmojiCssPx)
        assertEquals(true, standard.deckCellCssPx < large.deckCellCssPx)
        assertEquals(true, large.deckCellCssPx < extraLarge.deckCellCssPx)
        assertEquals(true, standard.deckEmojiCssPx < large.deckEmojiCssPx)
        assertEquals(true, large.deckEmojiCssPx < extraLarge.deckEmojiCssPx)
        assertEquals(true, standard.notificationReactionCssPx < large.notificationReactionCssPx)
        assertEquals(true, large.notificationReactionCssPx < extraLarge.notificationReactionCssPx)
        assertEquals(true, standard.notificationAvatarCssPx < large.notificationAvatarCssPx)
        assertEquals(true, large.notificationAvatarCssPx < extraLarge.notificationAvatarCssPx)
        assertEquals(true, standard.notificationGroupAvatarCssPx < large.notificationGroupAvatarCssPx)
        assertEquals(true, large.notificationGroupAvatarCssPx < extraLarge.notificationGroupAvatarCssPx)
        assertEquals(true, standard.notificationFontPercent < large.notificationFontPercent)
        assertEquals(true, large.notificationFontPercent < extraLarge.notificationFontPercent)
    }

    @Test
    fun notificationReactionLaneKeepsAvatarAndReactionSeparate() {
        ReactionScale.entries.forEach { scale ->
            val metrics = ReactionScalePolicy.forScale(scale)
            assertEquals(
                metrics.notificationAvatarCssPx +
                    metrics.notificationReactionGapCssPx +
                    metrics.notificationReactionCssPx,
                metrics.notificationHeadLaneCssPx,
            )
            assertEquals(
                metrics.notificationGroupAvatarCssPx +
                    metrics.notificationReactionGapCssPx +
                    metrics.notificationReactionCssPx,
                metrics.notificationGroupLaneCssPx,
            )
            assertEquals(true, metrics.notificationReactionGapCssPx > 0)
            assertEquals(true, metrics.notificationReactionCssPx < metrics.notificationAvatarCssPx)
        }
        assertEquals(true, ReactionScalePolicy.forScale(ReactionScale.EXTRA_LARGE).notificationReactionCssPx >= 48)
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
