package dev.midroid.app.web

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast

class ExternalNavigator(private val activity: Activity) {
    fun open(uri: Uri) {
        val scheme = uri.scheme?.lowercase()
        if (scheme !in ALLOWED_SCHEMES) {
            Toast.makeText(activity, "Blocked unsupported link scheme.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(intent)
        } else {
            Toast.makeText(activity, "No app can open this link.", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val ALLOWED_SCHEMES = setOf("https", "http", "mailto", "tel", "geo", "market")
    }
}
