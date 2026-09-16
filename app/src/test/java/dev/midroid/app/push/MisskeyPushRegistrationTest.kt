package dev.midroid.app.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MisskeyPushRegistrationTest {
    private val endpoint = "https://relay.example/v1/p/JzLQ3raZJfFBR0aqvOMsLrt54w4rJUsV"
    private val p256dh =
        "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"
    private val auth = "BTBZMqHH6r4Tts7J_aSIgg"

    @Test
    fun buildsExactSwRegisterShape() {
        val payload = MisskeyPushRegistration.build(endpoint, p256dh, auth)

        assertEquals(
            mapOf("endpoint" to endpoint, "publickey" to p256dh, "auth" to auth),
            payload,
        )
        assertEquals(
            setOf("endpoint", "publickey", "auth"),
            MisskeyPushRegistration.FIELD_NAMES,
        )
    }

    @Test
    fun payloadLeaksNoTransportIdentifiers() {
        val payload = MisskeyPushRegistration.build(endpoint, p256dh, auth)
        val serialized = payload.entries.joinToString { "${it.key}=${it.value}" }

        assertFalse(serialized.contains("fcm", ignoreCase = true))
        assertFalse(serialized.contains("unifiedpush", ignoreCase = true))
        assertFalse(serialized.contains("token", ignoreCase = true))
        assertTrue(payload.keys.containsAll(MisskeyPushRegistration.FIELD_NAMES))
    }

    @Test
    fun rejectsNonRelayEndpoint() {
        try {
            MisskeyPushRegistration.build("https://misskey.example/notes/1", p256dh, auth)
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("relay"))
        }
    }

    @Test
    fun rejectsBlankKeyMaterial() {
        try {
            MisskeyPushRegistration.build(endpoint, "  ", auth)
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("public key"))
        }
        try {
            MisskeyPushRegistration.build(endpoint, p256dh, "")
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("Auth secret"))
        }
    }
}
