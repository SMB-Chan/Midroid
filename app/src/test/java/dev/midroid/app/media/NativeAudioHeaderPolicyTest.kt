package dev.midroid.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAudioHeaderPolicyTest {
    private val headers = mapOf(
        "User-Agent" to "Midroid",
        "Cookie" to "session=secret",
        "Referer" to "https://misskey.example/notes/1",
        "Authorization" to "Bearer secret",
    )

    @Test
    fun sameOriginKeepsCookieAndReferer() {
        val safe = NativeAudioHeaderPolicy.sanitize(
            sourceUrl = "https://misskey.example/files/audio.ogg",
            instanceBaseUrl = "https://misskey.example/",
            headers = headers,
        )

        assertEquals("Midroid", safe["User-Agent"])
        assertEquals("session=secret", safe["Cookie"])
        assertEquals("https://misskey.example/notes/1", safe["Referer"])
        assertFalse(safe.containsKey("Authorization"))
    }

    @Test
    fun crossOriginDropsCookieAndReferer() {
        val safe = NativeAudioHeaderPolicy.sanitize(
            sourceUrl = "https://cdn.example/audio.ogg",
            instanceBaseUrl = "https://misskey.example/",
            headers = headers,
        )

        assertEquals(mapOf("User-Agent" to "Midroid"), safe)
    }

    @Test
    fun differentPortIsDifferentOrigin() {
        assertFalse(
            NativeAudioHeaderPolicy.sameOrigin(
                "https://misskey.example:8443/audio.ogg",
                "https://misskey.example/",
            ),
        )
    }

    @Test
    fun defaultHttpsPortMatchesExplicit443() {
        assertTrue(
            NativeAudioHeaderPolicy.sameOrigin(
                "https://misskey.example/audio.ogg",
                "https://misskey.example:443/",
            ),
        )
    }

    @Test
    fun malformedOrNonHttpsNeverMatches() {
        assertFalse(NativeAudioHeaderPolicy.sameOrigin("not a url", "https://misskey.example/"))
        assertFalse(NativeAudioHeaderPolicy.sameOrigin("http://misskey.example/a", "https://misskey.example/"))
    }
}
