package dev.midroid.app.push

import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object PushKeyRotation {
    fun rotate(
        store: PushKeyStore,
        subscriptions: MutableList<PushSubscription>,
        accountId: String,
        instanceOrigin: String,
        endpoint: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Rotation {
        require(accountId.isNotBlank()) { "Account id must not be blank." }
        val endpointId = RelayProtocol.parseEndpointId(endpoint)
            ?: throw IllegalArgumentException("Rotation endpoint must be a versioned relay URL.")
        val previous = subscriptions.firstOrNull { it.accountId == accountId }
        store.delete(accountId)
        val keys = store.create(accountId)
        val rotated = PushSubscription(
            accountId = accountId,
            instanceOrigin = instanceOrigin,
            endpoint = endpoint,
            p256dhBase64Url = PushKeys.base64UrlNoPad(keys.publicUncompressed),
            authBase64Url = PushKeys.base64UrlNoPad(keys.authSecret),
            endpointId = endpointId,
            createdAtEpochMs = previous?.createdAtEpochMs ?: nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
        )
        subscriptions.removeAll { it.accountId == accountId }
        subscriptions.add(rotated)
        return Rotation(previous?.endpoint, rotated, keys)
    }

    fun revoke(
        store: PushKeyStore,
        subscriptions: MutableList<PushSubscription>,
        accountId: String,
    ): String? {
        val previous = subscriptions.firstOrNull { it.accountId == accountId }
        subscriptions.removeAll { it.accountId == accountId }
        store.delete(accountId)
        return previous?.endpoint
    }

    data class Rotation(
        val supersededEndpoint: String?,
        val current: PushSubscription,
        val keys: StoredKeys,
    )
}

object PushKeySealTestHelper {
    fun generateWrappingKey(): SecretKey {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256, SecureRandom())
        return generator.generateKey()
    }
}
