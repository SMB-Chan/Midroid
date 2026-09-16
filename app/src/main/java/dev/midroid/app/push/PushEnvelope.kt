package dev.midroid.app.push

import java.util.Locale

data class PushEnvelope(
    val endpointId: String,
    val headers: Map<String, String>,
    val body: ByteArray,
    val ttlSeconds: Int?,
    val urgency: String?,
    val receivedAtEpochMs: Long,
) {
    fun validate(): Boolean {
        if (RelayProtocol.parseEndpointId("https://relay.invalid/v1/p/$endpointId") == null) {
            return false
        }
        if (body.isEmpty() || body.size > RelayProtocol.MAX_ENVELOPE_BYTES) return false
        val normalized = normalizedHeaders()
        if (!normalized["content-encoding"].equals("aes128gcm", ignoreCase = true)) return false
        val encryption = normalized["encryption"] ?: return false
        if (!hasSaltParameter(encryption)) return false
        if (ttlSeconds != null && (ttlSeconds < 0 || ttlSeconds > MAX_TTL_SECONDS)) return false
        if (urgency != null && urgency.lowercase(Locale.ROOT) !in VALID_URGENCIES) return false
        return true
    }

    fun contentEncoding(): String? = normalizedHeaders()["content-encoding"]

    fun encryptionSalt(): String? {
        val encryption = normalizedHeaders()["encryption"] ?: return null
        return encryption.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("salt=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotEmpty() }
    }

    fun senderPublicKey(): String? {
        val cryptoKey = normalizedHeaders()["crypto-key"] ?: return null
        return cryptoKey.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("dh=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotEmpty() }
    }

    private fun normalizedHeaders(): Map<String, String> =
        headers.entries.associate { (key, value) -> key.lowercase(Locale.ROOT) to value }

    private fun hasSaltParameter(encryption: String): Boolean = encryptionSalt() != null

    override fun toString(): String =
        "PushEnvelope(endpointId=${endpointId.take(8)}… bytes=${body.size})"

    companion object {
        const val MAX_TTL_SECONDS = 30 * 24 * 60 * 60
        private val VALID_URGENCIES = setOf("very-low", "low", "normal", "high")
    }
}
