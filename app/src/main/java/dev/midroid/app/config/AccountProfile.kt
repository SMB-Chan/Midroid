package dev.midroid.app.config

import java.net.URI
import java.util.UUID

data class AccountProfile(
    val id: String,
    val profileName: String?,
    val instanceOrigin: String,
    val label: String,
    val lastUrl: String,
) {
    fun instanceConfig(): InstanceConfig? = InstanceConfig.parse(instanceOrigin).getOrNull()

    fun displayLabel(): String = label.ifBlank { hostLabel(instanceOrigin) }

    fun restoreUrl(): String {
        val instance = instanceConfig() ?: return instanceOrigin
        return lastUrl.takeIf(instance::owns) ?: instance.origin
    }

    companion object {
        const val LEGACY_DEFAULT_ID = "legacy-default"

        fun legacyDefault(instance: InstanceConfig): AccountProfile = AccountProfile(
            id = LEGACY_DEFAULT_ID,
            profileName = null,
            instanceOrigin = instance.origin,
            label = hostLabel(instance.origin),
            lastUrl = instance.origin,
        )

        fun firstDefault(instance: InstanceConfig): AccountProfile = AccountProfile(
            id = UUID.randomUUID().toString(),
            profileName = null,
            instanceOrigin = instance.origin,
            label = hostLabel(instance.origin),
            lastUrl = instance.origin,
        )

        fun isolated(
            instance: InstanceConfig,
            label: String,
        ): AccountProfile {
            val id = UUID.randomUUID().toString()
            return AccountProfile(
                id = id,
                profileName = "midroid_${id.replace("-", "")}",
                instanceOrigin = instance.origin,
                label = label,
                lastUrl = instance.origin,
            )
        }

        fun hostLabel(origin: String): String = runCatching {
            URI(origin).host?.takeIf { it.isNotBlank() } ?: origin
        }.getOrDefault(origin)
    }
}
