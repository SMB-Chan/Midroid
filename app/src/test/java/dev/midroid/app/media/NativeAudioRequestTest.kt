package dev.midroid.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeAudioRequestTest {
    @Test
    fun `parses encoded https audio request`() {
        val parsed = NativeAudioRequest.parse(
            "midroid-audio://play?url=https%3A%2F%2Fmedia.example%2Faudio.mp3%3Fx%3D1%26y%3D2&title=Example+audio",
        )

        requireNotNull(parsed)
        assertEquals("https://media.example/audio.mp3?x=1&y=2", parsed.sourceUrl)
        assertEquals("Example audio", parsed.title)
    }

    @Test
    fun `rejects non https source`() {
        assertNull(
            NativeAudioRequest.parse(
                "midroid-audio://play?url=http%3A%2F%2Fmedia.example%2Faudio.mp3",
            ),
        )
    }

    @Test
    fun `rejects embedded credentials`() {
        assertNull(
            NativeAudioRequest.parse(
                "midroid-audio://play?url=https%3A%2F%2Fuser%3Apass%40media.example%2Faudio.mp3",
            ),
        )
    }

    @Test
    fun `rejects unknown private scheme host`() {
        assertNull(
            NativeAudioRequest.parse(
                "midroid-audio://other?url=https%3A%2F%2Fmedia.example%2Faudio.mp3",
            ),
        )
    }
}
