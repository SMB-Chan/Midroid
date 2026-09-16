package dev.midroid.app.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushKeysTest {
    @Test
    fun generatedKeyPairHasExpectedShape() {
        val pair = PushKeys.generateKeyPair()

        assertEquals(PushKeys.PUBLIC_KEY_BYTES, pair.publicUncompressed.size)
        assertEquals(0x04.toByte(), pair.publicUncompressed.first())
        assertEquals(PushKeys.PRIVATE_KEY_BYTES, pair.privateScalar.size)
    }

    @Test
    fun generatedPairsAndSecretsAreUnique() {
        val first = PushKeys.generateKeyPair()
        val second = PushKeys.generateKeyPair()

        assertFalse(first.publicUncompressed.contentEquals(second.publicUncompressed))
        assertFalse(first.privateScalar.contentEquals(second.privateScalar))
        assertFalse(
            PushKeys.generateAuthSecret().contentEquals(PushKeys.generateAuthSecret()),
        )
    }

    @Test
    fun authSecretIsSixteenBytes() {
        assertEquals(PushKeys.AUTH_SECRET_BYTES, PushKeys.generateAuthSecret().size)
    }

    @Test
    fun base64UrlEncodingHasNoPaddingOrUnsafeAlphabet() {
        val pair = PushKeys.generateKeyPair()
        val encoded = PushKeys.base64UrlNoPad(pair.publicUncompressed)

        assertFalse(encoded.contains('+'))
        assertFalse(encoded.contains('/'))
        assertFalse(encoded.contains('='))
    }

    @Test
    fun fingerprintIsStableAndDoesNotExposeKey() {
        val pair = PushKeys.generateKeyPair()
        val fingerprint = PushKeys.fingerprintSHA256(pair.publicUncompressed)

        assertEquals(fingerprint, PushKeys.fingerprintSHA256(pair.publicUncompressed))
        assertEquals(64, fingerprint.length)
        assertFalse(pair.toString().contains(PushKeys.base64UrlNoPad(pair.privateScalar)))
    }

    @Test
    fun malformedKeyMaterialIsRejected() {
        val pair = PushKeys.generateKeyPair()

        assertThrows(IllegalArgumentException::class.java) {
            ReceiverKeyPair(ByteArray(64), pair.privateScalar)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReceiverKeyPair(ByteArray(65), pair.privateScalar)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReceiverKeyPair(pair.publicUncompressed, ByteArray(31))
        }
    }

    @Test
    fun keyStoreRoundTripsPerAccount() {
        val store: PushKeyStore = InMemoryPushKeyStore()
        val stored = store.create("account-1")

        assertEquals(stored, store.load("account-1"))
        assertEquals(null, store.load("account-2"))

        val rotated = store.create("account-1")
        assertNotEquals(
            PushKeys.fingerprintSHA256(stored.publicUncompressed),
            PushKeys.fingerprintSHA256(rotated.publicUncompressed),
        )
        assertEquals(rotated, store.load("account-1"))

        store.delete("account-1")
        assertEquals(null, store.load("account-1"))
    }

    @Test
    fun blankSecretsAreRejected() {
        val pair = PushKeys.generateKeyPair()

        assertThrows(IllegalArgumentException::class.java) {
            StoredKeys("", pair.publicUncompressed, pair.privateScalar, PushKeys.generateAuthSecret())
        }
        assertThrows(IllegalArgumentException::class.java) {
            StoredKeys("a", pair.publicUncompressed, pair.privateScalar, ByteArray(15))
        }
    }

    private fun assertThrows(type: Class<out Throwable>, block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            assertTrue("Expected ${type.simpleName} but got ${error.javaClass.simpleName}", type.isInstance(error))
            return
        }
        throw AssertionError("Expected ${type.simpleName} but nothing was thrown.")
    }
}
