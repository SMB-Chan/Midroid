package dev.midroid.app.push

import android.content.Context
import dev.midroid.app.config.InstanceConfig
import org.json.JSONArray
import org.json.JSONObject

data class PushSubscription(
    val accountId: String,
    val instanceOrigin: String,
    val endpoint: String,
    val p256dhBase64Url: String,
    val authBase64Url: String,
    val endpointId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    init {
        require(accountId.isNotBlank()) { "Account id must not be blank." }
        require(RelayProtocol.parseEndpointId(endpoint) == endpointId) {
            "Endpoint id must match the relay endpoint URL."
        }
    }
}

object PushSubscriptionJson {
    fun encode(subscription: PushSubscription): Map<String, Any> = mapOf(
        "accountId" to subscription.accountId,
        "instanceOrigin" to subscription.instanceOrigin,
        "endpoint" to subscription.endpoint,
        "p256dh" to subscription.p256dhBase64Url,
        "auth" to subscription.authBase64Url,
        "endpointId" to subscription.endpointId,
        "createdAt" to subscription.createdAtEpochMs,
        "updatedAt" to subscription.updatedAtEpochMs,
    )

    fun decode(raw: Map<String, Any?>): PushSubscription? {
        val accountId = (raw["accountId"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val origin = raw["instanceOrigin"] as? String ?: return null
        val instance = InstanceConfig.parse(origin).getOrNull() ?: return null
        val endpoint = raw["endpoint"] as? String ?: return null
        val endpointId = RelayProtocol.parseEndpointId(endpoint) ?: return null
        if ((raw["endpointId"] as? String ?: endpointId) != endpointId) return null
        val p256dh = (raw["p256dh"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val auth = (raw["auth"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        return PushSubscription(
            accountId = accountId,
            instanceOrigin = instance.origin,
            endpoint = endpoint,
            p256dhBase64Url = p256dh,
            authBase64Url = auth,
            endpointId = endpointId,
            createdAtEpochMs = (raw["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAtEpochMs = (raw["updatedAt"] as? Number)?.toLong() ?: 0L,
        )
    }

    fun toJsonObject(fields: Map<String, Any>): JSONObject = JSONObject().also { json ->
        fields.forEach { (key, value) -> json.put(key, value) }
    }

    fun fromJsonObject(raw: JSONObject): PushSubscription? {
        val fields = mutableMapOf<String, Any?>()
        val keys = raw.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            fields[key] = raw.opt(key)
        }
        return decode(fields)
    }

    fun encodeAll(subscriptions: List<PushSubscription>): String {
        val array = JSONArray()
        subscriptions.forEach { array.put(toJsonObject(encode(it))) }
        return array.toString()
    }

    fun decodeAll(raw: String?): List<PushSubscription> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    fromJsonObject(array.getJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }
}

class PushSubscriptionStore(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun loadAll(): List<PushSubscription> =
        PushSubscriptionJson.decodeAll(preferences.getString(KEY_SUBSCRIPTIONS, null))

    fun findByAccountId(accountId: String): PushSubscription? =
        loadAll().firstOrNull { it.accountId == accountId }

    fun upsert(subscription: PushSubscription) {
        val updated = loadAll().filterNot { it.accountId == subscription.accountId } + subscription
        saveAll(updated)
    }

    fun removeByAccountId(accountId: String) {
        val updated = loadAll().filterNot { it.accountId == accountId }
        saveAll(updated)
    }

    private fun saveAll(subscriptions: List<PushSubscription>) {
        preferences.edit()
            .putString(KEY_SUBSCRIPTIONS, PushSubscriptionJson.encodeAll(subscriptions))
            .apply()
    }

    companion object {
        private const val NAME = "midroid_push"
        private const val KEY_SUBSCRIPTIONS = "subscriptions_json"
    }
}
