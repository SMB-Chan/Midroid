package dev.midroid.app.media

import java.net.URI

object NativeAudioHeaderPolicy {
    fun sanitize(
        sourceUrl: String,
        instanceBaseUrl: String?,
        headers: Map<String, String>,
    ): Map<String, String> {
        val sameOrigin = instanceBaseUrl != null && sameOrigin(sourceUrl, instanceBaseUrl)
        return headers
            .filterKeys { key ->
                key.equals("User-Agent", ignoreCase = true) ||
                    (key.equals("Cookie", ignoreCase = true) && sameOrigin) ||
                    (key.equals("Referer", ignoreCase = true) && sameOrigin)
            }
            .filterValues { it.isNotBlank() }
    }

    internal fun sameOrigin(first: String, second: String): Boolean {
        val left = parseHttpsOrigin(first) ?: return false
        val right = parseHttpsOrigin(second) ?: return false
        return left == right
    }

    private fun parseHttpsOrigin(value: String): Origin? = try {
        val uri = URI(value)
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase() ?: return null
        val port = if (uri.port == -1) 443 else uri.port
        Origin(host, port)
    } catch (_: Exception) {
        null
    }

    private data class Origin(
        val host: String,
        val port: Int,
    )
}
