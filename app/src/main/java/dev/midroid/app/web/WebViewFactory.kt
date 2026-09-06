package dev.midroid.app.web

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.StatFs
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.midroid.app.diagnostics.RuntimeDiagnostics

object WebViewFactory {
    fun create(activity: Activity): WebView {
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
        settings.setGeolocationEnabled(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }

        configureCaching(activity, webView)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        val appDebuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(appDebuggable)
        return webView
    }

    private fun configureCaching(activity: Activity, webView: WebView) {
        val settings = webView.settings

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOWNLOAD_FAVICONS_ENABLED)) {
            WebSettingsCompat.setDownloadFaviconsEnabled(settings, false)
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.BACK_FORWARD_CACHE)) {
            WebSettingsCompat.setBackForwardCacheEnabled(settings, true)
        }

        configureServiceWorkerCaching()
        configureHttpCacheQuota(activity, webView)
    }

    private fun configureServiceWorkerCaching() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) return

        runCatching {
            val workerSettings = ServiceWorkerControllerCompat.getInstance().serviceWorkerWebSettings
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_CACHE_MODE)) {
                workerSettings.cacheMode = WebSettings.LOAD_DEFAULT
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BLOCK_NETWORK_LOADS)) {
                workerSettings.blockNetworkLoads = false
            }
        }.onFailure { error ->
            Log.w(RuntimeDiagnostics.TAG, "event=service_worker_cache_config_failed type=${error.javaClass.simpleName}")
        }
    }

    private fun configureHttpCacheQuota(activity: Activity, webView: WebView) {
        val supported =
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.HTTP_CACHE_MANAGER)

        if (!supported) {
            Log.i(RuntimeDiagnostics.TAG, "event=cache_policy httpCacheManager=false")
            return
        }

        runCatching {
            val cache = WebViewCompat.getProfile(webView).httpCache
            val current = cache.quotaBytes
            val defaultQuota = cache.defaultQuotaBytes
            val available = runCatching {
                StatFs(activity.filesDir.absolutePath).availableBytes
            }.getOrDefault(0L)
            val target = WebCachePolicy.targetQuotaBytes(current, defaultQuota, available)

            if (current < target) {
                cache.setQuotaBytes(target)
            }

            val effective = cache.quotaBytes
            Log.i(
                RuntimeDiagnostics.TAG,
                "event=cache_policy httpCacheManager=true currentBytes=$current " +
                    "defaultBytes=$defaultQuota availableBytes=$available targetBytes=$target " +
                    "effectiveBytes=$effective",
            )
        }.onFailure { error ->
            Log.w(
                RuntimeDiagnostics.TAG,
                "event=cache_policy_failed type=${error.javaClass.simpleName}",
            )
        }
    }
}
