package dev.midroid.app.web

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale

class ExternalNavigator(private val activity: Activity) {
    fun open(uri: Uri) {
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme !in ALLOWED_SCHEMES) {
            Toast.makeText(activity, "Blocked unsupported link scheme.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        // Starting the implicit-intent flow directly is more reliable than preflighting with
        // resolveActivity() on Android 11+, where package visibility can hide valid handlers.
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, "No app can open this link.", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(activity, "This link cannot be opened safely.", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val ALLOWED_SCHEMES = setOf("https", "http", "mailto", "tel", "geo", "market")
    }
}
