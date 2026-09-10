package jp.example.budsswitch.prefs

import android.content.Context

object AppPrefs {
    private const val FILE = "buds_switch"
    private const val KEY_DEVICE = "selected_device"
    private const val KEY_PEER_CODE = "peer_code"

    fun selectedDevice(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE, null)

    fun setSelectedDevice(context: Context, address: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEVICE, address).apply()
    }

    fun peerCode(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_PEER_CODE, "").orEmpty()

    fun setPeerCode(context: Context, code: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_PEER_CODE, code.trim()).apply()
    }

    fun peerLinkEnabled(context: Context): Boolean = peerCode(context).length >= 6
}
