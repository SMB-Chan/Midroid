package dev.midroid.app.media

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.midroid.app.R
import java.util.Locale

@SuppressLint("UnsafeOptInUsageError")
class NativeAudioPlayerDialog(
    private val activity: Activity,
) {
    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val PREPARE_TIMEOUT_MS = 20_000L
        const val PROGRESS_INTERVAL_MS = 500L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private var dialog: AlertDialog? = null
    private var player: ExoPlayer? = null
    private var playButton: Button? = null
    private var seekBar: SeekBar? = null
    private var positionView: TextView? = null
    private var statusView: TextView? = null
    private var userSeeking = false
    private var preparedOnce = false
    private var timedOut = false

    private val prepareTimeout = Runnable {
        val active = player ?: return@Runnable
        if (active.playbackState == Player.STATE_READY || active.playbackState == Player.STATE_ENDED) {
            return@Runnable
        }

        timedOut = true
        active.playWhenReady = false
        active.stop()
        statusView?.text = activity.getString(R.string.native_audio_timeout)
        playButton?.apply {
            text = activity.getString(R.string.native_audio_retry)
            isEnabled = true
        }
    }

    private val progressUpdater = object : Runnable {
        override fun run() {
            val active = player ?: return
            if (!userSeeking && active.playbackState != Player.STATE_IDLE) {
                val duration = knownDuration(active)
                val position = active.currentPosition.coerceAtLeast(0L)
                updateSeek(position, duration)
            }
            mainHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    fun show(
        request: NativeAudioRequest,
        headers: Map<String, String>,
    ) {
        dismiss()

        val uri = Uri.parse(request.sourceUrl)
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
            Toast.makeText(activity, R.string.native_audio_invalid_url, Toast.LENGTH_SHORT).show()
            return
        }

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }

        val status = TextView(activity).apply {
            text = activity.getString(R.string.native_audio_preparing)
            setPadding(0, dp(4), 0, dp(8))
        }
        statusView = status
        layout.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val position = TextView(activity).apply {
            text = "0:00 / 0:00"
            gravity = Gravity.CENTER
        }
        positionView = position
        layout.addView(
            position,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val seek = SeekBar(activity).apply {
            max = 1_000
            isEnabled = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val active = player ?: return
                    val duration = knownDuration(active)
                    if (duration <= 0L) return
                    val target = progressToPosition(progress, duration)
                    updatePosition(target, duration)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    userSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val active = player
                    val duration = active?.let(::knownDuration) ?: 0L
                    if (active != null && duration > 0L) {
                        val target = progressToPosition(seekBar?.progress ?: 0, duration)
                        active.seekTo(target)
                    }
                    userSeeking = false
                }
            })
        }
        seekBar = seek
        layout.addView(
            seek,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val play = Button(activity).apply {
            text = activity.getString(R.string.native_audio_play)
            isEnabled = false
            setOnClickListener {
                val active = player ?: return@setOnClickListener
                when {
                    timedOut || active.playerError != null || active.playbackState == Player.STATE_IDLE -> {
                        retryPreparation()
                    }
                    active.isPlaying -> pause()
                    else -> startPlayback()
                }
            }
        }
        playButton = play
        layout.addView(
            play,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val title = request.title?.takeIf { it.isNotBlank() }
            ?: activity.getString(R.string.native_audio_title)
        val createdDialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(layout)
            .setNegativeButton(R.string.close, null)
            .create()
        createdDialog.setOnDismissListener {
            dialog = null
            releasePlayer()
        }
        dialog = createdDialog
        createdDialog.show()

        val safeHeaders = headers
            .filterKeys { key ->
                key.equals("User-Agent", true) ||
                    key.equals("Cookie", true) ||
                    key.equals("Referer", true)
            }
            .filterValues { it.isNotBlank() }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(false)
            .setDefaultRequestProperties(safeHeaders)

        val mediaSourceFactory = DefaultMediaSourceFactory(activity)
            .setDataSourceFactory(httpDataSourceFactory)

        val exoPlayer = ExoPlayer.Builder(activity)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, true)
                setHandleAudioBecomingNoisy(true)
                setMediaItem(MediaItem.fromUri(uri))
            }
        player = exoPlayer

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        statusView?.text = activity.getString(
                            if (preparedOnce) R.string.native_audio_buffering
                            else R.string.native_audio_preparing,
                        )
                    }
                    Player.STATE_READY -> {
                        mainHandler.removeCallbacks(prepareTimeout)
                        timedOut = false
                        val duration = knownDuration(exoPlayer)
                        seek.isEnabled = duration > 0L
                        play.isEnabled = true
                        if (!preparedOnce) {
                            preparedOnce = true
                            status.text = activity.getString(R.string.native_audio_ready)
                            updateSeek(exoPlayer.currentPosition, duration)
                            startPlayback()
                        }
                    }
                    Player.STATE_ENDED -> {
                        mainHandler.removeCallbacks(prepareTimeout)
                        play.text = activity.getString(R.string.native_audio_play)
                        play.isEnabled = true
                        status.text = activity.getString(R.string.native_audio_finished)
                        val duration = knownDuration(exoPlayer)
                        updateSeek(duration, duration)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    play.text = activity.getString(R.string.native_audio_pause)
                    status.text = activity.getString(R.string.native_audio_playing)
                } else if (
                    exoPlayer.playbackState == Player.STATE_READY &&
                    preparedOnce &&
                    !timedOut
                ) {
                    play.text = activity.getString(R.string.native_audio_play)
                    status.text = activity.getString(R.string.native_audio_paused)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                mainHandler.removeCallbacks(prepareTimeout)
                timedOut = false
                play.isEnabled = true
                play.text = activity.getString(R.string.native_audio_retry)
                status.text = activity.getString(R.string.native_audio_failed_detail, error.errorCodeName)
            }
        })

        mainHandler.removeCallbacks(progressUpdater)
        mainHandler.post(progressUpdater)
        beginPreparation()
    }

    fun pause() {
        player?.pause()
        playButton?.text = activity.getString(R.string.native_audio_play)
        if (player?.playbackState == Player.STATE_READY) {
            statusView?.text = activity.getString(R.string.native_audio_paused)
        }
    }

    fun dismiss() {
        val current = dialog
        if (current != null) {
            current.setOnDismissListener(null)
            current.dismiss()
            dialog = null
        }
        releasePlayer()
    }

    private fun beginPreparation() {
        val active = player ?: return
        timedOut = false
        statusView?.text = activity.getString(R.string.native_audio_preparing)
        playButton?.apply {
            text = activity.getString(R.string.native_audio_play)
            isEnabled = false
        }
        mainHandler.removeCallbacks(prepareTimeout)
        mainHandler.postDelayed(prepareTimeout, PREPARE_TIMEOUT_MS)
        active.prepare()
    }

    private fun retryPreparation() {
        val active = player ?: return
        preparedOnce = false
        timedOut = false
        active.stop()
        active.seekTo(0L)
        beginPreparation()
    }

    private fun startPlayback() {
        val active = player ?: return
        if (active.playbackState == Player.STATE_ENDED) {
            active.seekTo(0L)
        }
        active.play()
        playButton?.text = activity.getString(R.string.native_audio_pause)
    }

    private fun releasePlayer() {
        mainHandler.removeCallbacks(prepareTimeout)
        mainHandler.removeCallbacks(progressUpdater)
        player?.release()
        player = null
        playButton = null
        seekBar = null
        positionView = null
        statusView = null
        userSeeking = false
        preparedOnce = false
        timedOut = false
    }

    private fun knownDuration(active: Player): Long {
        val duration = active.duration
        return if (duration == C.TIME_UNSET || duration < 0L) 0L else duration
    }

    private fun updateSeek(positionMs: Long, durationMs: Long) {
        val duration = durationMs.coerceAtLeast(0L)
        val position = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        if (!userSeeking && duration > 0L) {
            seekBar?.progress = positionToProgress(position, duration)
        }
        seekBar?.isEnabled = duration > 0L
        updatePosition(position, duration)
    }

    private fun positionToProgress(positionMs: Long, durationMs: Long): Int {
        if (durationMs <= 0L) return 0
        return ((positionMs.coerceIn(0L, durationMs) * 1_000L) / durationMs)
            .toInt()
            .coerceIn(0, 1_000)
    }

    private fun progressToPosition(progress: Int, durationMs: Long): Long =
        (durationMs.coerceAtLeast(0L) * progress.coerceIn(0, 1_000)) / 1_000L

    private fun updatePosition(positionMs: Long, durationMs: Long) {
        positionView?.text = activity.getString(
            R.string.native_audio_position,
            formatTime(positionMs),
            formatTime(durationMs),
        )
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
