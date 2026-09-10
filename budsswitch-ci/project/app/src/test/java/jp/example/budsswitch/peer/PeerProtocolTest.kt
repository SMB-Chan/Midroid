package jp.example.budsswitch.peer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class PeerProtocolTest {

    @Test
    fun roundTripAuthenticatedPacket() {
        val now = 1_800_000_000_000L
        val bytes = PeerProtocol.encode(
            secret = "shared-123",
            budsAddress = "A0:56:2C:60:15:63",
            id = "phone-a",
            name = "Motorola edge 40 neo",
            score = 50,
            reason = "media",
            sinceMs = now - 1000,
            sentMs = now
        )

        val decoded = PeerProtocol.decode(
            secret = "shared-123",
            budsAddress = "A0:56:2C:60:15:63",
            payload = bytes,
            nowWallMs = now,
            receivedAtElapsedMs = 42
        )

        assertNotNull(decoded)
        assertEquals("phone-a", decoded!!.id)
        assertEquals(50, decoded.score)
        assertEquals("media", decoded.reason)
    }

    @Test
    fun wrongSecretIsRejected() {
        val now = 1_800_000_000_000L
        val bytes = PeerProtocol.encode(
            secret = "shared-123",
            budsAddress = "A0:56:2C:60:15:63",
            id = "phone-a",
            name = "Phone A",
            score = 100,
            reason = "call",
            sinceMs = now,
            sentMs = now
        )

        assertNull(
            PeerProtocol.decode(
                secret = "wrong-999",
                budsAddress = "A0:56:2C:60:15:63",
                payload = bytes,
                nowWallMs = now,
                receivedAtElapsedMs = 1
            )
        )
    }

    @Test
    fun tamperedPacketIsRejected() {
        val now = 1_800_000_000_000L
        val original = PeerProtocol.encode(
            "shared-123", "A0:56:2C:60:15:63",
            "phone-a", "Phone A", 50, "media", now, now
        ).toString(Charsets.UTF_8)
        val tampered = original.replace("|50|", "|100|").toByteArray()

        assertNull(
            PeerProtocol.decode(
                "shared-123", "A0:56:2C:60:15:63",
                tampered, now, 1
            )
        )
    }
}
