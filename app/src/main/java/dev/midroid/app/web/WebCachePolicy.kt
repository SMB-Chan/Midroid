package dev.midroid.app.web

object WebCachePolicy {
    private const val MIB: Long = 1024L * 1024L
    private const val GIB: Long = 1024L * MIB

    const val CONSERVATIVE_HTTP_CACHE_BYTES: Long = 64L * MIB
    const val MODERATE_HTTP_CACHE_BYTES: Long = 128L * MIB
    const val PREFERRED_HTTP_CACHE_BYTES: Long = 256L * MIB
    const val MIN_FREE_FOR_CACHE_GROWTH_BYTES: Long = 512L * MIB

    fun targetQuotaBytes(
        currentQuotaBytes: Long,
        defaultQuotaBytes: Long,
        availableBytes: Long,
    ): Long {
        // Quota is a ceiling, not preallocated storage, but avoid increasing it at all when
        // the app's filesystem is already tight. Existing/platform choices are never shrunk.
        if (availableBytes < MIN_FREE_FOR_CACHE_GROWTH_BYTES) return currentQuotaBytes

        val adaptiveFloor = when {
            availableBytes >= 2L * GIB -> PREFERRED_HTTP_CACHE_BYTES
            availableBytes >= GIB -> MODERATE_HTTP_CACHE_BYTES
            else -> CONSERVATIVE_HTTP_CACHE_BYTES
        }

        return maxOf(currentQuotaBytes, defaultQuotaBytes, adaptiveFloor)
    }
}
