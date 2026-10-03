package dev.midroid.app.diagnostics

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import dev.midroid.app.power.PowerMode

object RuntimeDiagnostics {
    const val TAG = "MidroidDiag"

    @Volatile
    private var webViewPackageLabel: String = "unknown"

    /**
     * [WebView.getCurrentWebViewPackage] is a binder lookup. Call this once off the
     * main thread at startup; log lines only read [webViewPackageLabel].
     */
    fun prefetchWebViewPackage() {
        webViewPackageLabel = runCatching {
            val webViewPackage = WebView.getCurrentWebViewPackage()
            if (webViewPackage == null) {
                "unknown"
            } else {
                "${webViewPackage.packageName}@${webViewPackage.versionName ?: "unknown"}"
            }
        }.getOrDefault("unknown")
    }

    fun logAppStart(context: Context, mode: PowerMode, rawUrl: String?) {
        Log.i(TAG, "event=app_start ${baseFields(context, mode, rawUrl)}")
    }

    fun logLifecycle(context: Context, event: String, mode: PowerMode, rawUrl: String?) {
        Log.i(TAG, "event=$event ${baseFields(context, mode, rawUrl)}")
    }

    fun logPageReady(context: Context, mode: PowerMode, rawUrl: String?) {
        Log.i(TAG, "event=page_ready ${baseFields(context, mode, rawUrl)}")
    }

    fun logRendererGone(
        context: Context,
        mode: PowerMode,
        rawUrl: String?,
        detail: RenderProcessGoneDetail,
    ) {
        Log.w(
            TAG,
            "event=renderer_gone crash=${detail.didCrash()} " +
                "priorityAtExit=${detail.rendererPriorityAtExit()} ${baseFields(context, mode, rawUrl)}",
        )
    }

    fun buildSnapshot(context: Context, mode: PowerMode, rawUrl: String?): String = buildString {
        appendLine("Midroid diagnostics")
        appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
        append(baseFields(context, mode, rawUrl))
    }

    private fun baseFields(context: Context, mode: PowerMode, rawUrl: String?): String {
        val webView = webViewPackageLabel
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val powerSave = powerManager?.isPowerSaveMode ?: false

        return "mode=${mode.key} origin=${diagnosticOrigin(rawUrl)} " +
            "webview=$webView sdk=${Build.VERSION.SDK_INT} powerSave=$powerSave"
    }
}
