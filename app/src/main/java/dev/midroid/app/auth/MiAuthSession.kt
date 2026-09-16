package dev.midroid.app.auth

import dev.midroid.app.config.InstanceConfig
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

data class MiAuthSession(
    val instanceOrigin: String,
    val sessionId: String,
) {
    init {
        require(InstanceConfig.parse(instanceOrigin).isSuccess) {
            "MiAuth session requires a valid HTTPS instance origin."
        }
        require(isValidSessionId(sessionId)) { "MiAuth session id must be a UUID." }
    }

    fun authUrl(
        appName: String = APP_NAME,
        callbackUrl: String? = null,
        permissions: List<String> = DEFAULT_PERMISSIONS,
    ): String {
        require(appName.isNotBlank()) { "App name must not be blank." }
        val params = mutableListOf("name=${encode(appName)}")
        if (!callbackUrl.isNullOrBlank()) {
            require(callbackUrl.startsWith("https://", ignoreCase = true)) {
                "MiAuth callback must use HTTPS."
            }
            params += "callback=${encode(callbackUrl)}"
        }
        val granted = permissions.filter { it.isNotBlank() }
        if (granted.isNotEmpty()) {
            params += "permission=${encode(granted.joinToString(","))}"
        }
        return "$instanceOrigin/miauth/$sessionId?${params.joinToString("&")}"
    }

    fun checkUrl(): String = "$instanceOrigin/api/miauth/$sessionId/check"

    companion object {
        const val APP_NAME = "Midroid"
        val DEFAULT_PERMISSIONS: List<String> = listOf("read:account")

        fun start(instanceOrigin: String): MiAuthSession = MiAuthSession(
            instanceOrigin = InstanceConfig.parse(instanceOrigin).getOrThrow().origin,
            sessionId = UUID.randomUUID().toString(),
        )

        fun isValidSessionId(raw: String?): Boolean = runCatching {
            require(!raw.isNullOrBlank())
            UUID.fromString(raw.trim())
            true
        }.getOrDefault(false)

        fun parseCallbackUrl(rawUrl: String?): String? {
            if (rawUrl.isNullOrBlank()) return null
            val query = runCatching { URI(rawUrl.trim()).rawQuery }.getOrNull() ?: return null
            val session = query.split('&')
                .mapNotNull { pair ->
                    val separator = pair.indexOf('=')
                    if (separator < 0) return@mapNotNull null
                    val key = decode(pair.substring(0, separator))
                    if (!key.equals("session", ignoreCase = true)) return@mapNotNull null
                    decode(pair.substring(separator + 1)).trim()
                }
                .firstOrNull { it.isNotEmpty() }
                ?: return null
            return session.takeIf(::isValidSessionId)
        }

        private fun encode(value: String): String =
            java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

        private fun decode(value: String): String =
            URLDecoder.decode(value, Charsets.UTF_8.name())
    }
}
