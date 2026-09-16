package dev.midroid.app.push

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class PushKeyRotationTest {
    private val endpointId = "JzLQ3raZJfFBR0aqvOMsLrt54w4rJUsV"
    private val endpoint = "https://relay.example/v1/p/$endpointId"
    private val rotatedId = "AAAAAAAAAAAAAAAAAAAAAA"
    private val rotatedEndpoint = "https://relay.example/v1/p/$rotatedId"

    @Test
    fun sealRoundTripsKeyMaterialWithAccountBinding() {
        val store = InMemoryPushKeyStore()
        val keys = store.create("account-1")
        val wrappingKey = PushKeySealTestHelper.generateWrappingKey()

        val opened = KeyMaterialSeal.open(
            wrappingKey,
            "account-1",
            KeyMaterialSeal.seal(wrappingKey, keys, SecureRandom()),
        )

        assertArrayEquals(keys.publicUncompressed, opened.publicUncompressed)
        assertArrayEquals(keys.privateScalar, opened.privateScalar)
        assertArrayEquals(keys.authSecret, opened.authSecret)
    }

    @Test
    fun sealRejectsWrongAccountBinding() {
        val store = InMemoryPushKeyStore()
        val keys = store.create("account-1")
        val wrappingKey = PushKeySealTestHelper.generateWrappingKey()
        val sealed = KeyMaterialSeal.seal(wrappingKey, keys, SecureRandom())

        try {
            KeyMaterialSeal.open(wrappingKey, "account-2", sealed)
            throw AssertionError("Expected sealed-open failure.")
        } catch (error: Exception) {
            assertTrue(error is javax.crypto.AEADBadTagException || error is IllegalArgumentException)
        }
    }

    @Test
    fun sealTamperFailsClosed() {
        val store = InMemoryPushKeyStore()
        val keys = store.create("account-1")
        val wrappingKey = PushKeySealTestHelper.generateWrappingKey()
        val sealed = KeyMaterialSeal.seal(wrappingKey, keys, SecureRandom())
        val tamperedBytes = sealed.ciphertext.copyOf()
        tamperedBytes[0] = (tamperedBytes[0].toInt() xor 0x01).toByte()

        try {
            KeyMaterialSeal.open(wrappingKey, "account-1", sealed.copy(ciphertext = tamperedBytes))
            throw AssertionError("Expected sealed-open failure.")
        } catch (error: Exception) {
            assertTrue(error is javax.crypto.AEADBadTagException || error is IllegalArgumentException)
        }
    }

    @Test
    fun sealEncodingRoundTripsAndRejectsGarbage() {
        val store = InMemoryPushKeyStore()
        val keys = store.create("account-1")
        val wrappingKey = PushKeySealTestHelper.generateWrappingKey()
        val sealed = KeyMaterialSeal.seal(wrappingKey, keys, SecureRandom())

        val decoded = KeyMaterialSeal.decode(KeyMaterialSeal.encode(sealed))
        assertArrayEquals(sealed.iv, decoded?.iv)
        assertArrayEquals(sealed.ciphertext, decoded?.ciphertext)
        assertNull(KeyMaterialSeal.decode(null))
        assertNull(KeyMaterialSeal.decode("not-base64!!!"))
    }

    @Test
    fun rotateReplacesKeysAndSubscriptionAtomically() {
        val store = InMemoryPushKeyStore()
        val subscriptions = mutableListOf(
            PushSubscription(
                accountId = "account-1",
                instanceOrigin = "https://misskey.example",
                endpoint = endpoint,
                p256dhBase64Url = "p256dh",
                authBase64Url = "auth",
                endpointId = endpointId,
                createdAtEpochMs = 10L,
                updatedAtEpochMs = 10L,
            ),
        )
        val before = store.create("account-1")

        val rotation = PushKeyRotation.rotate(
            store,
            subscriptions,
            "account-1",
            "https://misskey.example",
            rotatedEndpoint,
            nowEpochMs = 20L,
        )

        assertEquals(endpoint, rotation.supersededEndpoint)
        assertEquals(rotatedId, rotation.current.endpointId)
        assertEquals(10L, rotation.current.createdAtEpochMs)
        assertEquals(20L, rotation.current.updatedAtEpochMs)
        assertEquals(1, subscriptions.size)
        assertEquals(rotation.current, subscriptions.single())
        val after = store.load("account-1")
        assertEquals(rotation.keys, after)
        assertTrue(
            !before.publicUncompressed.contentEquals(after?.publicUncompressed),
        )
    }

    @Test
    fun revokeDeletesKeysAndSubscription() {
        val store = InMemoryPushKeyStore()
        store.create("account-1")
        val subscriptions = mutableListOf(
            PushSubscription(
                accountId = "account-1",
                instanceOrigin = "https://misskey.example",
                endpoint = endpoint,
                p256dhBase64Url = "p256dh",
                authBase64Url = "auth",
                endpointId = endpointId,
                createdAtEpochMs = 10L,
                updatedAtEpochMs = 10L,
            ),
        )

        assertEquals(endpoint, PushKeyRotation.revoke(store, subscriptions, "account-1"))
        assertTrue(subscriptions.isEmpty())
        assertNull(store.load("account-1"))
        assertNull(PushKeyRotation.revoke(store, subscriptions, "account-1"))
    }

    @Test
    fun rotateRejectsNonRelayEndpointWithoutDeletingSubscription() {
        val store = InMemoryPushKeyStore()
        val subscriptions = mutableListOf(
            PushSubscription(
                accountId = "account-1",
                instanceOrigin = "https://misskey.example",
                endpoint = endpoint,
                p256dhBase64Url = "p256dh",
                authBase64Url = "auth",
                endpointId = endpointId,
                createdAtEpochMs = 10L,
                updatedAtEpochMs = 10L,
            ),
        )

        try {
            PushKeyRotation.rotate(
                store,
                subscriptions,
                "account-1",
                "https://misskey.example",
                "https://misskey.example/notes/1",
            )
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("relay"))
        }
        assertEquals(1, subscriptions.size)
    }
}
