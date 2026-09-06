package dev.midroid.app.config

import android.content.Context
import dev.midroid.app.power.PowerMode

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun loadInstance(): InstanceConfig? {
        val origin = preferences.getString(KEY_INSTANCE, null) ?: return null
        return InstanceConfig.parse(origin).getOrNull()
    }

    fun loadPowerMode(): PowerMode {
        return PowerMode.fromKey(preferences.getString(KEY_POWER_MODE, null))
    }

    fun save(instance: InstanceConfig, mode: PowerMode) {
        preferences.edit()
            .putString(KEY_INSTANCE, instance.origin)
            .putString(KEY_POWER_MODE, mode.key)
            .apply()
    }

    companion object {
        private const val NAME = "midroid_preferences"
        private const val KEY_INSTANCE = "instance_origin"
        private const val KEY_POWER_MODE = "power_mode"
    }
}
