package dev.midroid.app.media

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
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
import dev.midroid.app.R
import java.util.Locale

class NativeAudioPlayerDialog(
    private val activity: Activity,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var dialog: AlertDialog? = null
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var playButton: Button? = null
    private var seekBar: SeekBar? = null
    private var positionView: TextView? = null
    private var statusView: TextView? = null
    private var userSeeking = false
    private var resumeAfterFocusGain = false
    private var ducked = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (ducked) {
                    player?.setVolume(1f, 1f)
                    ducked = false
                }
                if (resumeAfterFocusGain) {
                    resumeAfterFocusGain = false
                    startPlayback()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                player?.setVolume(0.2f, 0.2f)
                ducked = true
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeAfterFocusGain = isPlaying()
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterFocusGain = false
                pause()
                abandonAudioFocus()
            }
        }
    }

    private val progressUpdater = object : Runnable {
        override fun run() {
            val active = player ?: return
            if (!userSeeking) {
                runCatching {
                    seekBar?.progress = active.currentPosition
                    updatePosition(active.currentPosition, active.duration)
                }
            }
            mainHandler.postDelayed(this, 500L)
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
            isEnabled = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val duration = runCatching { player?.duration ?: 0 }.getOrDefault(0)
                        updatePosition(progress, duration)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    userSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val target = seekBar?.progress ?: 0
                    runCatching { player?.seekTo(target) }
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
                if (isPlaying()) {
                    pause()
                } else {
                    startPlayback()
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

        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        mediaPlayer.setAudioAttributes(audioAttributes)
        mediaPlayer.setOnPreparedListener { prepared ->
            val duration = runCatching { prepared.duration }.getOrDefault(0).coerceAtLeast(0)
            seek.max = duration
            seek.isEnabled = duration > 0
            play.isEnabled = true
            status.text = activity.getString(R.string.native_audio_ready)
            updatePosition(0, duration)
            startPlayback()
            mainHandler.removeCallbacks(progressUpdater)
            mainHandler.post(progressUpdater)
        }
        mediaPlayer.setOnCompletionListener {
            play.text = activity.getString(R.string.native_audio_play)
            status.text = activity.getString(R.string.native_audio_finished)
            seek.progress = seek.max
            updatePosition(seek.max, seek.max)
        }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            play.isEnabled = false
            status.text = activity.getString(R.string.native_audio_failed)
            true
        }

        runCatching {
            mediaPlayer.setDataSource(activity, uri, headers)
            mediaPlayer.prepareAsync()
        }.onFailure {
            status.text = activity.getString(R.string.native_audio_failed)
            play.isEnabled = false
        }
    }

    fun pause() {
        val active = player ?: return
        if (runCatching { active.isPlaying }.getOrDefault(false)) {
            runCatching { active.pause() }
        }
        playButton?.text = activity.getString(R.string.native_audio_play)
        statusView?.text = activity.getString(R.string.native_audio_paused)
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

    private fun startPlayback() {
        val active = player ?: return
        if (!requestAudioFocus()) {
            statusView?.text = activity.getString(R.string.native_audio_focus_failed)
            return
        }
        runCatching {
            if (ducked) {
                active.setVolume(1f, 1f)
                ducked = false
            }
            active.start()
        }.onSuccess {
            playButton?.text = activity.getString(R.string.native_audio_pause)
            statusView?.text = activity.getString(R.string.native_audio_playing)
        }.onFailure {
            statusView?.text = activity.getString(R.string.native_audio_failed)
        }
    }

    private fun isPlaying(): Boolean =
        runCatching { player?.isPlaying == true }.getOrDefault(false)

    private fun requestAudioFocus(): Boolean {
        val existing = focusRequest
        if (existing != null) {
            return audioManager.requestAudioFocus(existing) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(focusListener, mainHandler)
            .setWillPauseWhenDucked(false)
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun releasePlayer() {
        mainHandler.removeCallbacks(progressUpdater)
        abandonAudioFocus()
        player?.let { active ->
            runCatching { active.stop() }
            runCatching { active.reset() }
            active.release()
        }
        player = null
        playButton = null
        seekBar = null
        positionView = null
        statusView = null
        userSeeking = false
        resumeAfterFocusGain = false
        ducked = false
    }

    private fun updatePosition(positionMs: Int, durationMs: Int) {
        positionView?.text = activity.getString(
            R.string.native_audio_position,
            formatTime(positionMs),
            formatTime(durationMs),
        )
    }

    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = (milliseconds.coerceAtLeast(0) / 1000)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
