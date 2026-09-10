package jp.example.budsswitch.autoswitch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorityEngineTest {

    @Test
    fun mediaRequiresHoldTime() {
        val e = PriorityEngine(mediaHoldMs = 1500, minimumSwitchGapMs = 5000)
        assertFalse(e.onMediaChanged(true, 1000))
        assertFalse(e.onMediaChanged(true, 2000))
        assertTrue(e.onMediaChanged(true, 2600))
    }

    @Test
    fun mediaIsRateLimitedAfterSwitch() {
        val e = PriorityEngine(mediaHoldMs = 0, minimumSwitchGapMs = 5000)
        e.onMediaChanged(true, 1000)
        assertTrue(e.onMediaChanged(true, 1001))
        e.markSwitched(1001)
        assertFalse(e.onMediaChanged(true, 2000))
        assertTrue(e.onMediaChanged(true, 7000))
    }

    @Test
    fun callCanPreemptRateLimit() {
        val e = PriorityEngine()
        e.markSwitched(1000)
        assertTrue(e.onCallActive(1001))
    }
}
