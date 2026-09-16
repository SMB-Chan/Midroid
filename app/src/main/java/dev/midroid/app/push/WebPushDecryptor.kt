package dev.midroid.app.push

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object WebPushDecryptor {
    fun decrypt(
        receiverPrivateScalar: ByteArray,
        authSecret: ByteArray,
        senderPublicUncompressed: ByteArray,
        receiverPublicUncompressed: ByteArray,
        salt: ByteArray,
        body: ByteArray,
    ): Result<ByteArray> = runCatching {
        require(receiverPrivateScalar.size == PushKeys.PRIVATE_KEY_BYTES) {
            "Receiver private scalar must be 32 bytes."
        }
        require(authSecret.size == PushKeys.AUTH_SECRET_BYTES) {
            "Auth secret must be 16 bytes."
        }
        require(senderPublicUncompressed.size == PushKeys.PUBLIC_KEY_BYTES) {
            "Sender public key must be 65 uncompressed bytes."
        }
        require(receiverPublicUncompressed.size == PushKeys.PUBLIC_KEY_BYTES) {
            "Receiver public key must be 65 uncompressed bytes."
        }
        require(salt.size == 16) { "Salt must be 16 bytes." }

        val record = parseRecord(body, senderPublicUncompressed.size)
        val ecdhSecret = agree(receiverPrivateScalar, record.senderPublicKey)
        val keyInfo = "WebPush: info".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0x00) +
            receiverPublicUncompressed +
            record.senderPublicKey
        val pseudoRandomKey = hkdfExtract(authSecret, ecdhSecret)
        val inputKeyingMaterial = hkdfExpand(pseudoRandomKey, keyInfo, 32)
        val contentKeyInfo = "Content-Encoding: aes128gcm".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00)
        val nonceInfo = "Content-Encoding: nonce".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00)
        val pseudoRandom = hkdfExtract(salt, inputKeyingMaterial)
        val contentKey = hkdfExpand(pseudoRandom, contentKeyInfo, 16)
        val nonce = hkdfExpand(pseudoRandom, nonceInfo, 12)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(contentKey, "AES"),
            GCMParameterSpec(128, nonce),
        )
        val padded = cipher.doFinal(record.ciphertext)
        val delimiter = padded.indexOf(0x02.toByte())
        require(delimiter >= 0) { "Padding delimiter is missing." }
        padded.copyOfRange(0, delimiter)
    }

    fun decodeBase64Url(value: String): ByteArray {
        val padded = when (value.length % 4) {
            0 -> value
            2 -> "$value=="
            3 -> "$value="
            else -> throw IllegalArgumentException("Invalid base64url length.")
        }
        return Base64.getUrlDecoder().decode(padded)
    }

    private data class ParsedRecord(val senderPublicKey: ByteArray, val ciphertext: ByteArray)

    private fun parseRecord(body: ByteArray, expectedKeyLength: Int): ParsedRecord {
        require(body.size >= 16 + 4 + 1 + expectedKeyLength + 16) {
            "Encrypted body is too short."
        }
        val buffer = ByteBuffer.wrap(body)
        buffer.position(16)
        val recordSize = buffer.int
        require(recordSize >= 0) { "Record size must not be negative." }
        val idLength = buffer.get().toInt() and 0xFF
        require(idLength == expectedKeyLength) {
            "Sender key length in record does not match header key."
        }
        val senderPublicKey = ByteArray(idLength)
        buffer.get(senderPublicKey)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)
        return ParsedRecord(senderPublicKey, ciphertext)
    }

    private fun agree(privateScalar: ByteArray, peerPublicUncompressed: ByteArray): ByteArray {
        val params = ecParams()
        val privateKey = KeyFactory.getInstance("EC").generatePrivate(
            ECPrivateKeySpec(java.math.BigInteger(1, privateScalar), params),
        )
        val peerPoint = decodePoint(peerPublicUncompressed, params)
        val peerKey = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(peerPoint, params))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(peerKey, true)
        return agreement.generateSecret()
    }

    private fun ecParams(): ECParameterSpec {
        val generator = java.security.KeyPairGenerator.getInstance("EC")
        generator.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        val publicKey = generator.generateKeyPair().public as java.security.interfaces.ECPublicKey
        return publicKey.params
    }

    private fun decodePoint(uncompressed: ByteArray, params: ECParameterSpec): ECPoint {
        require(uncompressed.size == PushKeys.PUBLIC_KEY_BYTES && uncompressed[0] == 0x04.toByte()) {
            "Peer public key must use uncompressed point form."
        }
        val x = java.math.BigInteger(1, uncompressed.copyOfRange(1, 33))
        val y = java.math.BigInteger(1, uncompressed.copyOfRange(33, 65))
        return ECPoint(x, y)
    }

    private fun hkdfExtract(salt: ByteArray, inputKeyingMaterial: ByteArray): ByteArray {
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        return mac.doFinal(inputKeyingMaterial)
    }

    private fun hkdfExpand(pseudoRandomKey: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
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
