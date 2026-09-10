package jp.example.budsswitch.prefs

import android.content.Context

object AppPrefs {
    private const val FILE = "buds_switch"
    private const val KEY_DEVICE = "selected_device"

    fun selectedDevice(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE, null)

    fun setSelectedDevice(context: Context, address: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEVICE, address).apply()
    }
}
