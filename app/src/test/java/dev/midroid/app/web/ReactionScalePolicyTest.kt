package dev.midroid.app.web

import dev.midroid.app.config.ReactionScale
import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionScalePolicyTest {
    @Test
    fun standardUsesComfortableTouchTarget() {
        assertEquals(
            ReactionScaleMetrics(
                52, 200, 8,
                40, 160,
                48, 32,
                34, 102,
                46, 42,
                28, 24,
                6, 100,
            ),
            ReactionScalePolicy.forScale(ReactionScale.STANDARD),
        )
    }

    @Test
    fun largeAndExtraLargeAreClearlySeparated() {
        assertEquals(
            ReactionScaleMetrics(
                60, 230, 10,
                48, 192,
                56, 40,
                42, 126,
                52, 48,
                32, 28,
                6, 100,
            ),
            ReactionScalePolicy.forScale(ReactionScale.LARGE),
        )
        assertEquals(
            ReactionScaleMetrics(
                68, 260, 12,
                58, 232,
                64, 48,
                50, 150,
                58, 54,
                36, 32,
                8, 100,
            ),
            ReactionScalePolicy.forScale(ReactionScale.EXTRA_LARGE),
        )
    }

    @Test
    fun reactionSurfacesGrowWithoutScalingNotificationTextAgain() {
        val standard = ReactionScalePolicy.forScale(ReactionScale.STANDARD)
        val large = ReactionScalePolicy.forScale(ReactionScale.LARGE)
        val extraLarge = ReactionScalePolicy.forScale(ReactionScale.EXTRA_LARGE)

        assertEquals(true, standard.noteEmojiCssPx < large.noteEmojiCssPx)
        assertEquals(true, large.noteEmojiCssPx < extraLarge.noteEmojiCssPx)
        assertEquals(true, standard.noteEmojiMaxWidthCssPx < large.noteEmojiMaxWidthCssPx)
        assertEquals(true, large.noteEmojiMaxWidthCssPx < extraLarge.noteEmojiMaxWidthCssPx)
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
        assertEquals(true, standard.notificationStatusIconCssPx < large.notificationStatusIconCssPx)
        assertEquals(true, large.notificationStatusIconCssPx < extraLarge.notificationStatusIconCssPx)
        assertEquals(100, standard.notificationFontPercent)
        assertEquals(100, large.notificationFontPercent)
        assertEquals(100, extraLarge.notificationFontPercent)
    }

    @Test
    fun wideReactionsKeepAspectRatioHeadroomWithoutUnboundedNotificationRows() {
        ReactionScale.entries.forEach { scale ->
            val metrics = ReactionScalePolicy.forScale(scale)

            assertEquals(true, metrics.noteEmojiMaxWidthCssPx >= metrics.noteEmojiCssPx * 4)
            assertEquals(true, metrics.notificationReactionMaxWidthCssPx >= metrics.notificationReactionCssPx * 3)
            assertEquals(true, metrics.notificationStatusIconCssPx > 20)
            assertEquals(true, metrics.notificationGroupSymbolCssPx > 15)
            assertEquals(true, metrics.notificationAvatarCssPx <= 58)
            assertEquals(true, metrics.notificationGroupAvatarCssPx <= 54)
            assertEquals(true, metrics.notificationReactionCssPx <= 50)
        }
    }

    @Test
    fun notificationSurfacesHaveVisibleButCompactModeSeparation() {
        val standard = ReactionScalePolicy.forScale(ReactionScale.STANDARD)
        val large = ReactionScalePolicy.forScale(ReactionScale.LARGE)
        val extraLarge = ReactionScalePolicy.forScale(ReactionScale.EXTRA_LARGE)

        assertEquals(true, large.notificationReactionCssPx - standard.notificationReactionCssPx >= 8)
        assertEquals(true, extraLarge.notificationReactionCssPx - large.notificationReactionCssPx >= 8)
        assertEquals(true, large.notificationAvatarCssPx - standard.notificationAvatarCssPx >= 6)
        assertEquals(true, extraLarge.notificationAvatarCssPx - large.notificationAvatarCssPx >= 6)
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
