package jp.example.budsswitch.peer

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PeerProtocol {
    private const val VERSION = "BS1"
    private const val MAX_CLOCK_SKEW_MS = 60_000L

    fun encode(
        secret: String,
        budsAddress: String,
        id: String,
        name: String,
        score: Int,
        reason: String,
        sinceMs: Long,
        sentMs: Long
    ): ByteArray {
        require(secret.length >= 6) { "peer secret must be at least 6 chars" }
        val body = listOf(
            VERSION,
            groupId(secret, budsAddress),
            enc(id),
            enc(name),
            score.coerceIn(0, 100).toString(),
            enc(reason),
            sinceMs.toString(),
            sentMs.toString()
        ).joinToString("|")
        val signature = hex(hmac(secret, body))
        return "$body|$signature".toByteArray(StandardCharsets.UTF_8)
    }

    fun decode(
        secret: String,
        budsAddress: String,
        payload: ByteArray,
        nowWallMs: Long,
        receivedAtElapsedMs: Long
    ): PeerState? = runCatching {
        if (secret.length < 6) return null
        val text = payload.toString(StandardCharsets.UTF_8)
        val parts = text.split('|')
        if (parts.size != 9 || parts[0] != VERSION) return null
        if (parts[1] != groupId(secret, budsAddress)) return null

        val body = parts.take(8).joinToString("|")
        val expected = hmac(secret, body)
        val supplied = unhex(parts[8]) ?: return null
        if (!MessageDigest.isEqual(expected, supplied)) return null

        val score = parts[4].toIntOrNull()?.coerceIn(0, 100) ?: return null
        val sinceMs = parts[6].toLongOrNull() ?: return null
        val sentMs = parts[7].toLongOrNull() ?: return null
        if (kotlin.math.abs(nowWallMs - sentMs) > MAX_CLOCK_SKEW_MS) return null

        PeerState(
            id = dec(parts[2]),
            name = dec(parts[3]),
            score = score,
            reason = dec(parts[5]),
            sinceMs = sinceMs,
            receivedAtElapsedMs = receivedAtElapsedMs
        )
    }.getOrNull()

    private fun groupId(secret: String, budsAddress: String): String {
        val raw = "${budsAddress.uppercase()}|$secret".toByteArray(StandardCharsets.UTF_8)
        return hex(MessageDigest.getInstance("SHA-256").digest(raw)).take(16)
    }

    private fun hmac(secret: String, body: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body.toByteArray(StandardCharsets.UTF_8))
    }

    private fun enc(value: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun dec(value: String): String =
        Base64.getUrlDecoder().decode(value).toString(StandardCharsets.UTF_8)

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(value: String): ByteArray? {
        if (value.length % 2 != 0) return null
        return runCatching {
            ByteArray(value.length / 2) { i ->
                value.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }
}
