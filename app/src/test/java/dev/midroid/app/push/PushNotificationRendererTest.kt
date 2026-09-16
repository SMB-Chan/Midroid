package dev.midroid.app.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushNotificationRendererTest {
    @Test
    fun jsonPayloadMapsToContent() {
        val plaintext = """{"title":"New mention","body":"Hello from Misskey","url":"https://misskey.example/notes/1"}"""
            .toByteArray(Charsets.UTF_8)

        val content = PushNotificationRenderer.render(plaintext, "Fallback", "https://misskey.example/")

        assertEquals("New mention", content.title)
        assertEquals("Hello from Misskey", content.body)
        assertEquals("https://misskey.example/notes/1", content.targetUrl)
    }

    @Test
    fun plainTextPayloadBecomesTitle() {
        val content = PushNotificationRenderer.render(
            "Someone followed you".toByteArray(Charsets.UTF_8),
            "Fallback",
            null,
        )

        assertEquals("Someone followed you", content.title)
        assertEquals(null, content.body)
        assertEquals(null, content.targetUrl)
    }

    @Test
    fun emptyOrOversizePlaintextFallsBack() {
        val empty = PushNotificationRenderer.render(ByteArray(0), "Fallback", "https://misskey.example/")
        assertEquals("Fallback", empty.title)

        val oversize = PushNotificationRenderer.render(
            ByteArray(9 * 1024) { 0x41 },
            "Fallback",
            "https://misskey.example/",
        )
        assertEquals("Fallback", oversize.title)
    }

    @Test
    fun nonHttpsTargetUrlIsRejected() {
        val plaintext = """{"title":"Hi","url":"http://evil.example/phish"}"""
            .toByteArray(Charsets.UTF_8)

        val content = PushNotificationRenderer.render(plaintext, "Fallback", "https://misskey.example/")

        assertEquals("https://misskey.example/", content.targetUrl)
    }

    @Test
    fun javascriptSchemeNeverSurvives() {
        val plaintext = """{"title":"Hi","url":"javascript:alert(1)"}"""
            .toByteArray(Charsets.UTF_8)

        val content = PushNotificationRenderer.render(plaintext, "Fallback", null)

        assertNull(content.targetUrl)
    }

    @Test
    fun longFieldsAreTruncated() {
        val longTitle = "t".repeat(500)
        val longBody = "b".repeat(2000)
        val plaintext = """{"title":"$longTitle","body":"$longBody"}"""
            .toByteArray(Charsets.UTF_8)

        val content = PushNotificationRenderer.render(plaintext, "Fallback", null)

        assertEquals(PushNotificationRenderer.MAX_TITLE_CHARS, content.title.length)
        assertEquals(PushNotificationRenderer.MAX_BODY_CHARS, content.body?.length)
    }

    @Test
    fun malformedJsonFallsBackToRawText() {
        val content = PushNotificationRenderer.render(
            "{not json".toByteArray(Charsets.UTF_8),
            "Fallback",
            null,
        )

        assertTrue(content.title.isNotBlank())
    }
}
