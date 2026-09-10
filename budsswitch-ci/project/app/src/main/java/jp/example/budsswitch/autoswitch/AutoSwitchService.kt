package jp.example.budsswitch.autoswitch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import jp.example.budsswitch.media.BudsNotificationListener
import jp.example.budsswitch.prefs.AppPrefs
import jp.example.budsswitch.shizuku.ShizukuBridge

class AutoSwitchService : Service() {

    companion object {
        private const val CHANNEL = "buds_switch"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_MS = 750L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val engine = PriorityEngine()

    private val poll = object : Runnable {
        override fun run() {
            evaluateMedia()
            handler.postDelayed(this, POLL_MS)
        }
    }

    private lateinit var telephonyManager: TelephonyManager
    private var telephonyCallback: TelephonyCallback? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("BudsSwitch")
                .setContentText("自動切替を監視中")
                .setOngoing(true)
                .build()
        )

        ShizukuBridge.bindUserService()
        registerCallListener()
        handler.post(poll)
    }

    private fun registerCallListener() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return

        telephonyManager = getSystemService(TelephonyManager::class.java)
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                if (state == TelephonyManager.CALL_STATE_RINGING ||
                    state == TelephonyManager.CALL_STATE_OFFHOOK
                ) {
                    val now = SystemClock.elapsedRealtime()
                    if (engine.onCallActive(now)) reclaim("call", now)
                }
            }
        }
        telephonyCallback = callback
        telephonyManager.registerTelephonyCallback(mainExecutor, callback)
    }

    private fun evaluateMedia() {
        val active = isMediaPlaying()
        val now = SystemClock.elapsedRealtime()

        if (!active) {
            engine.onMediaChanged(false, now)
            return
        }

        val shouldSwitch = engine.onMediaChanged(true, now)
        if (shouldSwitch) reclaim("media", now)
    }

    private fun isMediaPlaying(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        if (!enabled.contains(packageName)) return false

        return runCatching {
            val manager = getSystemService(MediaSessionManager::class.java)
            val component = ComponentName(this, BudsNotificationListener::class.java)
            manager.getActiveSessions(component).any { controller ->
                when (controller.playbackState?.state) {
                    android.media.session.PlaybackState.STATE_PLAYING,
                    android.media.session.PlaybackState.STATE_BUFFERING -> true
                    else -> false
                }
            }
        }.getOrDefault(false)
    }

    private fun reclaim(reason: String, now: Long) {
        val address = AppPrefs.selectedDevice(this) ?: return
        if (!ShizukuBridge.ready()) {
            ShizukuBridge.bindUserService()
            return
        }

        val summary = ShizukuBridge.summary(address)
        // An ACL link alone does not mean that this phone owns the audio profile.
        // For media we require A2DP; for calls HFP is sufficient.
        val alreadyOwned = when (reason) {
            "call" -> summary.contains("HFP=connected")
            else -> summary.contains("A2DP=connected")
        }
        if (alreadyOwned) return

        val result = ShizukuBridge.connect(address)
        engine.markSwitched(now)
        updateNotification("切替要求: $reason / $result")
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("BudsSwitch")
                .setContentText(text.take(120))
                .setOngoing(true)
                .build()
        )
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "BudsSwitch Auto Switch",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        telephonyCallback?.let {
            runCatching { telephonyManager.unregisterTelephonyCallback(it) }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
