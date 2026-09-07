package dev.midroid.app.media

object NativeAudioPlaybackPolicy {
    fun shouldAutoStart(hostVisible: Boolean, playbackRequested: Boolean): Boolean =
        hostVisible && playbackRequested

    fun shouldPollProgress(hostVisible: Boolean, hasDialog: Boolean): Boolean =
        hostVisible && hasDialog
}
