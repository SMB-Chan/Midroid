package dev.midroid.app.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayProtocolTest {
    private val validId = "JzLQ3raZJfFBR0aqvOMsLrt54w4rJUsV"

    @Test
    fun acceptsVersionedRelayEndpoint() {
        assertEquals(
            validId,
            RelayProtocol.parseEndpointId("https://relay.example/v1/p/$validId"),
        )
        assertTrue(RelayProtocol.isPlausibleRelayEndpoint("https://relay.example:443/v1/p/$validId"))
    }

    @Test
    fun rejectsCleartextOrCredentialedEndpoints() {
        assertNull(RelayProtocol.parseEndpointId("http://relay.example/v1/p/$validId"))
        assertNull(
            RelayProtocol.parseEndpointId("https://user:pass@relay.example/v1/p/$validId"),
        )
    }

    @Test
    fun rejectsWrongPathShape() {
        assertNull(RelayProtocol.parseEndpointId("https://relay.example/v2/p/$validId"))
        assertNull(RelayProtocol.parseEndpointId("https://relay.example/v1/q/$validId"))
        assertNull(RelayProtocol.parseEndpointId("https://relay.example/v1/p/"))
        assertNull(RelayProtocol.parseEndpointId("https://relay.example/v1/p/a/b"))
        assertNull(RelayProtocol.parseEndpointId("not a url"))
    }

    @Test
    fun rejectsShortOrNonBase64UrlIds() {
        assertNull(RelayProtocol.parseEndpointId("https://relay.example/v1/p/short"))
        assertNull(RelayProtocol.parseEndpointId("https://relay.example/v1/p/abcde"))
        assertNull(
            RelayProtocol.parseEndpointId("https://relay.example/v1/p/!!!!invalid!!!!"),
        )
        assertFalse(
            RelayProtocol.isPlausibleRelayEndpoint("https://relay.example/v1/p/AAAAAAAAAAAAAAAA"),
        )
    }

    @Test
    fun minimumEntropyBoundaryIsEnforced() {
        val fifteenBytes = "AAAAAAAAAAAAAAAAAAAAAA"
        val sixteenBytes = "AAAAAAAAAAAAAAAAAAAAAA"

        assertNull(RelayProtocol.parseEndpointId("https://relay.example/v1/p/AAAAAAAAAAAAAAAA"))
        assertEquals(
            sixteenBytes,
            RelayProtocol.parseEndpointId("https://relay.example/v1/p/$sixteenBytes"),
        )
        assertEquals(fifteenBytes.length, 22)
    }

    @Test
    fun relayOriginNormalizesHostAndPort() {
        assertEquals(
            "https://relay.example",
            RelayProtocol.relayOrigin("https://Relay.Example:443/v1/p/$validId"),
        )
        assertEquals(
            "https://relay.example:8443",
            RelayProtocol.relayOrigin("https://relay.example:8443/v1/p/$validId"),
        )
        assertNull(RelayProtocol.relayOrigin("http://relay.example/v1/p/$validId"))
    }
}
