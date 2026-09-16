package dev.midroid.app.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushEnvelopeTest {
    private val endpointId = "JzLQ3raZJfFBR0aqvOMsLrt54w4rJUsV"

    private fun envelope(
        headers: Map<String, String> = mapOf(
            "Content-Encoding" to "aes128gcm",
            "Encryption" to "salt=DGv6ra1nlYgDCS1FRnbzlw",
            "Crypto-Key" to "dh=BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
        ),
        body: ByteArray = ByteArray(128) { 0x01 },
        ttlSeconds: Int? = 60,
        urgency: String? = "normal",
    ) = PushEnvelope(endpointId, headers, body, ttlSeconds, urgency, 0L)

    @Test
    fun validEnvelopePasses() {
        assertTrue(envelope().validate())
    }

    @Test
    fun headerLookupIsCaseInsensitive() {
        val parsed = envelope(
            headers = mapOf(
                "content-encoding" to "AES128GCM",
                "ENCRYPTION" to "salt=DGv6ra1nlYgDCS1FRnbzlw",
            ),
        )

        assertTrue(parsed.validate())
        assertEquals("AES128GCM", parsed.contentEncoding())
        assertEquals("DGv6ra1nlYgDCS1FRnbzlw", parsed.encryptionSalt())
    }

    @Test
    fun missingContentEncodingOrSaltFails() {
        assertFalse(envelope(headers = mapOf("Encryption" to "salt=abc")).validate())
        assertFalse(envelope(headers = mapOf("Content-Encoding" to "aes128gcm")).validate())
        assertFalse(
            envelope(headers = mapOf("Content-Encoding" to "aesgcm", "Encryption" to "salt=abc")).validate(),
        )
    }

    @Test
    fun oversizeOrEmptyBodyFails() {
        assertFalse(envelope(body = ByteArray(0)).validate())
        assertFalse(
            envelope(body = ByteArray(RelayProtocol.MAX_ENVELOPE_BYTES + 1)).validate(),
        )
        assertTrue(
            envelope(body = ByteArray(RelayProtocol.MAX_ENVELOPE_BYTES)).validate(),
        )
    }

    @Test
    fun shortEndpointIdFails() {
        assertFalse(envelope().copy(endpointId = "short").validate())
    }

    @Test
    fun ttlAndUrgencyBoundsAreEnforced() {
        assertFalse(envelope(ttlSeconds = -1).validate())
        assertFalse(envelope(ttlSeconds = PushEnvelope.MAX_TTL_SECONDS + 1).validate())
        assertFalse(envelope(urgency = "critical").validate())
        assertTrue(envelope(ttlSeconds = null, urgency = null).validate())
        assertTrue(envelope(urgency = "HIGH").validate())
    }

    @Test
    fun senderKeyExtraction() {
        val sender = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"
        assertEquals(sender, envelope().senderPublicKey())
        assertEquals(null, envelope(headers = mapOf("Content-Encoding" to "aes128gcm")).senderPublicKey())
    }

    @Test
    fun toStringNeverLeaksBody() {
        val text = envelope().toString()
        assertFalse(text.contains("DGv6ra1nlYgDCS1FRnbzlw"))
        assertTrue(text.contains("bytes=128"))
    }
}
