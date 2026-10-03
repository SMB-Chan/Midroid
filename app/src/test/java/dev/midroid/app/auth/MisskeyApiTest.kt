package dev.midroid.app.auth

import dev.midroid.app.push.RelayProtocol
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
    fun swUnregisterBodyRequiresKeysAndOmitsTokenWhenAbsent() {
        val authed = MisskeyApi.parseJsonFields(
            MisskeyApi.swUnregisterBody("TOKEN123", endpoint, auth, p256dh),
        )
        assertEquals("TOKEN123", authed["i"])
        assertEquals(endpoint, authed["endpoint"])
        assertEquals(auth, authed["auth"])
        assertEquals(p256dh, authed["publickey"])

        val anonymous = MisskeyApi.parseJsonFields(
            MisskeyApi.swUnregisterBody(null, endpoint, auth, p256dh),
        )
        assertNull(anonymous["i"])
        assertEquals(endpoint, anonymous["endpoint"])
        assertEquals(auth, anonymous["auth"])
        assertEquals(p256dh, anonymous["publickey"])

        val blankToken = MisskeyApi.parseJsonFields(
            MisskeyApi.swUnregisterBody("  ", endpoint, auth, p256dh),
        )
        assertNull(blankToken["i"])
        assertEquals(auth, blankToken["auth"])
        assertEquals(p256dh, blankToken["publickey"])
    }

    @Test
    fun swUnregisterBodyRejectsBlankAuthOrPublicKey() {
        try {
            MisskeyApi.swUnregisterBody("TOKEN123", endpoint, " ", p256dh)
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("Auth"))
        }
        try {
            MisskeyApi.swUnregisterBody(null, endpoint, auth, "")
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("public key"))
        }
    }

    @Test
    fun swRegisterEndpointRequiresHttpsAndRejectsUserInfo() {
        val cleartext = endpoint.replace("https://", "http://")
        val withUserInfo = endpoint.replace("https://", "https://user:secret@")
        // Misskey 2026.10.0 PushNotificationService.isValidEndpoint requires https
        // and rejects URL userinfo. Midroid enforces that in RelayProtocol.parseEndpointId,
        // which sw/register consults before building a body.
        assertNull(RelayProtocol.parseEndpointId(cleartext))
        assertNull(RelayProtocol.parseEndpointId(withUserInfo))
        for (bad in listOf(cleartext, withUserInfo)) {
            try {
                MisskeyApi.swRegisterBody("TOKEN123", bad, p256dh, auth)
                throw AssertionError("Expected IllegalArgumentException.")
            } catch (error: IllegalArgumentException) {
                assertTrue(error.message!!.contains("relay"))
            }
        }
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

    @Test
    fun revokeTokenBodyTargetsTheTokenItAuthenticatesWith() {
        val body = MisskeyApi.revokeTokenBody("TOKEN123")
        val fields = MisskeyApi.parseJsonFields(body)

        assertEquals("TOKEN123", fields["i"])
        assertEquals("TOKEN123", fields["token"])

        try {
            MisskeyApi.revokeTokenBody(" ")
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("token"))
        }
    }

    @Test
    fun revokeTokenOutcomeClassifiesHttpStatuses() {
        assertEquals(MisskeyApi.RevokeTokenOutcome.REVOKED, MisskeyApi.revokeTokenOutcome(200))
        assertEquals(MisskeyApi.RevokeTokenOutcome.REVOKED, MisskeyApi.revokeTokenOutcome(204))
        assertEquals(MisskeyApi.RevokeTokenOutcome.ALREADY_GONE, MisskeyApi.revokeTokenOutcome(401))
        assertEquals(MisskeyApi.RevokeTokenOutcome.UNSUPPORTED, MisskeyApi.revokeTokenOutcome(404))
        assertEquals(MisskeyApi.RevokeTokenOutcome.FAILED, MisskeyApi.revokeTokenOutcome(400))
        assertEquals(MisskeyApi.RevokeTokenOutcome.FAILED, MisskeyApi.revokeTokenOutcome(403))
        assertEquals(MisskeyApi.RevokeTokenOutcome.FAILED, MisskeyApi.revokeTokenOutcome(500))
        assertEquals(MisskeyApi.RevokeTokenOutcome.FAILED, MisskeyApi.revokeTokenOutcome(502))
    }

    @Test
    fun parsesMetaVersion() {
        val response = """
            {"maintainerName":"example","maintainerEmail":null,"version":"2026.9.1",
             "providesTarball":false,"features":{"registration":true},"cacheRemoteFiles":true}
        """.trimIndent()
        assertEquals("2026.9.1", MisskeyApi.parseMetaVersion(response))
        assertNull(MisskeyApi.parseMetaVersion("""{"maintainerName":"example"}"""))
        assertNull(MisskeyApi.parseMetaVersion("""{"version":null}"""))
        assertNull(MisskeyApi.parseMetaVersion("null"))
        assertNull(MisskeyApi.parseMetaVersion(""))
        assertEquals("{}", MisskeyApi.metaBody())
    }
}
