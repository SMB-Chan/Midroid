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

    fun loadTextScale(): TextScale {
        return TextScale.fromKey(preferences.getString(KEY_TEXT_SCALE, null))
    }

    fun save(instance: InstanceConfig, mode: PowerMode, textScale: TextScale) {
        preferences.edit()
            .putString(KEY_INSTANCE, instance.origin)
            .putString(KEY_POWER_MODE, mode.key)
            .putString(KEY_TEXT_SCALE, textScale.key)
            .apply()
    }

    companion object {
        private const val NAME = "midroid_preferences"
        private const val KEY_INSTANCE = "instance_origin"
        private const val KEY_POWER_MODE = "power_mode"
        private const val KEY_TEXT_SCALE = "text_scale"
    }
}
