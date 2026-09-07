package dev.midroid.app.media

import java.net.URI
import java.util.Locale

object NativeAudioHeaderPolicy {
    fun sanitize(
        sourceUrl: String,
        instanceBaseUrl: String?,
        headers: Map<String, String>,
    ): Map<String, String> {
        // DefaultHttpDataSource can follow HTTPS redirects to another host and fixed
        // default request properties may survive that hop. Until Midroid owns redirect
        // handling per hop, keep only non-sensitive transport metadata.
        return headers
            .filterKeys { key -> key.equals("User-Agent", ignoreCase = true) }
            .filterValues { it.isNotBlank() }
    }

    internal fun sameOrigin(first: String, second: String): Boolean {
        val left = parseHttpsOrigin(first) ?: return false
        val right = parseHttpsOrigin(second) ?: return false
        return left == right
    }

    private fun parseHttpsOrigin(value: String): Origin? {
        return try {
            val uri = URI(value)
            val host = uri.host?.lowercase(Locale.ROOT)
            if (!uri.scheme.equals("https", ignoreCase = true) || host.isNullOrBlank()) {
                null
            } else {
                val port = if (uri.port == -1) 443 else uri.port
                Origin(host, port)
            }
        } catch (_: Exception) {
            null
        }
    }

    private data class Origin(
        val host: String,
        val port: Int,
    )
}
