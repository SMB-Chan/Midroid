package dev.midroid.app.config

import java.net.URI
import java.util.Locale

data class InstanceConfig(val origin: String) {
    private val uri = URI(origin)

    fun owns(rawUrl: String): Boolean = runCatching {
        val candidate = URI(rawUrl)
        candidate.scheme.equals("https", ignoreCase = true) &&
            candidate.host.equals(uri.host, ignoreCase = true) &&
            effectivePort(candidate) == effectivePort(uri)
    }.getOrDefault(false)

    companion object {
        fun parse(raw: String): Result<InstanceConfig> = runCatching {
            val trimmed = raw.trim()
            require(trimmed.isNotEmpty()) { "Enter a Misskey instance URL." }

            val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val parsed = URI(withScheme)

            require(parsed.scheme.equals("https", ignoreCase = true)) {
                "Midroid currently accepts HTTPS instances only."
            }
            require(!parsed.host.isNullOrBlank()) { "The instance host is invalid." }
            require(parsed.userInfo.isNullOrEmpty()) { "Credentials must not be embedded in the URL." }

            val host = parsed.host.lowercase(Locale.ROOT)
            val port = parsed.port
            val authority = if (port == -1 || port == 443) host else "$host:$port"
            InstanceConfig("https://$authority")
        }

        private fun effectivePort(uri: URI): Int = if (uri.port == -1) 443 else uri.port
    }
}
