package dev.midroid.app.push

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.Base64

object PushKeys {
    const val AUTH_SECRET_BYTES = 16
    const val PUBLIC_KEY_BYTES = 65
    const val PRIVATE_KEY_BYTES = 32

    fun generateKeyPair(random: SecureRandom = SecureRandom()): ReceiverKeyPair {
        val generator = java.security.KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), random)
        val pair = generator.generateKeyPair()
        val publicKey = (pair.public as java.security.interfaces.ECPublicKey).toUncompressed()
        val privateKey = (pair.private as java.security.interfaces.ECPrivateKey).toFixedLengthScalar()
        return ReceiverKeyPair(publicKey, privateKey)
    }

    fun generateAuthSecret(random: SecureRandom = SecureRandom()): ByteArray {
        val secret = ByteArray(AUTH_SECRET_BYTES)
        random.nextBytes(secret)
        return secret
    }

    fun base64UrlNoPad(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun fingerprintSHA256(publicKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun java.security.interfaces.ECPublicKey.toUncompressed(): ByteArray {
        val size = 32
        val x = w.affineX.toByteArray().takeLastPadded(size)
        val y = w.affineY.toByteArray().takeLastPadded(size)
        return byteArrayOf(0x04) + x + y
    }

    private fun java.security.interfaces.ECPrivateKey.toFixedLengthScalar(): ByteArray =
        s.toByteArray().takeLastPadded(PRIVATE_KEY_BYTES)

    private fun ByteArray.takeLastPadded(size: Int): ByteArray {
        if (this.size == size) return copyOf()
        if (this.size > size) return copyOfRange(this.size - size, this.size)
        return ByteArray(size - this.size) + this
    }
}
