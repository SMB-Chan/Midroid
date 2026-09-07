package dev.midroid.app.web

import dev.midroid.app.media.NativeAudioRequest
import java.util.Locale

object NativeAudioNavigationPolicy {
    fun isNativeAudioScheme(scheme: String?): Boolean =
        scheme?.lowercase(Locale.ROOT) == NativeAudioRequest.SCHEME

    fun shouldDispatch(scheme: String?, isForMainFrame: Boolean): Boolean =
        isForMainFrame && isNativeAudioScheme(scheme)
}
