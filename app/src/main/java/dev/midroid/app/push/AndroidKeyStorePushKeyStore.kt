package dev.midroid.app.push

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeyStorePushKeyStore(
    context: Context,
    private val random: SecureRandom = SecureRandom(),
) : PushKeyStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun create(accountId: String): StoredKeys {
        require(accountId.isNotBlank()) { "Account id must not be blank." }
        delete(accountId)
        val pair = PushKeys.generateKeyPair(random)
        val authSecret = PushKeys.generateAuthSecret(random)
        val stored = StoredKeys(
            accountId = accountId,
            publicUncompressed = pair.publicUncompressed,
            privateScalar = pair.privateScalar,
            authSecret = authSecret,
        )
        saveSealed(accountId, KeyMaterialSeal.seal(wrappingKey(accountId), stored, random))
        return stored
    }

    override fun load(accountId: String): StoredKeys? {
        val sealed = loadSealed(accountId) ?: return null
        return runCatching {
            KeyMaterialSeal.open(wrappingKey(accountId), accountId, sealed)
        }.getOrNull()
    }

    override fun delete(accountId: String) {
        preferences.edit()
            .remove(keyFor(accountId))
            .apply()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            keyStore.deleteEntry(wrappingAlias(accountId))
        }
    }

    private fun wrappingKey(accountId: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getEntry(wrappingAlias(accountId), null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val builder = KeyGenParameterSpec.Builder(
            wrappingAlias(accountId),
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true)
        }
        generator.init(builder.build(), random)
        return generator.generateKey()
    }

    private fun saveSealed(accountId: String, sealed: SealedKeyMaterial) {
        preferences.edit()
            .putString(keyFor(accountId), KeyMaterialSeal.encode(sealed))
            .apply()
    }

    private fun loadSealed(accountId: String): SealedKeyMaterial? {
        val raw = preferences.getString(keyFor(accountId), null) ?: return null
        return KeyMaterialSeal.decode(raw)
    }

    private fun keyFor(accountId: String): String = "$KEY_PREFIX$accountId"

    private fun wrappingAlias(accountId: String): String = "$WRAPPING_ALIAS_PREFIX$accountId"

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val NAME = "midroid_push_keys"
        private const val KEY_PREFIX = "sealed_"
        private const val WRAPPING_ALIAS_PREFIX = "midroid-push-wrap-"
    }
}

internal data class SealedKeyMaterial(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

internal object KeyMaterialSeal {
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    fun seal(wrappingKey: SecretKey, stored: StoredKeys, random: SecureRandom): SealedKeyMaterial {
        val iv = ByteArray(IV_BYTES)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(stored.accountId.toByteArray(Charsets.UTF_8))
        val plaintext = stored.publicUncompressed + stored.privateScalar + stored.authSecret
        return SealedKeyMaterial(iv, cipher.doFinal(plaintext))
    }

    fun open(wrappingKey: SecretKey, accountId: String, sealed: SealedKeyMaterial): StoredKeys {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, sealed.iv))
        cipher.updateAAD(accountId.toByteArray(Charsets.UTF_8))
        val plaintext = cipher.doFinal(sealed.ciphertext)
        val publicKey = plaintext.copyOfRange(0, PushKeys.PUBLIC_KEY_BYTES)
        val privateKey = plaintext.copyOfRange(
            PushKeys.PUBLIC_KEY_BYTES,
            PushKeys.PUBLIC_KEY_BYTES + PushKeys.PRIVATE_KEY_BYTES,
        )
        val authSecret = plaintext.copyOfRange(
            PushKeys.PUBLIC_KEY_BYTES + PushKeys.PRIVATE_KEY_BYTES,
            PushKeys.PUBLIC_KEY_BYTES + PushKeys.PRIVATE_KEY_BYTES + PushKeys.AUTH_SECRET_BYTES,
        )
        require(plaintext.size == PushKeys.PUBLIC_KEY_BYTES + PushKeys.PRIVATE_KEY_BYTES + PushKeys.AUTH_SECRET_BYTES) {
            "Sealed key material has an unexpected length."
        }
        return StoredKeys(accountId, publicKey, privateKey, authSecret)
    }

    fun encode(sealed: SealedKeyMaterial): String {
        val combined = sealed.iv + sealed.ciphertext
        return java.util.Base64.getEncoder().encodeToString(combined)
    }

    fun decode(raw: String?): SealedKeyMaterial? = runCatching {
        require(!raw.isNullOrBlank()) { "Missing sealed material." }
        val combined = java.util.Base64.getDecoder().decode(raw)
        require(combined.size > IV_BYTES) { "Sealed material is too short." }
        SealedKeyMaterial(combined.copyOfRange(0, IV_BYTES), combined.copyOfRange(IV_BYTES, combined.size))
    }.getOrNull()
}
