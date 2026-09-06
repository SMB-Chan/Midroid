package dev.midroid.app

import android.app.DownloadManager
import android.content.Context
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
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import dev.midroid.app.config.AppPreferences
import dev.midroid.app.config.InstanceConfig
import dev.midroid.app.diagnostics.RuntimeDiagnostics
import dev.midroid.app.power.PowerMode
import dev.midroid.app.power.WebViewPowerController
import dev.midroid.app.ui.SetupScreen
import dev.midroid.app.web.ExternalNavigator
import dev.midroid.app.web.MidroidWebChromeClient
import dev.midroid.app.web.MidroidWebViewClient
import dev.midroid.app.web.WebViewFactory

class MainActivity : ComponentActivity() {
    private lateinit var preferences: AppPreferences
    private val powerController = WebViewPowerController()

    private var webView: WebView? = null
    private var browserRoot: FrameLayout? = null
    private var currentInstance: InstanceConfig? = null
    private var currentMode: PowerMode = PowerMode.BALANCED
    private var pendingUrl: String? = null
    private var lastKnownUrl: String? = null
    private var foreground = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AppPreferences(this)
        installBackHandler()

        currentInstance = preferences.loadInstance()
        currentMode = preferences.loadPowerMode()
        RuntimeDiagnostics.logAppStart(this, currentMode, currentInstance?.origin)

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
        RuntimeDiagnostics.logLifecycle(
            this,
            "foreground",
            currentMode,
            lastKnownUrl ?: currentInstance?.origin,
        )

        if (webView == null && pendingUrl != null && currentInstance != null) {
            showBrowser(pendingUrl!!)
            pendingUrl = null
            return
        }

        webView?.let { powerController.onForeground(it, currentMode) }
    }

    override fun onPause() {
        foreground = false
        RuntimeDiagnostics.logLifecycle(
            this,
            "background",
            currentMode,
            lastKnownUrl ?: currentInstance?.origin,
        )
        webView?.let { powerController.onBackground(it) }
        super.onPause()
    }

    override fun onDestroy() {
        destroyWebView()
        super.onDestroy()
    }

    private fun installBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val active = webView
                if (active != null && active.canGoBack()) {
                    active.goBack()
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
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
        lastKnownUrl = url

        val root = FrameLayout(this)
        browserRoot = root

        val created = WebViewFactory.create(this)
        webView = created

        powerController.configure(this, created, currentMode)

        val externalNavigator = ExternalNavigator(this)
        created.webViewClient = MidroidWebViewClient(
            instance = instance,
            externalNavigator = externalNavigator,
            onMainFrameUrlChanged = { lastKnownUrl = it },
            onPageReady = { view ->
                RuntimeDiagnostics.logPageReady(this, currentMode, lastKnownUrl)
                powerController.onPageReady(view, currentMode)
            },
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
        val restoreUrl = lastKnownUrl ?: currentInstance?.origin
        RuntimeDiagnostics.logRendererGone(this, currentMode, restoreUrl, detail)

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
            fileChooserLauncher.launch(params.createIntent())
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
}
