package dev.midroid.app.web

import org.junit.Assert.assertEquals
import org.junit.Test

class WebCachePolicyTest {
    @Test
    fun raisesSmallCachesToMidroidFloor() {
        assertEquals(
            256L * 1024L * 1024L,
            WebCachePolicy.targetQuotaBytes(
                currentQuotaBytes = 64L * 1024L * 1024L,
                defaultQuotaBytes = 96L * 1024L * 1024L,
            ),
        )
    }

    @Test
    fun neverShrinksExistingLargerCache() {
        assertEquals(
            512L * 1024L * 1024L,
            WebCachePolicy.targetQuotaBytes(
                currentQuotaBytes = 512L * 1024L * 1024L,
                defaultQuotaBytes = 96L * 1024L * 1024L,
            ),
        )
    }

    @Test
    fun respectsLargerWebViewDefault() {
        assertEquals(
            384L * 1024L * 1024L,
            WebCachePolicy.targetQuotaBytes(
                currentQuotaBytes = 128L * 1024L * 1024L,
                defaultQuotaBytes = 384L * 1024L * 1024L,
            ),
        )
    }
}
