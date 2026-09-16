package dev.midroid.app.push

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPushDecryptorTest {
    private val auth = WebPushDecryptor.decodeBase64Url("BTBZMqHH6r4Tts7J_aSIgg")
    private val receiverPrivate =
        WebPushDecryptor.decodeBase64Url("q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94")
    private val receiverPublic = WebPushDecryptor.decodeBase64Url(
        "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
    )
    private val senderPublic = WebPushDecryptor.decodeBase64Url(
        "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
    )
    private val salt = WebPushDecryptor.decodeBase64Url("DGv6ra1nlYgDCS1FRnbzlw")
    private val body = WebPushDecryptor.decodeBase64Url(
        "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml" +
            "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT" +
            "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
    )

    @Test
    fun rfc8291VectorDecryptsToWatermelonPlaintext() {
        val plaintext = WebPushDecryptor.decrypt(
            receiverPrivateScalar = receiverPrivate,
            authSecret = auth,
            senderPublicUncompressed = senderPublic,
            receiverPublicUncompressed = receiverPublic,
            salt = salt,
            body = body,
        ).getOrThrow()

        assertEquals("When I grow up, I want to be a watermelon", String(plaintext, Charsets.UTF_8))
    }

    @Test
    fun tamperedCiphertextFailsAuthentication() {
        val tampered = body.copyOf()
        tampered[tampered.size - 1] = (tampered.last().toInt() xor 0x01).toByte()

        assertTrue(
            WebPushDecryptor.decrypt(
                receiverPrivate,
                auth,
                senderPublic,
                receiverPublic,
                salt,
                tampered,
            ).isFailure,
        )
    }

    @Test
    fun wrongAuthSecretFailsAuthentication() {
        val wrongAuth = auth.copyOf()
        wrongAuth[0] = (wrongAuth[0].toInt() xor 0x01).toByte()

        assertTrue(
            WebPushDecryptor.decrypt(
                receiverPrivate,
                wrongAuth,
                senderPublic,
                receiverPublic,
                salt,
                body,
            ).isFailure,
        )
    }

    @Test
    fun malformedInputsFailClosed() {
        assertTrue(
            WebPushDecryptor.decrypt(
                ByteArray(31),
                auth,
                senderPublic,
                receiverPublic,
                salt,
                body,
            ).isFailure,
        )
        assertTrue(
            WebPushDecryptor.decrypt(
                receiverPrivate,
                ByteArray(15),
                senderPublic,
                receiverPublic,
                salt,
                body,
            ).isFailure,
        )
        assertTrue(
            WebPushDecryptor.decrypt(
                receiverPrivate,
                auth,
                senderPublic,
                receiverPublic,
                salt,
                ByteArray(10),
            ).isFailure,
        )
    }

    @Test
    fun roundTripWithFreshKeys() {
        val pair = PushKeys.generateKeyPair()
        val secret = PushKeys.generateAuthSecret()
        val sender = PushKeys.generateKeyPair()
        val plaintext = "midroid push round-trip".toByteArray(Charsets.UTF_8)
        val messageSalt = PushKeys.generateAuthSecret().copyOf(16)

        val senderPrivate = sender.privateScalar
        val senderPublicBytes = sender.publicUncompressed
        val ecdh = ecdhForTest(senderPrivate, pair.publicUncompressed)
        val keyInfo = "WebPush: info".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0x00) + pair.publicUncompressed + senderPublicBytes
        val pseudoRandomKey = hmacForTest(secret, ecdh)
        val inputKeyingMaterial = hkdfExpandForTest(
            pseudoRandomKey,
            keyInfo,
            32,
        )
        val pseudoRandom = hmacForTest(messageSalt, inputKeyingMaterial)
        val contentKey = hkdfExpandForTest(
            pseudoRandom,
            "Content-Encoding: aes128gcm".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00),
            16,
        )
        val nonce = hkdfExpandForTest(
            pseudoRandom,
            "Content-Encoding: nonce".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00),
            12,
        )
        val padded = plaintext + byteArrayOf(0x02)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(contentKey, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, nonce),
        )
        val ciphertext = cipher.doFinal(padded)
        val record = messageSalt +
            byteArrayOf(0x10, 0x00, 0x00, 0x00) +
            byteArrayOf(senderPublicBytes.size.toByte()) +
            senderPublicBytes +
            ciphertext

        val decrypted = WebPushDecryptor.decrypt(
            pair.privateScalar,
            secret,
            senderPublicBytes,
            pair.publicUncompressed,
            messageSalt,
            record,
        ).getOrThrow()

        assertArrayEquals(plaintext, decrypted)
    }

    private fun ecdhForTest(privateScalar: ByteArray, peerPublic: ByteArray): ByteArray {
        val generator = java.security.KeyPairGenerator.getInstance("EC")
        generator.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        val params = (generator.generateKeyPair().public as java.security.interfaces.ECPublicKey).params
        val factory = java.security.KeyFactory.getInstance("EC")
        val privateKey = factory.generatePrivate(
            java.security.spec.ECPrivateKeySpec(java.math.BigInteger(1, privateScalar), params),
        )
        val point = java.security.spec.ECPoint(
            java.math.BigInteger(1, peerPublic.copyOfRange(1, 33)),
            java.math.BigInteger(1, peerPublic.copyOfRange(33, 65)),
        )
        val peer = factory.generatePublic(java.security.spec.ECPublicKeySpec(point, params))
        val agreement = javax.crypto.KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(peer, true)
        return agreement.generateSecret()
    }

    private fun hmacForTest(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hkdfExpandForTest(pseudoRandomKey: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
        val output = mutableListOf<Byte>()
        var previous = ByteArray(0)
        var counter = 1
        while (output.size < length) {
            mac.reset()
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            output.addAll(previous.toList())
            counter += 1
        }
        return output.take(length).toByteArray()
    }
}
