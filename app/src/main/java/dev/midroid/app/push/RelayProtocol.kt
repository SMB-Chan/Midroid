package dev.midroid.app.push

import java.net.URI
import java.util.Base64
import java.util.Locale

object RelayProtocol {
    const val PROTOCOL_VERSION = 1
    const val MIN_ENDPOINT_ID_BITS = 128
    const val MAX_ENVELOPE_BYTES = 8 * 1024

    fun parseEndpointId(endpointUrl: String): String? = runCatching {
        val uri = URI(endpointUrl.trim())
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        if (!uri.userInfo.isNullOrEmpty()) return null

        val segments = (uri.rawPath ?: "").split('/').filter { it.isNotEmpty() }
        if (segments.size != 3) return null
        if (!segments[0].equals("v1", ignoreCase = false)) return null
        if (!segments[1].equals("p", ignoreCase = false)) return null

        val endpointId = segments[2]
        if (!isBase64Url(endpointId)) return null
        val decodedBits = decodeUnpadded(endpointId).size * 8
        if (decodedBits < MIN_ENDPOINT_ID_BITS) return null
        endpointId
    }.getOrNull()

    fun isPlausibleRelayEndpoint(endpointUrl: String): Boolean =
        parseEndpointId(endpointUrl) != null

    fun relayOrigin(endpointUrl: String): String? = runCatching {
        val uri = URI(endpointUrl.trim())
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        val authority = if (uri.port == -1 || uri.port == 443) host else "$host:${uri.port}"
        "https://$authority"
    }.getOrNull()

    private fun isBase64Url(value: String): Boolean {
        if (value.isEmpty() || value.length % 4 == 1) return false
        return value.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }
    }

    private fun decodeUnpadded(value: String): ByteArray {
        val padded = when (value.length % 4) {
            0 -> value
            2 -> "$value=="
            3 -> "$value="
            else -> throw IllegalArgumentException("Invalid base64url length.")
        }
        return Base64.getUrlDecoder().decode(padded)
    }
}
