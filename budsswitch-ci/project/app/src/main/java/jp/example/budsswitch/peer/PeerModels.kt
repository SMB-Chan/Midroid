package jp.example.budsswitch.peer

data class DemandCandidate(
    val id: String,
    val name: String,
    val score: Int,
    val reason: String,
    val sinceMs: Long
)

data class PeerState(
    val id: String,
    val name: String,
    val score: Int,
    val reason: String,
    val sinceMs: Long,
    val receivedAtElapsedMs: Long
) {
    fun candidate(): DemandCandidate = DemandCandidate(id, name, score, reason, sinceMs)
}

object PeerArbitrator {
    /**
     * Higher score wins. For equal score, the most recently activated demand wins.
     * A final stable id tie-break prevents two devices from believing they both own the Buds.
     */
    fun winner(local: DemandCandidate, peers: Collection<PeerState>): DemandCandidate? =
        buildList {
            if (local.score > 0) add(local)
            peers.asSequence()
                .filter { it.score > 0 }
                .mapTo(this) { it.candidate() }
        }.maxWithOrNull(
            compareBy<DemandCandidate> { it.score }
                .thenBy { it.sinceMs }
                .thenBy { it.id }
        )
}
