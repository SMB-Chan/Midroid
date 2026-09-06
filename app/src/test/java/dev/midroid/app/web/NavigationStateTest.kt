package dev.midroid.app.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationStateTest {
    @Test
    fun lightboxCanReturnToCommittedMisskeyPage() {
        val state = NavigationState("https://misskey.example/notes/123")
        state.onHistoryChanged("https://misskey.example/notes/123#pswp")

        assertTrue(state.isMisskeyLightboxOpen())
        assertEquals("https://misskey.example/notes/123", state.interruptedReturnUrl())
    }

    @Test
    fun inFlightNavigationKeepsItsStartingPageAsFallbackEvenAfterCommit() {
        val state = NavigationState("https://misskey.example/notes/123")
        state.onPageStarted("https://misskey.example/files/large-image.jpg")
        state.onPageCommitted("https://misskey.example/files/large-image.jpg")

        assertTrue(state.mainFrameLoading)
        assertEquals("https://misskey.example/notes/123", state.interruptedReturnUrl())
    }

    @Test
    fun finishedNavigationDoesNotKeepAnInterruptFallback() {
        val state = NavigationState("https://misskey.example/notes/123")
        state.onPageStarted("https://misskey.example/notes/456")
        state.onPageCommitted("https://misskey.example/notes/456")
        state.onPageFinished("https://misskey.example/notes/456")

        assertFalse(state.mainFrameLoading)
        assertNull(state.interruptedReturnUrl())
    }

    @Test
    fun onlyExactPswpFragmentCountsAsLightbox() {
        assertTrue(NavigationState.isMisskeyLightboxUrl("https://misskey.example/#pswp"))
        assertFalse(NavigationState.isMisskeyLightboxUrl("https://misskey.example/#pswp-extra"))
        assertFalse(NavigationState.isMisskeyLightboxUrl("https://misskey.example/#other"))
    }
}
