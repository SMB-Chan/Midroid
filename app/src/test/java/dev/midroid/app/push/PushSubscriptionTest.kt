package dev.midroid.app.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushSubscriptionTest {
    private fun subscription(
        accountId: String = "account-1",
        endpointId: String = "JzLQ3raZJfFBR0aqvOMsLrt54w4rJUsV",
    ): PushSubscription = PushSubscription(
        accountId = accountId,
        instanceOrigin = "https://misskey.example",
        endpoint = "https://relay.example/v1/p/$endpointId",
        p256dhBase64Url = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
        authBase64Url = "BTBZMqHH6r4Tts7J_aSIgg",
        endpointId = endpointId,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 2L,
    )

    @Test
    fun codecRoundTripsValidSubscription() {
        val decoded = PushSubscriptionJson.decode(
            PushSubscriptionJson.encode(subscription()),
        )

        assertEquals(subscription(), decoded)
    }

    @Test
    fun codecSkipsNonHttpsInstanceOrigin() {
        val raw = PushSubscriptionJson.encode(subscription()).toMutableMap()
        raw["instanceOrigin"] = "http://misskey.example"

        assertNull(PushSubscriptionJson.decode(raw))
    }

    @Test
    fun codecNormalizesInstanceOrigin() {
        val raw = PushSubscriptionJson.encode(subscription()).toMutableMap()
        raw["instanceOrigin"] = "https://Misskey.Example:443/notes"

        assertEquals(
            "https://misskey.example",
            PushSubscriptionJson.decode(raw)?.instanceOrigin,
        )
    }

    @Test
    fun codecSkipsNonRelayEndpoint() {
        val raw = PushSubscriptionJson.encode(subscription()).toMutableMap()
        raw["endpoint"] = "https://misskey.example/notes/1"

        assertNull(PushSubscriptionJson.decode(raw))
    }

    @Test
    fun codecSkipsMismatchedEndpointId() {
        val raw = PushSubscriptionJson.encode(subscription()).toMutableMap()
        raw["endpointId"] = "AAAAAAAAAAAAAAAAAAAAAA"

        assertNull(PushSubscriptionJson.decode(raw))
    }

    @Test
    fun codecSkipsBlankAccountOrKeyMaterial() {
        val blankAccount = PushSubscriptionJson.encode(subscription()).toMutableMap()
        blankAccount["accountId"] = "  "
        assertNull(PushSubscriptionJson.decode(blankAccount))

        val blankKey = PushSubscriptionJson.encode(subscription()).toMutableMap()
        blankKey["p256dh"] = ""
        assertNull(PushSubscriptionJson.decode(blankKey))
    }

    @Test
    fun decodeHandlesMissingOptionalFields() {
        val raw = PushSubscriptionJson.encode(subscription()).toMutableMap()
        raw.remove("endpointId")
        raw.remove("createdAt")
        raw.remove("updatedAt")

        val decoded = PushSubscriptionJson.decode(raw)

        assertEquals(subscription().endpointId, decoded?.endpointId)
        assertEquals(0L, decoded?.createdAtEpochMs)
        assertEquals(0L, decoded?.updatedAtEpochMs)
    }

    @Test
    fun constructorRejectsMismatchedEndpointId() {
        val valid = subscription()
        try {
            valid.copy(endpointId = "AAAAAAAAAAAAAAAAAAAAAA")
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("Endpoint id"))
        }
    }
}
