package dev.midroid.app.diagnostics

import java.net.URI

internal fun diagnosticOrigin(rawUrl: String?): String {
    if (rawUrl.isNullOrBlank()) return "none"

    return runCatching {
        val uri = URI(rawUrl)
        val scheme = uri.scheme?.lowercase() ?: return@runCatching "invalid"
        val host = uri.host?.lowercase() ?: return@runCatching "invalid"
        val port = uri.port

        when {
            port == -1 -> "$scheme://$host"
            scheme == "https" && port == 443 -> "$scheme://$host"
            scheme == "http" && port == 80 -> "$scheme://$host"
            else -> "$scheme://$host:$port"
        }
    }.getOrDefault("invalid")
}
