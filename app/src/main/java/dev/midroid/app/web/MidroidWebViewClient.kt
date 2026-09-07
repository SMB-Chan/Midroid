package dev.midroid.app.web

import android.graphics.Bitmap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.midroid.app.config.InstanceConfig
import dev.midroid.app.media.NativeAudioRequest
import java.util.Locale

class MidroidWebViewClient(
    private val instance: InstanceConfig,
    private val externalNavigator: ExternalNavigator,
    private val onMainFrameLoadStarted: (String) -> Unit,
    private val onMainFrameCommitted: (String) -> Unit,
    private val onHistoryChanged: (String) -> Unit,
    private val onPageReady: (WebView, String) -> Unit,
    private val onNativeAudioRequested: (NativeAudioRequest) -> Unit,
    private val onRendererGone: (WebView, RenderProcessGoneDetail) -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        val scheme = uri.scheme?.lowercase(Locale.ROOT)

        if (scheme == NativeAudioRequest.SCHEME) {
            if (request.isForMainFrame) {
                NativeAudioRequest.parse(uri.toString())?.let(onNativeAudioRequested)
            }
            // Always consume the private Midroid scheme, including malformed requests and
            // sub-frame attempts. Native UI may only be triggered by the main frame.
            return true
        }

        if (!request.isForMainFrame) return false
        if (scheme == "https" && instance.owns(uri.toString())) {
            return false
        }

        externalNavigator.open(uri)
        return true
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onMainFrameLoadStarted(url)
    }

    override fun onPageCommitVisible(view: WebView, url: String) {
        super.onPageCommitVisible(view, url)
        onMainFrameCommitted(url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        onHistoryChanged(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageReady(view, url)
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRendererGone(view, detail)
        return true
    }
}
