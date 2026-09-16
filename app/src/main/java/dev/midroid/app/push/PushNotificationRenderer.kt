package dev.midroid.app.push

data class PushNotificationContent(
    val title: String,
    val body: String?,
    val targetUrl: String?,
) {
    init {
        require(title.isNotBlank()) { "Notification title must not be blank." }
    }
}

object PushNotificationRenderer {
    const val MAX_TITLE_CHARS = 120
    const val MAX_BODY_CHARS = 400

    fun render(
        authenticatedPlaintext: ByteArray,
        fallbackTitle: String,
        fallbackTargetUrl: String?,
    ): PushNotificationContent {
        val parsed = parseLenient(authenticatedPlaintext)
        val title = firstNonBlank(
            parsed?.title,
            fallbackTitle,
        ) ?: fallbackTitle.take(MAX_TITLE_CHARS)
        val body = firstNonBlank(parsed?.body, null)?.take(MAX_BODY_CHARS)
        val targetUrl = sanitizeTargetUrl(parsed?.targetUrl)
            ?: sanitizeTargetUrl(fallbackTargetUrl)
        return PushNotificationContent(
            title = title.take(MAX_TITLE_CHARS).ifBlank { fallbackTitle.take(MAX_TITLE_CHARS) },
            body = body,
            targetUrl = targetUrl,
        )
    }

    fun parseLenient(authenticatedPlaintext: ByteArray): ParsedPayload? {
        if (authenticatedPlaintext.isEmpty() || authenticatedPlaintext.size > MAX_PLAINTEXT_BYTES) {
            return null
        }
        val text = runCatching {
            String(authenticatedPlaintext, Charsets.UTF_8)
        }.getOrNull() ?: return null
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("{")) {
            return ParsedPayload(title = trimmed.take(MAX_TITLE_CHARS), body = null, targetUrl = null)
        }
        return parseJsonLenient(trimmed)
    }

    internal fun parseJsonLenient(trimmed: String): ParsedPayload? {
        val title = extractStringField(trimmed, "title")
            ?: extractStringField(trimmed, "body")
        val body = extractStringField(trimmed, "body")
        val targetUrl = extractStringField(trimmed, "url")
            ?: extractStringField(trimmed, "targetUrl")
        if (title == null && body == null && targetUrl == null) return null
        return ParsedPayload(title, body, targetUrl)
    }

    private fun extractStringField(json: String, field: String): String? {
        val key = "\"$field\""
        var index = json.indexOf(key)
        while (index >= 0) {
            var cursor = index + key.length
            while (cursor < json.length && json[cursor].isWhitespace()) cursor += 1
            if (cursor < json.length && json[cursor] == ':') {
                cursor += 1
                while (cursor < json.length && json[cursor].isWhitespace()) cursor += 1
                if (cursor < json.length && json[cursor] == '"') {
                    val value = StringBuilder()
                    cursor += 1
                    while (cursor < json.length) {
                        val char = json[cursor]
                        if (char == '\\' && cursor + 1 < json.length) {
                            val escaped = json[cursor + 1]
                            value.append(
                                when (escaped) {
                                    'n' -> '\n'
                                    'r' -> '\r'
                                    't' -> '\t'
                                    '"' -> '"'
                                    '\\' -> '\\'
                                    else -> escaped
                                },
                            )
                            cursor += 2
                        } else if (char == '"') {
                            return value.toString().takeIf { it.isNotBlank() }
                        } else {
                            value.append(char)
                            cursor += 1
                        }
                    }
                    return null
                }
                return null
            }
            index = json.indexOf(key, index + 1)
        }
        return null
    }

    data class ParsedPayload(
        val title: String?,
        val body: String?,
        val targetUrl: String?,
    )

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun sanitizeTargetUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.length > MAX_URL_CHARS) return null
        if (!(trimmed.startsWith("https://", ignoreCase = true))) return null
        if (trimmed.contains(' ') || trimmed.contains('\n') || trimmed.contains('\r')) return null
        return trimmed
    }

    private const val MAX_PLAINTEXT_BYTES = 8 * 1024
    private const val MAX_URL_CHARS = 2048
}
