package dev.midroid.app.web

import android.app.Activity
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import kotlin.math.roundToInt

object WebViewFactory {
    fun create(activity: Activity, textScalePercent: Int = 100): WebView {
        val webView = WebView(activity)
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.loadsImagesAutomatically = true
        // WebView already maps CSS pixels through the display density. Applying
        // density again would double-scale high-DPI screens; only add font scale.
        settings.textZoom = (activity.resources.configuration.fontScale * textScalePercent).roundToInt()
        settings.setGeolocationEnabled(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        WebView.setWebContentsDebuggingEnabled(false)
        return webView
    }
}
