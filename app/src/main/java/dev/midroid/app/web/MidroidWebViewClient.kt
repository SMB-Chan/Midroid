package dev.midroid.app.web

import android.graphics.Bitmap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.midroid.app.config.InstanceConfig

class MidroidWebViewClient(
    private val instance: InstanceConfig,
    private val externalNavigator: ExternalNavigator,
    private val onMainFrameUrlChanged: (String) -> Unit,
    private val onPageReady: (WebView) -> Unit,
    private val onRendererGone: (WebView, RenderProcessGoneDetail) -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        val uri = request.url
        val scheme = uri.scheme?.lowercase()
        if (scheme == "https" && instance.owns(uri.toString())) {
            return false
        }

        externalNavigator.open(uri)
        return true
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onMainFrameUrlChanged(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageReady(view)
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRendererGone(view, detail)
        return true
    }
}
