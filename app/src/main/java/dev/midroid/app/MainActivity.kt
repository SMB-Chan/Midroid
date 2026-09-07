package dev.midroid.app

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
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
import androidx.webkit.WebViewFeature
import dev.midroid.app.config.AccountProfile
import dev.midroid.app.config.AccountRegistry
import dev.midroid.app.config.AppPreferences
import dev.midroid.app.config.InstanceConfig
import dev.midroid.app.config.ReactionScale
import dev.midroid.app.config.TextScale
import dev.midroid.app.diagnostics.RuntimeDiagnostics
import dev.midroid.app.media.NativeAudioPlayerDialog
import dev.midroid.app.media.NativeAudioRequest
import dev.midroid.app.power.PowerMode
import dev.midroid.app.power.WebViewPowerController
import dev.midroid.app.ui.SetupScreen
import dev.midroid.app.web.ExternalNavigator
import dev.midroid.app.web.MidroidWebChromeClient
import dev.midroid.app.web.MidroidWebViewClient
import dev.midroid.app.web.MisskeyMediaFallbackBridge
import dev.midroid.app.web.MisskeyUiTuner
import dev.midroid.app.web.NavigationState
import dev.midroid.app.web.WebViewFactory

class MainActivity : ComponentActivity() {
    private lateinit var preferences: AppPreferences
    private lateinit var accountRegistry: AccountRegistry
    private val powerController = WebViewPowerController()
    private val navigationState = NavigationState()
    private val uiTuner = MisskeyUiTuner()
    private val mediaFallbackBridge = MisskeyMediaFallbackBridge()

    private var accounts: List<AccountProfile> = emptyList()
    private var currentAccount: AccountProfile? = null
    private var webView: WebView? = null
    private var browserRoot: FrameLayout? = null
    private var nativeAudioPlayer: NativeAudioPlayerDialog? = null
    private var currentInstance: InstanceConfig? = null
    private var currentMode: PowerMode = PowerMode.BALANCED
    private var currentTextScale: TextScale = TextScale.AUTO
    private var currentReactionScale: ReactionScale = ReactionScale.LARGE
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
        accountRegistry = AccountRegistry(this)
        installBackHandler()

        currentMode = preferences.loadPowerMode()
        currentTextScale = preferences.loadTextScale()
        currentReactionScale = preferences.loadReactionScale()

        accounts = accountRegistry.loadOrMigrate(preferences.loadInstance())
        currentAccount = accountRegistry.activeAccount(accounts)
        currentInstance = currentAccount?.instanceConfig()

        RuntimeDiagnostics.logAppStart(this, currentMode, currentInstance?.origin)

        val account = currentAccount
        if (account == null || currentInstance == null) {
            showSetup()
        } else {
            showBrowser(account.restoreUrl())
        }
    }

    override fun onStart() {
        super.onStart()
        visibleToUser = true
        nativeAudioPlayer?.onHostStarted()
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
        persistCurrentUrl()
        RuntimeDiagnostics.logLifecycle(
            this,
            "hidden",
            currentMode,
            navigationState.currentUrl ?: lastKnownUrl ?: currentInstance?.origin,
        )
        nativeAudioPlayer?.onHostStopped()
        webView?.let { powerController.onBackground(it) }
        visibleToUser = false
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        webView?.let { view ->
            applyTextScale(view, newConfig)
            uiTuner.applyReactionScale(view, currentReactionScale)
            mediaFallbackBridge.install(view)
        }
    }

    override fun onDestroy() {
        persistCurrentUrl()
        nativeAudioPlayer?.dismiss()
        nativeAudioPlayer = null
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
        persistCurrentUrl()
        nativeAudioPlayer?.dismiss()
        nativeAudioPlayer = null
        destroyWebView()

        val editingExisting = currentAccount != null
        val screen = SetupScreen(
            activity = this,
            initialUrl = currentInstance?.origin.orEmpty(),
            initialMode = currentMode,
            initialTextScale = currentTextScale,
            initialReactionScale = currentReactionScale,
            onCopyDiagnostics = ::copyDiagnostics,
            instanceEditable = !editingExisting,
        ) { rawUrl, mode, textScale, reactionScale ->
            InstanceConfig.parse(rawUrl).fold(
                onSuccess = { parsedInstance ->
                    val account = if (editingExisting) {
                        currentAccount ?: return@fold getString(R.string.invalid_instance_url)
                    } else {
                        accountRegistry.addDefault(parsedInstance).also {
                            accounts = accountRegistry.loadAccounts()
                            currentAccount = it
                        }
                    }
                    val instance = account.instanceConfig()
                        ?: return@fold getString(R.string.invalid_instance_url)

                    currentInstance = instance
                    currentMode = mode
                    currentTextScale = textScale
                    currentReactionScale = reactionScale
                    preferences.save(instance, mode, textScale, reactionScale)
                    showBrowser(account.restoreUrl())
                    null
                },
                onFailure = { error ->
                    error.message ?: getString(R.string.invalid_instance_url)
                },
            )
        }
        setInsetContentView(screen)
    }

    private fun showAccountSwitcher() {
        persistCurrentUrl()
        accounts = accountRegistry.loadAccounts()
        if (accounts.isEmpty()) {
            showSetup()
            return
        }

        val activeId = currentAccount?.id
        val labels = accounts.map { account ->
            val marker = if (account.id == activeId) "✓ " else ""
            "$marker${account.displayLabel()}\n${account.instanceOrigin}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.accounts)
            .setItems(labels) { _, index -> switchAccount(accounts[index]) }
            .setPositiveButton(R.string.add_account) { _, _ -> showAddAccountDialog() }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showAddAccountDialog() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            Toast.makeText(this, R.string.multi_profile_unsupported, Toast.LENGTH_LONG).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        container.addView(TextView(this).apply {
            text = getString(R.string.add_account_description)
            setPadding(0, 0, 0, dp(12))
        })
        val input = EditText(this).apply {
            hint = getString(R.string.misskey_instance_hint)
            setSingleLine(true)
        }
        container.addView(
            input,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.add_account)
            .setView(container)
            .setPositiveButton(R.string.add_account, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                InstanceConfig.parse(input.text.toString()).fold(
                    onSuccess = { instance ->
                        persistCurrentUrl()
                        nativeAudioPlayer?.dismiss()
                        nativeAudioPlayer = null
                        val account = accountRegistry.addIsolated(instance)
                        accounts = accountRegistry.loadAccounts()
                        currentAccount = account
                        currentInstance = instance
                        preferences.save(instance, currentMode, currentTextScale, currentReactionScale)
                        dialog.dismiss()
                        Toast.makeText(this, R.string.account_added, Toast.LENGTH_SHORT).show()
                        showBrowser(instance.origin)
                    },
                    onFailure = { error ->
                        input.error = error.message ?: getString(R.string.invalid_instance_url)
                    },
                )
            }
        }
        dialog.show()
    }

    private fun switchAccount(account: AccountProfile) {
        if (account.id == currentAccount?.id) return
        if (
            account.profileName != null &&
            !WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        ) {
            Toast.makeText(this, R.string.multi_profile_unsupported, Toast.LENGTH_LONG).show()
            return
        }

        persistCurrentUrl()
        nativeAudioPlayer?.dismiss()
        nativeAudioPlayer = null
        val instance = account.instanceConfig() ?: return
        accountRegistry.setActive(account.id)
        currentAccount = account
        currentInstance = instance
        preferences.save(instance, currentMode, currentTextScale, currentReactionScale)
        showBrowser(account.restoreUrl())
    }

    private fun recoverToDefaultAccount(failedAccount: AccountProfile): Boolean {
        accounts = accountRegistry.loadAccounts()
        val fallback = accounts.firstOrNull { candidate ->
            candidate.id != failedAccount.id &&
                candidate.profileName == null &&
                candidate.instanceConfig() != null
        } ?: return false
        val instance = fallback.instanceConfig() ?: return false

        accountRegistry.setActive(fallback.id)
        currentAccount = fallback
        currentInstance = instance
        preferences.save(instance, currentMode, currentTextScale, currentReactionScale)
        Toast.makeText(this, R.string.account_profile_recovered, Toast.LENGTH_LONG).show()
        showBrowser(fallback.restoreUrl())
        return true
    }

    private fun showWebViewRecovery(detail: String) {
        destroyWebView()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.webview_recovery_title)
            textSize = 22f
            setPadding(0, 0, 0, dp(12))
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.webview_recovery_message, detail)
            setPadding(0, 0, 0, dp(20))
        })
        root.addView(Button(this).apply {
            text = getString(R.string.retry)
            setOnClickListener {
                val target = currentAccount?.restoreUrl() ?: currentInstance?.origin
                if (target != null) showBrowser(target) else showSetup()
            }
        })
        root.addView(Button(this).apply {
            text = getString(R.string.accounts)
            setOnClickListener { showAccountSwitcher() }
        })
        root.addView(Button(this).apply {
            text = getString(R.string.settings)
            setOnClickListener { showSetup() }
        })
        setInsetContentView(root)
    }

    private fun persistCurrentUrl() {
        val account = currentAccount ?: return
        val url = navigationState.currentUrl ?: lastKnownUrl ?: return
        accountRegistry.updateLastUrl(account.id, url)
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
        val account = currentAccount ?: return
        val instance = account.instanceConfig() ?: return
        currentInstance = instance

        if (
            account.profileName != null &&
            !WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        ) {
            if (recoverToDefaultAccount(account)) return
            showWebViewRecovery(getString(R.string.multi_profile_unsupported))
            return
        }

        destroyWebView()
        val safeUrl = url.takeIf(instance::owns) ?: instance.origin
        lastKnownUrl = safeUrl
        navigationState.reset(safeUrl)

        val root = FrameLayout(this)
        browserRoot = root

        val created = runCatching {
            WebViewFactory.create(this, account.profileName)
        }.getOrElse {
            browserRoot = null
            showWebViewRecovery(getString(R.string.webview_recovery_provider_error))
            return
        }
        webView = created

        applyTextScale(created)
        powerController.configure(this, created, currentMode)
        mediaFallbackBridge.attach(created, instance) { request ->
            playNativeAudio(request, created)
        }

        val externalNavigator = ExternalNavigator(this)
        created.webViewClient = MidroidWebViewClient(
            instance = instance,
            externalNavigator = externalNavigator,
            onMainFrameLoadStarted = { navigationState.onPageStarted(it) },
            onMainFrameCommitted = { committedUrl ->
                navigationState.onPageCommitted(committedUrl)
                lastKnownUrl = committedUrl
                accountRegistry.updateLastUrl(account.id, committedUrl)
            },
            onHistoryChanged = { historyUrl ->
                navigationState.onHistoryChanged(historyUrl)
                accountRegistry.updateLastUrl(account.id, historyUrl)
                mediaFallbackBridge.install(created)
            },
            onPageReady = { view, finishedUrl ->
                navigationState.onPageFinished(finishedUrl)
                RuntimeDiagnostics.logPageReady(this, currentMode, navigationState.currentUrl ?: lastKnownUrl)
                powerController.onPageReady(this, view, currentMode)
                uiTuner.applyReactionScale(view, currentReactionScale)
                mediaFallbackBridge.install(view)
            },
            onNativeAudioRequested = { request -> playNativeAudio(request, created) },
            onRendererGone = ::handleRendererGone,
        )
        created.webChromeClient = MidroidWebChromeClient(::launchFileChooser)
        created.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(created, downloadUrl, userAgent, contentDisposition, mimeType)
        }

        val accountButton = Button(this).apply {
            text = "⇄"
            contentDescription = getString(R.string.switch_account)
            textSize = 18f
            alpha = 0.9f
            setTextColor(Color.WHITE)
            background = browserControlBackground()
            minWidth = dp(40)
            minimumWidth = dp(40)
            minHeight = dp(40)
            minimumHeight = dp(40)
            setPadding(0, 0, 0, 0)
            stateListAnimator = null
            setOnClickListener { showAccountSwitcher() }
        }

        val nativeAudioButton = Button(this).apply {
            text = "♫"
            contentDescription = getString(R.string.native_audio_fallback)
            textSize = 19f
            alpha = 0.9f
            setTextColor(Color.WHITE)
            background = browserControlBackground()
            minWidth = dp(40)
            minimumWidth = dp(40)
            minHeight = dp(40)
            minimumHeight = dp(40)
            setPadding(0, 0, 0, 0)
            stateListAnimator = null
            setOnClickListener {
                mediaFallbackBridge.install(created)
                mediaFallbackBridge.requestPlayback(created) { started ->
                    if (!started) {
                        Toast.makeText(
                            this@MainActivity,
                            R.string.native_audio_not_found,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }

        val settingsButton = Button(this).apply {
            text = "⚙"
            contentDescription = getString(R.string.settings)
            textSize = 18f
            alpha = 0.9f
            setTextColor(Color.WHITE)
            background = browserControlBackground()
            minWidth = dp(40)
            minimumWidth = dp(40)
            minHeight = dp(40)
            minimumHeight = dp(40)
            setPadding(0, 0, 0, 0)
            stateListAnimator = null
            setOnClickListener { showSetup() }
        }

        fun browserControlParams(): LinearLayout.LayoutParams {
            return LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                marginStart = dp(4)
            }
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = false
            isFocusable = false
            setPadding(dp(10), dp(2), dp(6), dp(2))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x66202124.toInt(), 0x11202124),
            )
            elevation = dp(4).toFloat()
            addView(TextView(this@MainActivity).apply {
                text = "${getString(R.string.app_name)} · ${account.displayLabel()}"
                textSize = 15f
                alpha = 0.94f
                setTextColor(Color.WHITE)
                setShadowLayer(4f, 0f, 1f, Color.BLACK)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(6), 0, dp(4), 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(accountButton, browserControlParams())
            addView(nativeAudioButton, browserControlParams())
            addView(settingsButton, browserControlParams())
        }

        root.addView(
            created,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            toolbar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(44),
                Gravity.TOP,
            ),
        )

        setInsetContentView(root)
        created.loadUrl(safeUrl)
        if (visibleToUser) powerController.onForeground(this, created, currentMode)
    }

    private fun playNativeAudio(request: NativeAudioRequest, sourceWebView: WebView) {
        val headers = linkedMapOf<String, String>()
        sourceWebView.settings.userAgentString
            ?.takeIf { it.isNotBlank() }
            ?.let { headers["User-Agent"] = it }

        // Media3/HttpURLConnection may follow HTTPS redirects to another host while
        // replaying fixed request headers. Never copy WebView Cookie or full-page Referer
        // into that redirecting client. Authenticated native media can be reintroduced only
        // with a hop-aware HTTP implementation that strips credentials on origin change.

        nativeAudioPlayer?.dismiss()
        nativeAudioPlayer = NativeAudioPlayerDialog(this).also { player ->
            player.show(request, headers)
        }
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
        sourceWebView: WebView,
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
            // DownloadManager replays fixed headers while following redirects. Do not
            // attach WebView cookies here: a same-origin URL may redirect to another HTTPS host.
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
            WindowInsetsCompat.CONSUMED
        }
        setContentView(container)
        ViewCompat.requestApplyInsets(container)
    }

    private fun browserControlBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x7A202124)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
