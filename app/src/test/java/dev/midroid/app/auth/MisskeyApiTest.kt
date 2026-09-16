package dev.midroid.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MisskeyApiTest {
    private val endpoint = "https://relay.example/v1/p/JzLQ3raZJfFBR0aqvOMsLrt54w4rJUsV"
    private val p256dh =
        "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"
    private val auth = "BTBZMqHH6r4Tts7J_aSIgg"

    @Test
    fun swRegisterBodyUsesTokenPlusContractFields() {
        val body = MisskeyApi.swRegisterBody("TOKEN123", endpoint, p256dh, auth)
        val fields = MisskeyApi.parseJsonFields(body)

        assertEquals("TOKEN123", fields["i"])
        assertEquals(endpoint, fields["endpoint"])
        assertEquals(auth, fields["auth"])
        assertEquals(p256dh, fields["publickey"])
    }

    @Test
    fun swUnregisterBodyOmitsTokenWhenAbsent() {
        val authed = MisskeyApi.parseJsonFields(MisskeyApi.swUnregisterBody("TOKEN123", endpoint))
        assertEquals("TOKEN123", authed["i"])
        assertEquals(endpoint, authed["endpoint"])

        val anonymous = MisskeyApi.parseJsonFields(MisskeyApi.swUnregisterBody(null, endpoint))
        assertNull(anonymous["i"])
        assertEquals(endpoint, anonymous["endpoint"])
    }

    @Test
    fun endpointUrlRejectsNonApiPaths() {
        assertEquals(
            "https://misskey.example/api/sw/register",
            MisskeyApi.endpointUrl("https://misskey.example", "/api/sw/register"),
        )
        try {
            MisskeyApi.endpointUrl("https://misskey.example", "/miauth/abc")
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("/api/"))
        }
    }

    @Test
    fun parsesSubscribedAndAlreadySubscribed() {
        val subscribed = MisskeyApi.parseSwRegisterResponse(
            """{"state":"subscribed","key":"VAPIDPUB","userId":"user1","endpoint":"$endpoint","sendReadMessage":false}""",
        )
        assertEquals(MisskeyApi.SwRegisterState.SUBSCRIBED, subscribed.state)
        assertEquals("VAPIDPUB", subscribed.vapidPublicKey)
        assertEquals("user1", subscribed.userId)

        val existing = MisskeyApi.parseSwRegisterResponse(
            """{"state":"already-subscribed","key":null,"userId":"user1","endpoint":"$endpoint","sendReadMessage":true}""",
        )
        assertEquals(MisskeyApi.SwRegisterState.ALREADY_SUBSCRIBED, existing.state)
        assertEquals(null, existing.vapidPublicKey)
        assertEquals(true, existing.sendReadMessage)
    }

    @Test
    fun parsesShowRegistrationAndNull() {
        assertNull(MisskeyApi.parseSwShowRegistrationResponse("null"))
        assertNull(MisskeyApi.parseSwShowRegistrationResponse("  "))

        val found = MisskeyApi.parseSwShowRegistrationResponse(
            """{"userId":"user1","endpoint":"$endpoint","sendReadMessage":false}""",
        )
        assertEquals("user1", found?.userId)
        assertEquals(endpoint, found?.endpoint)
    }

    @Test
    fun parsesMiauthCheckResponse() {
        val result = MisskeyApi.parseMiauthCheckResponse(
            """{"token":"ACCESS123","user":{"id":"user1"}}""",
        )

        assertEquals("ACCESS123", result.token)
        assertTrue(result.userJson!!.contains("user1"))
    }

    @Test
    fun rejectsMalformedResponses() {
        try {
            MisskeyApi.parseSwRegisterResponse("""{"state":"weird","userId":"u","endpoint":"e"}""")
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("state"))
        }
        try {
            MisskeyApi.parseMiauthCheckResponse("""{"user":{}}""")
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("token"))
        }
    }
}
