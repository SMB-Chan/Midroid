package dev.midroid.app.web

class NavigationState(initialUrl: String? = null) {
    var currentUrl: String? = initialUrl
        private set

    var lastCommittedUrl: String? = initialUrl
        private set

    var mainFrameLoading: Boolean = false
        private set

    private var returnUrlForPendingLoad: String? = null

    fun reset(url: String?) {
        currentUrl = url
        lastCommittedUrl = url
        mainFrameLoading = false
        returnUrlForPendingLoad = null
    }

    fun onPageStarted(url: String) {
        if (!mainFrameLoading) {
            returnUrlForPendingLoad = lastCommittedUrl
        }
        mainFrameLoading = true
        currentUrl = url
    }

    fun onPageCommitted(url: String) {
        lastCommittedUrl = url
        currentUrl = url
    }

    fun onHistoryChanged(url: String) {
        currentUrl = url
    }

    fun onPageFinished(url: String) {
        mainFrameLoading = false
        currentUrl = url
        returnUrlForPendingLoad = null
    }

    fun onLoadCancelled() {
        mainFrameLoading = false
    }

    fun isMisskeyLightboxOpen(): Boolean = isMisskeyLightboxUrl(currentUrl)

    fun interruptedReturnUrl(): String? {
        if (isMisskeyLightboxOpen()) {
            return lastCommittedUrl?.substringBefore('#')
        }
        if (mainFrameLoading) {
            return returnUrlForPendingLoad
        }
        return null
    }

    companion object {
        fun isMisskeyLightboxUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            return url.substringAfter('#', missingDelimiterValue = "") == "pswp"
        }
    }
}
