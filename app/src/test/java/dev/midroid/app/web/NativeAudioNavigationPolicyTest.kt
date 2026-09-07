package dev.midroid.app.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAudioNavigationPolicyTest {
    @Test
    fun mainFrameNativeAudioRequestMayDispatch() {
        assertTrue(
            NativeAudioNavigationPolicy.shouldDispatch(
                scheme = "MIDROID-AUDIO",
                isForMainFrame = true,
            ),
        )
    }

    @Test
    fun subFrameNativeAudioRequestNeverDispatches() {
        assertFalse(
            NativeAudioNavigationPolicy.shouldDispatch(
                scheme = "midroid-audio",
                isForMainFrame = false,
            ),
        )
    }

    @Test
    fun ordinarySchemesNeverDispatch() {
        assertFalse(NativeAudioNavigationPolicy.shouldDispatch("https", true))
        assertFalse(NativeAudioNavigationPolicy.shouldDispatch(null, true))
    }
}
