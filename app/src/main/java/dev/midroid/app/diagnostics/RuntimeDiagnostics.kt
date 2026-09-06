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

    private fun baseFields(context: Context, mode: PowerMode, rawUrl: String?): String {
        val webViewPackage = WebView.getCurrentWebViewPackage()
        val webView = if (webViewPackage == null) {
            "unknown"
        } else {
            "${webViewPackage.packageName}@${webViewPackage.versionName ?: "unknown"}"
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val powerSave = powerManager?.isPowerSaveMode ?: false

        return "mode=${mode.key} origin=${diagnosticOrigin(rawUrl)} " +
            "webview=$webView sdk=${Build.VERSION.SDK_INT} powerSave=$powerSave"
    }
}
