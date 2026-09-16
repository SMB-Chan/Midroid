package dev.midroid.app.auth

import dev.midroid.app.config.InstanceConfig
import dev.midroid.app.push.MisskeyPushRegistration
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

object MisskeyApi {
    fun swRegisterBody(token: String, endpoint: String, p256dh: String, auth: String): String {
        require(token.isNotBlank()) { "API token must not be blank." }
        val payload = MisskeyPushRegistration.build(endpoint, p256dh, auth)
        return jsonObject(
            "i" to token,
            MisskeyPushRegistration.FIELD_ENDPOINT to payload.getValue(MisskeyPushRegistration.FIELD_ENDPOINT),
            MisskeyPushRegistration.FIELD_AUTH to payload.getValue(MisskeyPushRegistration.FIELD_AUTH),
            MisskeyPushRegistration.FIELD_PUBLIC_KEY to payload.getValue(MisskeyPushRegistration.FIELD_PUBLIC_KEY),
        )
    }

    fun swRegisterBodyWithReadMessage(
        token: String,
        endpoint: String,
        p256dh: String,
        auth: String,
        sendReadMessage: Boolean,
    ): String {
        require(token.isNotBlank()) { "API token must not be blank." }
        val payload = MisskeyPushRegistration.build(endpoint, p256dh, auth)
        return jsonObject(
            "i" to token,
            MisskeyPushRegistration.FIELD_ENDPOINT to payload.getValue(MisskeyPushRegistration.FIELD_ENDPOINT),
            MisskeyPushRegistration.FIELD_AUTH to payload.getValue(MisskeyPushRegistration.FIELD_AUTH),
            MisskeyPushRegistration.FIELD_PUBLIC_KEY to payload.getValue(MisskeyPushRegistration.FIELD_PUBLIC_KEY),
            "sendReadMessage" to sendReadMessage,
        )
    }

    fun swUnregisterBody(token: String?, endpoint: String): String {
        require(endpoint.isNotBlank()) { "Endpoint must not be blank." }
        return if (token.isNullOrBlank()) {
            jsonObject("endpoint" to endpoint)
        } else {
            jsonObject("i" to token, "endpoint" to endpoint)
        }
    }

    fun swShowRegistrationBody(token: String, endpoint: String): String {
        require(token.isNotBlank()) { "API token must not be blank." }
        require(endpoint.isNotBlank()) { "Endpoint must not be blank." }
        return jsonObject("i" to token, "endpoint" to endpoint)
    }

    fun miauthCheckBody(sessionId: String): String {
        require(MiAuthSession.isValidSessionId(sessionId)) { "Session id must be a UUID." }
        return "{}"
    }

    fun parseSwRegisterResponse(raw: String): SwRegisterResult {
        val fields = parseJsonFields(raw)
        val state = fields["state"]
        val key = fields["key"]
        val userId = fields["userId"]
            ?: throw IllegalArgumentException("sw/register response lacks userId.")
        val endpoint = fields["endpoint"]
            ?: throw IllegalArgumentException("sw/register response lacks endpoint.")
        return SwRegisterResult(
            state = when (state) {
                "already-subscribed" -> SwRegisterState.ALREADY_SUBSCRIBED
                "subscribed" -> SwRegisterState.SUBSCRIBED
                else -> throw IllegalArgumentException("Unknown sw/register state: $state.")
            },
            vapidPublicKey = key,
            userId = userId,
            endpoint = endpoint,
            sendReadMessage = fields["sendReadMessage"]?.toBooleanStrictOrNull() ?: false,
        )
    }

    fun parseSwShowRegistrationResponse(raw: String): SwRegistration? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null") return null
        val fields = parseJsonFields(trimmed)
        val userId = fields["userId"] ?: return null
        val endpoint = fields["endpoint"] ?: return null
        return SwRegistration(
            userId = userId,
            endpoint = endpoint,
            sendReadMessage = fields["sendReadMessage"]?.toBooleanStrictOrNull() ?: false,
        )
    }

    fun parseMiauthCheckResponse(raw: String): MiauthCheckResult {
        val fields = parseJsonFields(raw)
        val token = fields["token"]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("miauth/check response lacks token.")
        return MiauthCheckResult(token = token, userJson = fields["user"])
    }

    fun endpointUrl(instanceOrigin: String, apiPath: String): String {
        val instance = InstanceConfig.parse(instanceOrigin).getOrThrow()
        require(apiPath.startsWith("/api/")) { "API path must start with /api/." }
        return instance.origin + apiPath
    }

    fun postJson(url: String, body: String, timeoutMs: Int = 15_000): ApiResponse {
        val uri = URI(url.trim())
        require(uri.scheme.equals("https", ignoreCase = true)) { "API calls require HTTPS." }
        require(uri.host?.isNotBlank() == true) { "API URL host is invalid." }
        val connection = uri.toURL().openConnection() as HttpsURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
            ApiResponse(status, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseJsonFields(raw: String): Map<String, String?> {
        val trimmed = raw.trim()
        require(trimmed.startsWith("{") && trimmed.endsWith("}")) { "Expected a JSON object." }
        val fields = mutableMapOf<String, String?>()
        var cursor = 1
        while (cursor < trimmed.length - 1) {
            while (cursor < trimmed.length && trimmed[cursor].isWhitespace() || trimmed.getOrNull(cursor) == ',') {
                cursor += 1
            }
            if (cursor >= trimmed.length - 1) break
            require(trimmed[cursor] == '"') { "Expected a JSON string key." }
            val (key, afterKey) = readString(trimmed, cursor)
            cursor = afterKey
            while (cursor < trimmed.length && trimmed[cursor].isWhitespace()) cursor += 1
            require(cursor < trimmed.length && trimmed[cursor] == ':') { "Expected ':'." }
            cursor += 1
            while (cursor < trimmed.length && trimmed[cursor].isWhitespace()) cursor += 1
            require(cursor < trimmed.length) { "Unexpected end of JSON." }
            when (trimmed[cursor]) {
                '"' -> {
                    val (value, afterValue) = readString(trimmed, cursor)
                    fields[key] = value
                    cursor = afterValue
                }
                '{' -> {
                    val end = findBalancedEnd(trimmed, cursor, '{', '}')
                    fields[key] = trimmed.substring(cursor, end)
                    cursor = end
                }
                '[' -> {
                    val end = findBalancedEnd(trimmed, cursor, '[', ']')
                    fields[key] = trimmed.substring(cursor, end)
                    cursor = end
                }
                else -> {
                    val end = trimmed.indexOfAny(charArrayOf(',', '}'), cursor)
                        .takeIf { it >= 0 } ?: trimmed.length
                    fields[key] = trimmed.substring(cursor, end).trim().takeIf { it != "null" }
                    cursor = end
                }
            }
        }
        return fields
    }

    private fun readString(json: String, start: Int): Pair<String, Int> {
        require(json[start] == '"')
        val value = StringBuilder()
        var cursor = start + 1
        while (cursor < json.length) {
            val char = json[cursor]
            if (char == '\\' && cursor + 1 < json.length) {
                when (val escaped = json[cursor + 1]) {
                    'n' -> value.append('\n')
                    'r' -> value.append('\r')
                    't' -> value.append('\t')
                    '"' -> value.append('"')
                    '\\' -> value.append('\\')
                    '/' -> value.append('/')
                    'u' -> {
                        require(cursor + 5 < json.length) { "Bad unicode escape." }
                        value.append(json.substring(cursor + 2, cursor + 6).toInt(16).toChar())
                        cursor += 6
                    }
                    else -> value.append(escaped)
                }
                if (json[cursor + 1] != 'u') cursor += 2
            } else if (char == '"') {
                return value.toString() to cursor + 1
            } else {
                value.append(char)
                cursor += 1
            }
        }
        throw IllegalArgumentException("Unterminated JSON string.")
    }

    private fun findBalancedEnd(json: String, start: Int, open: Char, close: Char): Int {
        var depth = 0
        var inString = false
        var cursor = start
        while (cursor < json.length) {
            val char = json[cursor]
            if (inString) {
                if (char == '\\') cursor += 1 else if (char == '"') inString = false
            } else {
                when (char) {
                    '"' -> inString = true
                    open -> depth += 1
                    close -> {
                        depth -= 1
                        if (depth == 0) return cursor + 1
                    }
                }
            }
            cursor += 1
        }
        throw IllegalArgumentException("Unbalanced JSON.")
    }

    private fun jsonObject(vararg entries: Pair<String, Any>): String = buildString {
        append('{')
        entries.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            append('"').append(escape(key)).append("\":")
            when (value) {
                is String -> append('"').append(escape(value)).append('"')
                is Boolean -> append(if (value) "true" else "false")
                is Number -> append(value.toString())
                else -> throw IllegalArgumentException("Unsupported JSON value type.")
            }
        }
        append('}')
    }

    private fun escape(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
                }
            }
        }
    }

    data class ApiResponse(val status: Int, val body: String) {
        fun isSuccess(): Boolean = status in 200..299
    }

    enum class SwRegisterState { SUBSCRIBED, ALREADY_SUBSCRIBED }

    data class SwRegisterResult(
        val state: SwRegisterState,
        val vapidPublicKey: String?,
        val userId: String,
        val endpoint: String,
        val sendReadMessage: Boolean,
    )

    data class SwRegistration(
        val userId: String,
        val endpoint: String,
        val sendReadMessage: Boolean,
    )

    data class MiauthCheckResult(val token: String, val userJson: String?)
}
