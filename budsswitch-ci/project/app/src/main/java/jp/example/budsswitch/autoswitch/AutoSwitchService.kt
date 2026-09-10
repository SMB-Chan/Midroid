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
import jp.example.budsswitch.peer.DemandCandidate
import jp.example.budsswitch.peer.PeerArbitrator
import jp.example.budsswitch.peer.PeerCoordinator
import jp.example.budsswitch.peer.PeerState
import jp.example.budsswitch.prefs.AppPrefs
import jp.example.budsswitch.shizuku.ShizukuBridge

class AutoSwitchService : Service() {

    companion object {
        private const val CHANNEL = "buds_switch"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_MS = 350L
        private const val MEDIA_HOLD_MS = 900L
        private const val WINNER_STABLE_MS = 250L
        private const val ACTION_GAP_MS = 900L
        private const val OWNERSHIP_CACHE_MS = 300L
    }

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var callActive = false
    @Volatile private var peers: List<PeerState> = emptyList()

    private var peerCoordinator: PeerCoordinator? = null
    private var mediaDetectedAtElapsed: Long? = null
    private var localScore = 0
    private var localReason = "idle"
    private var localSinceWallMs = 0L
    private var lastWinnerId: String? = null
    private var winnerSinceElapsed = 0L
    private var lastActionElapsed = Long.MIN_VALUE / 2
    private var lastOwnershipCheckElapsed = Long.MIN_VALUE / 2
    private var lastOwnershipSummary = "A2DP=disconnected HFP=disconnected"

    private lateinit var telephonyManager: TelephonyManager
    private var telephonyCallback: TelephonyCallback? = null

    private val poll = object : Runnable {
        override fun run() {
            evaluateAndArbitrate()
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("自動切替を監視中"))
        ShizukuBridge.bindUserService()
        setupPeerLink()
        registerCallListener()
        handler.post(poll)
    }

    private fun setupPeerLink() {
        val address = AppPrefs.selectedDevice(this) ?: return
        val code = AppPrefs.peerCode(this)
        if (code.length < 6) {
            updateNotification("自動切替を監視中 / Peer Link: OFF")
            return
        }

        peerCoordinator = runCatching {
            PeerCoordinator(this, address, code) { snapshot ->
                peers = snapshot
                // PeerCoordinator only invokes this on meaningful demand changes. React now
                // instead of waiting up to one poll interval; calls are always immediate.
                handler.post {
                    evaluateAndArbitrate(forceImmediate = snapshot.any { it.score >= 100 })
                }
            }
        }.onSuccess {
            updateNotification("自動切替を監視中 / Peer Link: ON")
        }.onFailure {
            updateNotification("Peer Link開始失敗: ${it.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun registerCallListener() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) return
        telephonyManager = getSystemService(TelephonyManager::class.java)
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                callActive = state == TelephonyManager.CALL_STATE_RINGING ||
                    state == TelephonyManager.CALL_STATE_OFFHOOK
                evaluateAndArbitrate(forceImmediate = callActive)
            }
        }
        telephonyCallback = callback
        telephonyManager.registerTelephonyCallback(mainExecutor, callback)
    }

    private fun evaluateAndArbitrate(forceImmediate: Boolean = false) {
        val address = AppPrefs.selectedDevice(this) ?: return
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()

        val mediaRaw = isMediaPlaying()
        if (mediaRaw) {
            if (mediaDetectedAtElapsed == null) mediaDetectedAtElapsed = nowElapsed
        } else mediaDetectedAtElapsed = null
        val mediaEffective = mediaRaw &&
            nowElapsed - (mediaDetectedAtElapsed ?: nowElapsed) >= MEDIA_HOLD_MS

        val newScore: Int
        val newReason: String
        when {
            callActive -> { newScore = 100; newReason = "call" }
            mediaEffective -> { newScore = 50; newReason = "media" }
            else -> { newScore = 0; newReason = "idle" }
        }
        if (newScore != localScore || newReason != localReason) {
            localScore = newScore
            localReason = newReason
            localSinceWallMs = if (newScore > 0) nowWall else 0L
        }

        val coordinator = peerCoordinator
        coordinator?.updateLocal(localScore, localReason, localSinceWallMs)
        val localId = coordinator?.localId ?: "local"
        val local = DemandCandidate(
            id = localId,
            name = coordinator?.localName ?: android.os.Build.MODEL,
            score = localScore,
            reason = localReason,
            sinceMs = localSinceWallMs
        )
        val winner = PeerArbitrator.winner(local, peers)
        if (winner?.id != lastWinnerId) {
            lastWinnerId = winner?.id
            winnerSinceElapsed = nowElapsed
        }
        if (winner == null) return

        val decisivePeer = winner.id != localId && winner.score > localScore
        val stable = forceImmediate || winner.score >= 100 || decisivePeer ||
            nowElapsed - winnerSinceElapsed >= WINNER_STABLE_MS
        if (!stable) return

        if (winner.id == localId) {
            if (localScore > 0) reclaim(address, localReason, nowElapsed)
        } else {
            yieldToPeer(address, winner, nowElapsed)
        }
    }

    private fun reclaim(address: String, reason: String, nowElapsed: Long) {
        if (!ShizukuBridge.ready()) { ShizukuBridge.bindUserService(); return }
        if (reason != "call" && nowElapsed - lastActionElapsed < ACTION_GAP_MS) return

        val summary = ownershipSummary(address, nowElapsed)
        val alreadyOwned = if (reason == "call") summary.contains("HFP=connected")
        else summary.contains("A2DP=connected")
        if (alreadyOwned) return

        val result = ShizukuBridge.connect(address)
        lastActionElapsed = nowElapsed
        lastOwnershipCheckElapsed = Long.MIN_VALUE / 2
        updateNotification("この端末へ切替: $reason / $result")
    }

    private fun yieldToPeer(address: String, winner: DemandCandidate, nowElapsed: Long) {
        if (!ShizukuBridge.ready()) return
        if (winner.score < 100 && nowElapsed - lastActionElapsed < ACTION_GAP_MS) return

        val summary = ownershipSummary(address, nowElapsed)
        val owned = summary.contains("A2DP=connected") || summary.contains("HFP=connected")
        if (!owned) return

        val result = ShizukuBridge.disconnect(address)
        lastActionElapsed = nowElapsed
        lastOwnershipCheckElapsed = Long.MIN_VALUE / 2
        updateNotification("${winner.name}へ譲渡: ${winner.reason} / $result")
    }

    private fun ownershipSummary(address: String, nowElapsed: Long): String {
        if (nowElapsed - lastOwnershipCheckElapsed < OWNERSHIP_CACHE_MS) return lastOwnershipSummary
        lastOwnershipSummary = ShizukuBridge.summary(address)
        lastOwnershipCheckElapsed = nowElapsed
        return lastOwnershipSummary
    }

    private fun isMediaPlaying(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
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

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle("BudsSwitch")
        .setContentText(text.take(160))
        .setOngoing(true)
        .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "BudsSwitch Auto Switch", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        peerCoordinator?.close(); peerCoordinator = null
        if (::telephonyManager.isInitialized) {
            telephonyCallback?.let { runCatching { telephonyManager.unregisterTelephonyCallback(it) } }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
