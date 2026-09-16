package dev.midroid.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MiAuthSessionTest {
    @Test
    fun startGeneratesUuidSessionOnCanonicalOrigin() {
        val session = MiAuthSession.start("https://Misskey.Example:443")

        assertEquals("https://misskey.example", session.instanceOrigin)
        assertTrue(MiAuthSession.isValidSessionId(session.sessionId))
    }

    @Test
    fun authUrlCarriesNameCallbackAndPermission() {
        val session = MiAuthSession("https://misskey.example", UUID.randomUUID().toString())

        val url = session.authUrl(
            appName = "Midroid",
            callbackUrl = "https://app.example/callback",
            permissions = listOf("read:account", "write:notes"),
        )

        assertTrue(url.startsWith("https://misskey.example/miauth/${session.sessionId}?"))
        assertTrue(url.contains("name=Midroid"))
        assertTrue(url.contains("callback=https%3A%2F%2Fapp.example%2Fcallback"))
        assertTrue(url.contains("permission=read%3Aaccount%2Cwrite%3Anotes"))
    }

    @Test
    fun authUrlRejectsCleartextCallback() {
        val session = MiAuthSession("https://misskey.example", UUID.randomUUID().toString())

        try {
            session.authUrl(callbackUrl = "http://app.example/callback")
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("HTTPS"))
        }
    }

    @Test
    fun checkUrlShape() {
        val session = MiAuthSession("https://misskey.example", UUID.randomUUID().toString())

        assertEquals(
            "https://misskey.example/api/miauth/${session.sessionId}/check",
            session.checkUrl(),
        )
    }

    @Test
    fun callbackParserExtractsSession() {
        val sessionId = UUID.randomUUID().toString()

        assertEquals(
            sessionId,
            MiAuthSession.parseCallbackUrl("https://app.example/callback?session=$sessionId"),
        )
        assertEquals(
            sessionId,
            MiAuthSession.parseCallbackUrl("https://app.example/callback?foo=1&session=$sessionId&bar=2"),
        )
        assertNull(MiAuthSession.parseCallbackUrl("https://app.example/callback?foo=1"))
        assertNull(MiAuthSession.parseCallbackUrl("https://app.example/callback?session=not-a-uuid"))
        assertNull(MiAuthSession.parseCallbackUrl(null))
    }

    @Test
    fun rejectsInvalidConstruction() {
        try {
            MiAuthSession("http://misskey.example", UUID.randomUUID().toString())
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("HTTPS"))
        }
        try {
            MiAuthSession("https://misskey.example", "not-a-uuid")
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("UUID"))
        }
    }
}
