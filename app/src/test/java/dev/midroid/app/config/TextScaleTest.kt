package dev.midroid.app.config

import org.junit.Assert.assertEquals
import org.junit.Test

class TextScaleTest {
    @Test
    fun unknownPreferenceFallsBackToAuto() {
        assertEquals(TextScale.AUTO, TextScale.fromKey(null))
        assertEquals(TextScale.AUTO, TextScale.fromKey("unknown"))
    }

    @Test
    fun autoScaleGrowsOnDenseNarrowPhones() {
        assertEquals(117, TextScale.AUTO.resolveTextZoom(densityDpi = 420, screenWidthDp = 393))
        assertEquals(126, TextScale.AUTO.resolveTextZoom(densityDpi = 560, screenWidthDp = 360))
    }

    @Test
    fun autoScaleKeepsLowDensityWideScreensAtDefault() {
        assertEquals(100, TextScale.AUTO.resolveTextZoom(densityDpi = 320, screenWidthDp = 600))
    }

    @Test
    fun fixedScalesAreExact() {
        assertEquals(100, TextScale.DEFAULT.resolveTextZoom(560, 360))
        assertEquals(115, TextScale.COMFORTABLE.resolveTextZoom(320, 600))
        assertEquals(130, TextScale.LARGE.resolveTextZoom(320, 600))
        assertEquals(145, TextScale.EXTRA_LARGE.resolveTextZoom(320, 600))
    }

    @Test
    fun persistedKeysResolveToExpectedScales() {
        assertEquals(TextScale.AUTO, TextScale.fromKey("auto"))
        assertEquals(TextScale.DEFAULT, TextScale.fromKey("100"))
        assertEquals(TextScale.COMFORTABLE, TextScale.fromKey("115"))
        assertEquals(TextScale.LARGE, TextScale.fromKey("130"))
        assertEquals(TextScale.EXTRA_LARGE, TextScale.fromKey("145"))
    }

    @Test
    fun autoScaleClampsToDocumentedBounds() {
        assertEquals(100, TextScale.autoTextZoom(densityDpi = 0, screenWidthDp = 1200))
        assertEquals(126, TextScale.autoTextZoom(densityDpi = 640, screenWidthDp = 1))
    }
}
