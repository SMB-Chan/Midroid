package dev.midroid.app

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import dev.midroid.app.config.AppPreferences
import dev.midroid.app.config.InstanceConfig
import dev.midroid.app.power.PowerMode
import dev.midroid.app.power.WebViewPowerController
import dev.midroid.app.ui.SetupScreen
import dev.midroid.app.web.ExternalNavigator
import dev.midroid.app.web.MidroidWebChromeClient
import dev.midroid.app.web.MidroidWebViewClient
import dev.midroid.app.web.WebViewFactory

class MainActivity : Activity() {
    private lateinit var preferences: AppPreferences
    private val powerController = WebViewPowerController()

    private var webView: WebView? = null
    private var browserRoot: FrameLayout? = null
    private var currentInstance: InstanceConfig? = null
    private var currentMode: PowerMode = PowerMode.BALANCED
    private var pendingUrl: String? = null
    private var foreground = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AppPreferences(this)

        currentInstance = preferences.loadInstance()
        currentMode = preferences.loadPowerMode()

        val instance = currentInstance
        if (instance == null) {
            showSetup()
        } else {
            showBrowser(instance.origin)
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true

        if (webView == null && pendingUrl != null && currentInstance != null) {
            showBrowser(pendingUrl!!)
            pendingUrl = null
            return
        }

        webView?.let { powerController.onForeground(it, currentMode) }
    }

    override fun onPause() {
        foreground = false
        webView?.let { powerController.onBackground(it) }
        super.onPause()
    }

    override fun onDestroy() {
        destroyWebView()
        super.onDestroy()
    }

    @Deprecated("Platform back callback retained to avoid an AndroidX dependency in the MVP")
    override fun onBackPressed() {
        val active = webView
        if (active != null && active.canGoBack()) {
            active.goBack()
        } else {
            super.onBackPressed()
        }
    }

    @Deprecated("Used for the platform WebView file chooser without AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val result = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            fileCallback?.onReceiveValue(result)
            fileCallback = null
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showSetup() {
        destroyWebView()
        val screen = SetupScreen(
            activity = this,
            initialUrl = currentInstance?.origin.orEmpty(),
            initialMode = currentMode,
        ) { rawUrl, mode, errorView ->
            val result = InstanceConfig.parse(rawUrl)
            result.onSuccess { instance ->
                currentInstance = instance
                currentMode = mode
                preferences.save(instance, mode)
                showBrowser(instance.origin)
            }.onFailure { error ->
                errorView.text = error.message ?: "Invalid instance URL."
                errorView.visibility = android.view.View.VISIBLE
            }
        }
        setContentView(screen)
    }

    private fun showBrowser(url: String) {
        val instance = currentInstance ?: return
        destroyWebView()

        val root = FrameLayout(this)
        browserRoot = root

        val created = WebViewFactory.create(this)
        webView = created

        powerController.configure(this, created, currentMode)

        val externalNavigator = ExternalNavigator(this)
        created.webViewClient = MidroidWebViewClient(
            instance = instance,
            externalNavigator = externalNavigator,
            onPageReady = { view -> powerController.onPageReady(view, currentMode) },
            onRendererGone = ::handleRendererGone,
        )
        created.webChromeClient = MidroidWebChromeClient(::launchFileChooser)
        created.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(downloadUrl, userAgent, contentDisposition, mimeType)
        }

        root.addView(created, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        val settingsButton = Button(this).apply {
            text = "⚙"
            contentDescription = getString(R.string.settings)
            textSize = 18f
            alpha = 0.72f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xAA202124.toInt())
            minWidth = dp(44)
            minimumWidth = dp(44)
            minHeight = dp(44)
            minimumHeight = dp(44)
            setPadding(0, 0, 0, 0)
            setOnClickListener { showSetup() }
        }
        root.addView(settingsButton, FrameLayout.LayoutParams(dp(44), dp(44)).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(8)
            marginEnd = dp(8)
        })

        setContentView(root)
        created.loadUrl(url)
        if (foreground) powerController.onForeground(created, currentMode)
    }

    private fun handleRendererGone(deadView: WebView, detail: RenderProcessGoneDetail) {
        val restoreUrl = deadView.url ?: currentInstance?.origin
        val root = browserRoot
        root?.removeView(deadView)
        if (webView === deadView) webView = null
        deadView.destroy()

        pendingUrl = restoreUrl
        val reason = if (detail.didCrash()) "Web renderer crashed." else "Web renderer was reclaimed."
        Toast.makeText(this, "$reason Restoring Misskey…", Toast.LENGTH_SHORT).show()

        if (foreground && restoreUrl != null) {
            showBrowser(restoreUrl)
            pendingUrl = null
        }
    }

    private fun launchFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        return try {
            startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST)
            true
        } catch (_: Exception) {
            fileCallback = null
            callback.onReceiveValue(null)
            Toast.makeText(this, "No file picker is available.", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun enqueueDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        val parsed = Uri.parse(url)
        if (!parsed.scheme.equals("https", ignoreCase = true)) {
            Toast.makeText(this, "Blocked non-HTTPS download.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val request = DownloadManager.Request(parsed)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)
            if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            if (!contentDisposition.isNullOrBlank()) {
                request.setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType))
            }

            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "Download started.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Download could not be started.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun destroyWebView() {
        val active = webView ?: return
        browserRoot?.removeView(active)
        active.stopLoading()
        active.webChromeClient = null
        active.webViewClient = android.webkit.WebViewClient()
        active.destroy()
        webView = null
        browserRoot = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val FILE_CHOOSER_REQUEST = 7001
    }
}
