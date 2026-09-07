package dev.midroid.app

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.midroid.app.config.AppPreferences
import dev.midroid.app.config.InstanceConfig
import dev.midroid.app.config.TextScale
import dev.midroid.app.diagnostics.RuntimeDiagnostics
import dev.midroid.app.power.PowerMode
import dev.midroid.app.power.WebViewPowerController
import dev.midroid.app.ui.SetupScreen
import dev.midroid.app.web.ExternalNavigator
import dev.midroid.app.web.MidroidWebChromeClient
import dev.midroid.app.web.MidroidWebViewClient
import dev.midroid.app.web.MisskeyUiTuner
import dev.midroid.app.web.NavigationState
import dev.midroid.app.web.WebViewFactory

class MainActivity : ComponentActivity() {
    private lateinit var preferences: AppPreferences
    private val powerController = WebViewPowerController()
    private val navigationState = NavigationState()
    private val uiTuner = MisskeyUiTuner()

    private var webView: WebView? = null
    private var browserRoot: LinearLayout? = null
    private var currentInstance: InstanceConfig? = null
    private var currentMode: PowerMode = PowerMode.BALANCED
    private var currentTextScale: TextScale = TextScale.AUTO
    private var pendingUrl: String? = null
    private var lastKnownUrl: String? = null
    private var visibleToUser = false
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
        enableEdgeToEdge()
        preferences = AppPreferences(this)
        installBackHandler()

        currentInstance = preferences.loadInstance()
        currentMode = preferences.loadPowerMode()
        currentTextScale = preferences.loadTextScale()
        RuntimeDiagnostics.logAppStart(this, currentMode, currentInstance?.origin)

        val instance = currentInstance
        if (instance == null) {
            showSetup()
        } else {
            showBrowser(instance.origin)
        }
    }

    override fun onStart() {
        super.onStart()
        visibleToUser = true
        RuntimeDiagnostics.logLifecycle(
            this,
            "visible",
            currentMode,
            navigationState.currentUrl ?: lastKnownUrl ?: currentInstance?.origin,
        )

        if (webView == null && pendingUrl != null && currentInstance != null) {
            showBrowser(pendingUrl!!)
            pendingUrl = null
            return
        }

        webView?.let { powerController.onForeground(this, it, currentMode) }
    }

    override fun onStop() {
        RuntimeDiagnostics.logLifecycle(
            this,
            "hidden",
            currentMode,
            navigationState.currentUrl ?: lastKnownUrl ?: currentInstance?.origin,
        )
        webView?.let { powerController.onBackground(it) }
        visibleToUser = false
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        webView?.let { view ->
            val textZoom = applyTextScale(view, newConfig)
            uiTuner.applyReactionScale(view, textZoom)
        }
    }

    override fun onDestroy() {
        destroyWebView()
        super.onDestroy()
    }

    private fun installBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val active = webView
                if (active != null && handleInterruptibleWebBack(active)) {
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    private fun handleInterruptibleWebBack(active: WebView): Boolean {
        val canGoBack = active.canGoBack()
        val shouldInterrupt = canGoBack ||
            navigationState.mainFrameLoading ||
            navigationState.isMisskeyLightboxOpen()
        if (!shouldInterrupt) return false

        // Cancel outstanding page/image requests first. Back navigation must never wait for
        // a large media response to finish before the user can leave the current surface.
        val fallbackUrl = navigationState.interruptedReturnUrl()
        active.stopLoading()
        navigationState.onLoadCancelled()

        if (canGoBack) {
            active.goBack()
            return true
        }

        if (!fallbackUrl.isNullOrBlank() && currentInstance?.owns(fallbackUrl) == true) {
            active.loadUrl(fallbackUrl)
            return true
        }

        return false
    }

    private fun showSetup() {
        destroyWebView()
        val screen = SetupScreen(
            activity = this,
            initialUrl = currentInstance?.origin.orEmpty(),
            initialMode = currentMode,
            initialTextScale = currentTextScale,
            onCopyDiagnostics = ::copyDiagnostics,
        ) { rawUrl, mode, textScale ->
            InstanceConfig.parse(rawUrl).fold(
                onSuccess = { instance ->
                    currentInstance = instance
                    currentMode = mode
                    currentTextScale = textScale
                    preferences.save(instance, mode, textScale)
                    showBrowser(instance.origin)
                    null
                },
                onFailure = { error ->
                    error.message ?: "Invalid instance URL."
                },
            )
        }
        setInsetContentView(screen)
    }

    private fun copyDiagnostics() {
        val text = RuntimeDiagnostics.buildSnapshot(
            this,
            currentMode,
            navigationState.currentUrl ?: lastKnownUrl ?: currentInstance?.origin,
        )
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Midroid diagnostics", text))
        Toast.makeText(this, "Diagnostics copied.", Toast.LENGTH_SHORT).show()
    }

    private fun showBrowser(url: String) {
        val instance = currentInstance ?: return
        destroyWebView()
        lastKnownUrl = url
        navigationState.reset(url)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        browserRoot = root

        val created = WebViewFactory.create(this)
        webView = created

        applyTextScale(created)
        powerController.configure(this, created, currentMode)

        val externalNavigator = ExternalNavigator(this)
        created.webViewClient = MidroidWebViewClient(
            instance = instance,
            externalNavigator = externalNavigator,
            onMainFrameLoadStarted = { navigationState.onPageStarted(it) },
            onMainFrameCommitted = { committedUrl ->
                navigationState.onPageCommitted(committedUrl)
                lastKnownUrl = committedUrl
            },
            onHistoryChanged = { navigationState.onHistoryChanged(it) },
            onPageReady = { view, finishedUrl ->
                navigationState.onPageFinished(finishedUrl)
                RuntimeDiagnostics.logPageReady(this, currentMode, navigationState.currentUrl ?: lastKnownUrl)
                powerController.onPageReady(this, view, currentMode)
                uiTuner.applyReactionScale(view, resolveTextZoom())
            },
            onRendererGone = ::handleRendererGone,
        )
        created.webChromeClient = MidroidWebChromeClient(::launchFileChooser)
        created.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(downloadUrl, userAgent, contentDisposition, mimeType)
        }

        val settingsButton = Button(this).apply {
            text = "⚙"
            contentDescription = getString(R.string.settings)
            textSize = 18f
            alpha = 0.72f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xAA202124.toInt())
            minWidth = dp(48)
            minimumWidth = dp(48)
            minHeight = dp(48)
            minimumHeight = dp(48)
            setPadding(0, 0, 0, 0)
            setOnClickListener { showSetup() }
        }

        // Keep native controls out of Misskey's own top navigation. The previous overlay
        // occupied the same pixels as Misskey tabs and could intercept their touch targets.
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.app_name)
                textSize = 16f
                setPadding(dp(16), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(settingsButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        root.addView(
            toolbar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)),
        )
        root.addView(
            created,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        setInsetContentView(root)
        created.loadUrl(url)
        if (visibleToUser) powerController.onForeground(this, created, currentMode)
    }

    private fun resolveTextZoom(configuration: Configuration = resources.configuration): Int {
        val densityDpi = resources.displayMetrics.densityDpi
        val screenWidthDp = configuration.screenWidthDp
        return currentTextScale.resolveTextZoom(densityDpi, screenWidthDp)
    }

    private fun applyTextScale(
        view: WebView,
        configuration: Configuration = resources.configuration,
    ): Int {
        val textZoom = resolveTextZoom(configuration)
        view.settings.textZoom = textZoom
        return textZoom
    }

    private fun handleRendererGone(deadView: WebView, detail: RenderProcessGoneDetail) {
        val restoreUrl = navigationState.lastCommittedUrl ?: lastKnownUrl ?: currentInstance?.origin
        RuntimeDiagnostics.logRendererGone(this, currentMode, restoreUrl, detail)

        val root = browserRoot
        root?.removeView(deadView)
        if (webView === deadView) webView = null
        deadView.destroy()

        pendingUrl = restoreUrl
        val reason = if (detail.didCrash()) "Web renderer crashed." else "Web renderer was reclaimed."
        Toast.makeText(this, "$reason Restoring Misskey…", Toast.LENGTH_SHORT).show()

        if (visibleToUser && restoreUrl != null) {
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

    private fun setInsetContentView(content: View) {
        val container = FrameLayout(this)
        container.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime(),
            )
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            // This native container owns the safe area; do not inset the WebView again.
            WindowInsetsCompat.CONSUMED
        }
        setContentView(container)
        ViewCompat.requestApplyInsets(container)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
