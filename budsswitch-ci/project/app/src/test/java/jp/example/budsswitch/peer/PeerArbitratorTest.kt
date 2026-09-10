package jp.example.budsswitch.peer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeerArbitratorTest {

    @Test
    fun callBeatsMedia() {
        val local = DemandCandidate("a", "Phone A", 50, "media", 1000)
        val peer = PeerState("b", "Phone B", 100, "call", 900, 10)
        assertEquals("b", PeerArbitrator.winner(local, listOf(peer))?.id)
    }

    @Test
    fun newerEqualPriorityDemandWins() {
        val local = DemandCandidate("a", "Phone A", 50, "media", 1000)
        val peer = PeerState("b", "Phone B", 50, "media", 2000, 10)
        assertEquals("b", PeerArbitrator.winner(local, listOf(peer))?.id)
    }

    @Test
    fun idleHasNoWinner() {
        val local = DemandCandidate("a", "Phone A", 0, "idle", 0)
        val peer = PeerState("b", "Phone B", 0, "idle", 0, 10)
        assertNull(PeerArbitrator.winner(local, listOf(peer)))
    }
}
