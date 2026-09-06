package dev.midroid.app.web

object WebCachePolicy {
    const val MIN_HTTP_CACHE_BYTES: Long = 256L * 1024L * 1024L

    fun targetQuotaBytes(currentQuotaBytes: Long, defaultQuotaBytes: Long): Long {
        return maxOf(currentQuotaBytes, defaultQuotaBytes, MIN_HTTP_CACHE_BYTES)
    }
}
