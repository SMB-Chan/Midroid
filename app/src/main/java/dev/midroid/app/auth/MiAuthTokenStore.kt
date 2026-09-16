package dev.midroid.app.auth

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object MiAuthTokenJson {
    fun encodeAll(tokens: Map<String, String>): String {
        val array = JSONArray()
        tokens.forEach { (accountId, token) ->
            array.put(JSONObject().put("accountId", accountId).put("token", token))
        }
        return array.toString()
    }

    fun decodeAll(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val array = JSONArray(raw)
            buildMap {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val accountId = item.optString("accountId", "").takeIf { it.isNotBlank() }
                        ?: continue
                    val token = item.optString("token", "").takeIf { it.isNotBlank() }
                        ?: continue
                    put(accountId, token)
                }
            }
        }.getOrDefault(emptyMap())
    }
}

class MiAuthTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(accountId: String): String? = loadAll()[accountId]

    fun save(accountId: String, token: String) {
        require(accountId.isNotBlank()) { "Account id must not be blank." }
        require(token.isNotBlank()) { "Token must not be blank." }
        val updated = loadAll().toMutableMap()
        updated[accountId] = token
        saveAll(updated)
    }

    fun delete(accountId: String) {
        val updated = loadAll().toMutableMap()
        if (updated.remove(accountId) != null) saveAll(updated)
    }

    fun hasToken(accountId: String): Boolean = load(accountId) != null

    private fun loadAll(): Map<String, String> =
        MiAuthTokenJson.decodeAll(preferences.getString(KEY_TOKENS, null))

    private fun saveAll(tokens: Map<String, String>) {
        preferences.edit()
            .putString(KEY_TOKENS, MiAuthTokenJson.encodeAll(tokens))
            .apply()
    }

    override fun toString(): String = "MiAuthTokenStore(accounts=${loadAll().size})"

    companion object {
        private const val NAME = "midroid_miauth"
        private const val KEY_TOKENS = "tokens_json"
    }
}
