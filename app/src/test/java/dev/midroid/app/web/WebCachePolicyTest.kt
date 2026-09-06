package dev.midroid.app.web

import org.junit.Assert.assertEquals
import org.junit.Test

class WebCachePolicyTest {
    private val mib = 1024L * 1024L
    private val gib = 1024L * mib

    @Test
    fun raisesToPreferredQuotaWhenStorageIsHealthy() {
        assertEquals(
            256L * mib,
            WebCachePolicy.targetQuotaBytes(
                currentQuotaBytes = 64L * mib,
                defaultQuotaBytes = 96L * mib,
                availableBytes = 3L * gib,
            ),
        )
    }

    @Test
    fun usesModerateQuotaWithOneToTwoGiBFree() {
        assertEquals(
            128L * mib,
            WebCachePolicy.targetQuotaBytes(
                currentQuotaBytes = 64L * mib,
                defaultQuotaBytes = 96L * mib,
                availableBytes = 1536L * mib,
            ),
        )
    }

    @Test
    fun doesNotGrowCacheWhenStorageIsTight() {
        assertEquals(
            64L * mib,
            WebCachePolicy.targetQuotaBytes(
                currentQuotaBytes = 64L * mib,
                defaultQuotaBytes = 96L * mib,
                availableBytes = 400L * mib,
            ),
        )
    }

    @Test
    fun neverShrinksExistingLargerCache() {
        assertEquals(
            512L * mib,
            WebCachePolicy.targetQuotaBytes(
                currentQuotaBytes = 512L * mib,
                defaultQuotaBytes = 96L * mib,
                availableBytes = 3L * gib,
            ),
        )
    }

    @Test
    fun respectsLargerWebViewDefaultWhenThereIsRoom() {
        assertEquals(
            384L * mib,
            WebCachePolicy.targetQuotaBytes(
                currentQuotaBytes = 128L * mib,
                defaultQuotaBytes = 384L * mib,
                availableBytes = 3L * gib,
            ),
        )
    }
}
