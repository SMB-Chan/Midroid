package dev.midroid.app.push

import java.security.MessageDigest

data class ReceiverKeyPair(
    val publicUncompressed: ByteArray,
    val privateScalar: ByteArray,
) {
    init {
        require(publicUncompressed.size == PushKeys.PUBLIC_KEY_BYTES) {
            "Receiver public key must be 65 uncompressed bytes."
        }
        require(publicUncompressed.first() == 0x04.toByte()) {
            "Receiver public key must use uncompressed point form."
        }
        require(privateScalar.size == PushKeys.PRIVATE_KEY_BYTES) {
            "Receiver private scalar must be 32 bytes."
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReceiverKeyPair) return false
        return MessageDigest.isEqual(publicUncompressed, other.publicUncompressed) &&
            MessageDigest.isEqual(privateScalar, other.privateScalar)
    }

    override fun hashCode(): Int =
        31 * publicUncompressed.contentHashCode() + privateScalar.contentHashCode()

    override fun toString(): String =
        "ReceiverKeyPair(fingerprint=${PushKeys.fingerprintSHA256(publicUncompressed).take(16)}…)"
}
