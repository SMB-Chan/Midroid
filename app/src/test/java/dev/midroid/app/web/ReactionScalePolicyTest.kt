package dev.midroid.app.web

import dev.midroid.app.config.ReactionScale
import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionScalePolicyTest {
    @Test
    fun standardUsesComfortableTouchTarget() {
        assertEquals(
            ReactionScaleMetrics(52, 200, 8, 36, 48, 32, 24, 44, 44, 100),
            ReactionScalePolicy.forScale(ReactionScale.STANDARD),
        )
    }

    @Test
    fun largeAndExtraLargeAreClearlySeparated() {
        assertEquals(
            ReactionScaleMetrics(60, 230, 10, 44, 56, 40, 30, 50, 50, 108),
            ReactionScalePolicy.forScale(ReactionScale.LARGE),
        )
        assertEquals(
            ReactionScaleMetrics(68, 260, 12, 52, 64, 48, 36, 58, 58, 116),
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
        assertEquals(true, standard.notificationEmojiCssPx < large.notificationEmojiCssPx)
        assertEquals(true, large.notificationEmojiCssPx < extraLarge.notificationEmojiCssPx)
        assertEquals(true, standard.notificationAvatarCssPx < large.notificationAvatarCssPx)
        assertEquals(true, large.notificationAvatarCssPx < extraLarge.notificationAvatarCssPx)
        assertEquals(true, standard.notificationGroupItemCssPx < large.notificationGroupItemCssPx)
        assertEquals(true, large.notificationGroupItemCssPx < extraLarge.notificationGroupItemCssPx)
        assertEquals(true, standard.notificationFontPercent < large.notificationFontPercent)
        assertEquals(true, large.notificationFontPercent < extraLarge.notificationFontPercent)
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
