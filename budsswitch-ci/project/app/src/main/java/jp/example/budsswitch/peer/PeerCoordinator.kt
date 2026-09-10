package jp.example.budsswitch.peer

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class PeerCoordinator(
    context: Context,
    private val budsAddress: String,
    private val secret: String,
    private val onPeersChanged: (List<PeerState>) -> Unit
) : AutoCloseable {

    companion object {
        private const val PORT = 43173
        private const val HEARTBEAT_MS = 350L
        private const val STALE_MS = 2_200L
        private const val MAX_PACKET = 2_048
    }

    val localId: String = stableDeviceId(context)
    val localName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    @Volatile private var localScore: Int = 0
    @Volatile private var localReason: String = "idle"
    @Volatile private var localSinceMs: Long = 0L

    private val running = AtomicBoolean(true)
    private val peers = ConcurrentHashMap<String, PeerState>()
    private val receiver = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BudsSwitch-peer-rx").apply { isDaemon = true }
    }
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "BudsSwitch-peer-tx").apply { isDaemon = true }
    }

    private val socket = DatagramSocket(null).apply {
        reuseAddress = true
        broadcast = true
        bind(InetSocketAddress(PORT))
    }

    init {
        require(secret.length >= 6) { "Peer Link code must have at least 6 characters" }
        receiver.execute(::receiveLoop)
        scheduler.scheduleAtFixedRate({ heartbeatAndPrune() }, 0, HEARTBEAT_MS, TimeUnit.MILLISECONDS)
    }

    fun updateLocal(score: Int, reason: String, sinceMs: Long) {
        val newScore = score.coerceIn(0, 100)
        val changed = newScore != localScore || reason != localReason || sinceMs != localSinceMs
        localScore = newScore
        localReason = reason
        localSinceMs = sinceMs
        // Do not wait for the next periodic heartbeat when playback/call state changes.
        if (changed && running.get()) runCatching { scheduler.execute(::heartbeatAndPrune) }
    }

    fun snapshot(): List<PeerState> = peers.values.sortedBy { it.id }

    private fun heartbeatAndPrune() {
        if (!running.get()) return
        val payload = PeerProtocol.encode(
            secret = secret,
            budsAddress = budsAddress,
            id = localId,
            name = localName,
            score = localScore,
            reason = localReason,
            sinceMs = localSinceMs,
            sentMs = System.currentTimeMillis()
        )
        broadcastAddresses().forEach { address ->
            runCatching { socket.send(DatagramPacket(payload, payload.size, address, PORT)) }
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        var changed = false
        peers.entries.removeIf { (_, state) ->
            val stale = nowElapsed - state.receivedAtElapsedMs > STALE_MS
            if (stale) changed = true
            stale
        }
        if (changed) onPeersChanged(snapshot())
    }

    private fun receiveLoop() {
        val buffer = ByteArray(MAX_PACKET)
        while (running.get()) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
                val payload = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                val state = PeerProtocol.decode(
                    secret = secret,
                    budsAddress = budsAddress,
                    payload = payload,
                    nowWallMs = System.currentTimeMillis(),
                    receivedAtElapsedMs = SystemClock.elapsedRealtime()
                ) ?: continue
                if (state.id == localId) continue
                val previous = peers.put(state.id, state)
                val meaningfulChange = previous == null ||
                    previous.score != state.score ||
                    previous.reason != state.reason ||
                    previous.sinceMs != state.sinceMs ||
                    previous.name != state.name
                if (meaningfulChange) onPeersChanged(snapshot())
            } catch (_: Throwable) {
                if (!running.get()) break
            }
        }
    }

    private fun broadcastAddresses(): Set<InetAddress> {
        val addresses = linkedSetOf<InetAddress>()
        runCatching { addresses += InetAddress.getByName("255.255.255.255") }
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val network = interfaces.nextElement()
                if (!network.isUp || network.isLoopback) continue
                network.interfaceAddresses.forEach { iface -> iface.broadcast?.let(addresses::add) }
            }
        }
        return addresses
    }

    override fun close() {
        if (!running.getAndSet(false)) return
        runCatching { socket.close() }
        receiver.shutdownNow(); scheduler.shutdownNow(); peers.clear()
    }

    private fun stableDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        return MessageDigest.getInstance("SHA-256")
            .digest("$androidId|${context.packageName}".toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
    }
}
