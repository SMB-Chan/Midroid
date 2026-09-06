package dev.midroid.app.web

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.midroid.app.config.InstanceConfig

class MidroidWebViewClient(
    private val instance: InstanceConfig,
    private val externalNavigator: ExternalNavigator,
    private val onPageReady: (WebView) -> Unit,
    private val onRendererGone: (WebView, RenderProcessGoneDetail) -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        return route(request.url)
    }

    @Deprecated("Compatibility for legacy WebView callbacks")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return route(Uri.parse(url))
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageReady(view)
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRendererGone(view, detail)
        return true
    }

    private fun route(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "https" && instance.owns(uri.toString())) {
            return false
        }

        externalNavigator.open(uri)
        return true
    }
}
