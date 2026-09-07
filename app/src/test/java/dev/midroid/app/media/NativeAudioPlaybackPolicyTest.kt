package dev.midroid.app.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAudioPlaybackPolicyTest {
    @Test
    fun delayedReadyAfterHostStopDoesNotAutoStart() {
        assertFalse(
            NativeAudioPlaybackPolicy.shouldAutoStart(
                hostVisible = false,
                playbackRequested = false,
            ),
        )
    }

    @Test
    fun visibleExplicitRequestMayStart() {
        assertTrue(
            NativeAudioPlaybackPolicy.shouldAutoStart(
                hostVisible = true,
                playbackRequested = true,
            ),
        )
    }

    @Test
    fun progressPollingStopsWhenHiddenOrDismissed() {
        assertFalse(NativeAudioPlaybackPolicy.shouldPollProgress(false, true))
        assertFalse(NativeAudioPlaybackPolicy.shouldPollProgress(true, false))
        assertTrue(NativeAudioPlaybackPolicy.shouldPollProgress(true, true))
    }
}
