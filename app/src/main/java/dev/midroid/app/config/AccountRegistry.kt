package dev.midroid.app.config

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AccountRegistry(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun loadOrMigrate(legacyInstance: InstanceConfig?): List<AccountProfile> {
        val stored = decodeAccounts(preferences.getString(KEY_ACCOUNTS, null))
        if (stored.isNotEmpty()) return stored
        if (legacyInstance == null) return emptyList()

        val migrated = listOf(AccountProfile.legacyDefault(legacyInstance))
        saveAccounts(migrated)
        preferences.edit().putString(KEY_ACTIVE_ID, migrated.first().id).apply()
        return migrated
    }

    fun activeAccount(accounts: List<AccountProfile>): AccountProfile? {
        if (accounts.isEmpty()) return null
        val activeId = preferences.getString(KEY_ACTIVE_ID, null)
        return accounts.firstOrNull { it.id == activeId } ?: accounts.first()
    }

    fun addDefault(instance: InstanceConfig): AccountProfile {
        val existing = loadAccounts()
        require(existing.isEmpty()) { "The default WebView profile is already assigned." }
        val account = AccountProfile.firstDefault(instance)
        saveAccounts(listOf(account))
        setActive(account.id)
        return account
    }

    fun addIsolated(instance: InstanceConfig): AccountProfile {
        val accounts = loadAccounts().toMutableList()
        val base = AccountProfile.hostLabel(instance.origin)
        val sameHostCount = accounts.count {
            AccountProfile.hostLabel(it.instanceOrigin).equals(base, ignoreCase = true)
        }
        val label = if (sameHostCount == 0) base else "$base #${sameHostCount + 1}"
        val account = AccountProfile.isolated(instance, label)
        accounts += account
        saveAccounts(accounts)
        setActive(account.id)
        return account
    }

    fun setActive(accountId: String) {
        preferences.edit().putString(KEY_ACTIVE_ID, accountId).apply()
    }

    fun updateLastUrl(accountId: String, rawUrl: String) {
        val accounts = loadAccounts()
        val updated = accounts.map { account ->
            if (account.id != accountId) return@map account
            val instance = account.instanceConfig() ?: return@map account
            if (!instance.owns(rawUrl)) return@map account
            account.copy(lastUrl = rawUrl)
        }
        if (updated != accounts) saveAccounts(updated)
    }

    fun loadAccounts(): List<AccountProfile> =
        decodeAccounts(preferences.getString(KEY_ACCOUNTS, null))

    private fun saveAccounts(accounts: List<AccountProfile>) {
        val array = JSONArray()
        accounts.forEach { account ->
            val item = JSONObject()
                .put("id", account.id)
                .put("instanceOrigin", account.instanceOrigin)
                .put("label", account.label)
                .put("lastUrl", account.lastUrl)
            account.profileName?.let { item.put("profileName", it) }
            array.put(item)
        }
        preferences.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }

    private fun decodeAccounts(raw: String?): List<AccountProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val origin = item.getString("instanceOrigin")
                    val instance = InstanceConfig.parse(origin).getOrNull() ?: continue
                    val id = item.getString("id").takeIf { it.isNotBlank() } ?: continue
                    val profileName = item.optString("profileName", "").takeIf { it.isNotBlank() }
                    val label = item.optString("label", AccountProfile.hostLabel(instance.origin))
                    val lastUrl = item.optString("lastUrl", instance.origin)
                    add(
                        AccountProfile(
                            id = id,
                            profileName = profileName,
                            instanceOrigin = instance.origin,
                            label = label,
                            lastUrl = lastUrl.takeIf(instance::owns) ?: instance.origin,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val NAME = "midroid_accounts"
        private const val KEY_ACCOUNTS = "accounts_json"
        private const val KEY_ACTIVE_ID = "active_account_id"
    }
}
