package dev.midroid.app.media

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class NativeAudioRequest(
    val sourceUrl: String,
    val title: String?,
) {
    companion object {
        const val SCHEME = "midroid-audio"
        private const val HOST = "play"

        fun parse(rawUrl: String): NativeAudioRequest? = runCatching {
            val uri = URI(rawUrl)
            if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
            if (!uri.host.equals(HOST, ignoreCase = true)) return null

            val params = uri.rawQuery
                ?.split('&')
                ?.mapNotNull { pair ->
                    val separator = pair.indexOf('=')
                    if (separator < 0) return@mapNotNull null
                    val key = decode(pair.substring(0, separator))
                    val value = decode(pair.substring(separator + 1))
                    key to value
                }
                ?.toMap()
                .orEmpty()

            val source = params["url"]?.takeIf { it.isNotBlank() } ?: return null
            val sourceUri = URI(source)
            if (!sourceUri.scheme.equals("https", ignoreCase = true)) return null
            if (sourceUri.host.isNullOrBlank()) return null
            if (sourceUri.userInfo != null) return null

            NativeAudioRequest(
                sourceUrl = source,
                title = params["title"]?.takeIf { it.isNotBlank() }?.take(200),
            )
        }.getOrNull()

        private fun decode(value: String): String =
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}
