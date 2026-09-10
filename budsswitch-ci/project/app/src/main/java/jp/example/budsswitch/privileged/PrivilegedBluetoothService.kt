package jp.example.budsswitch.privileged

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.AttributionSource
import android.content.Context
import android.os.IBinder
import android.os.Process
import androidx.annotation.Keep
import java.lang.reflect.InvocationTargetException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import rikka.shizuku.SystemServiceHelper

@Keep
class PrivilegedBluetoothService(private val context: Context) :
    IBluetoothPrivilegedService.Stub() {

    private val adapter: BluetoothAdapter by lazy { createPrivilegedAdapter() }

    override fun ping(): String = buildString {
        append("ok uid=${Process.myUid()} pid=${Process.myPid()} api=${android.os.Build.VERSION.SDK_INT}")
        append(" attribution=${attributionPackageForUid(Process.myUid())}")
    }

    override fun connectDevice(address: String): String = runCatching {
        validate(address)
        val device = adapter.getRemoteDevice(address)
        val allProfiles = invokeDeviceInt(device, "connect")
        if (allProfiles == 0) return@runCatching "all-profiles=success"

        val a2dp = invokeProfile(BluetoothProfile.A2DP, device, "connect")
        val headset = invokeProfile(BluetoothProfile.HEADSET, device, "connect")
        val primary = if (allProfiles == Int.MIN_VALUE) "all-profiles API unavailable"
        else "all-profiles result=$allProfiles"
        "$primary; fallback A2DP=$a2dp HFP=$headset"
    }.getOrElse { rootMessage("connect failed", it) }

    override fun disconnectDevice(address: String): String = runCatching {
        validate(address)
        val device = adapter.getRemoteDevice(address)
        val allProfiles = invokeDeviceInt(device, "disconnect")
        if (allProfiles == 0) return@runCatching "all-profiles=success"

        val headset = invokeProfile(BluetoothProfile.HEADSET, device, "disconnect")
        val a2dp = invokeProfile(BluetoothProfile.A2DP, device, "disconnect")
        val primary = if (allProfiles == Int.MIN_VALUE) "all-profiles API unavailable"
        else "all-profiles result=$allProfiles"
        "$primary; fallback HFP=$headset A2DP=$a2dp"
    }.getOrElse { rootMessage("disconnect failed", it) }

    override fun connectionSummary(address: String): String = runCatching {
        validate(address)
        val device = adapter.getRemoteDevice(address)
        val a2dp = queryConnectionState(BluetoothProfile.A2DP, device)
        val headset = queryConnectionState(BluetoothProfile.HEADSET, device)
        "A2DP=${stateName(a2dp)} HFP=${stateName(headset)}"
    }.getOrElse { rootMessage("status failed", it) }

    override fun codecDiagnostics(address: String): String = runCatching {
        validate(address)
        val device = adapter.getRemoteDevice(address)
        val proxy = acquireProfile(BluetoothProfile.A2DP)
            ?: return@runCatching "A2DP proxy unavailable"
        try {
            val statusMethod = proxy.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
            val status = statusMethod.invoke(proxy, device)
                ?: return@runCatching "A2DP codec status unavailable (connect Buds first)"

            val current = callNoArg(status, "getCodecConfig")
            val local = callNoArg(status, "getCodecsLocalCapabilities") as? List<*> ?: emptyList<Any>()
            val selectable = callNoArg(status, "getCodecsSelectableCapabilities") as? List<*>
                ?: emptyList<Any>()
            val supported = (callNoArg(proxy, "getSupportedCodecTypes") as? Collection<*>)
                ?.toList() ?: emptyList<Any>()

            val visibleTypes = buildList<Any?> {
                addAll(supported)
                add(extractExtendedType(current))
                local.forEach { add(extractExtendedType(it)) }
                selectable.forEach { add(extractExtendedType(it)) }
            }.filterNotNull()

            val samsungTypes = visibleTypes
                .mapNotNull { type -> codecId(type)?.let { id -> id to type } }
                .filter { (id, _) -> isSamsungVendorCodec(id) }
                .distinctBy { (id, _) -> normalizeCodecId(id) }

            buildString {
                appendLine("=== A2DP codec diagnostics v2 ===")
                appendLine("device=$address")
                appendLine("current=${formatCodec(current)}")
                appendLine()
                appendLine("-- platform supported source codec types (${supported.size}) --")
                if (supported.isEmpty()) appendLine("(API unavailable / empty)")
                supported.forEachIndexed { i, type -> appendLine("P$i ${formatCodecType(type)}") }
                appendLine()
                appendLine("-- local capabilities (${local.size}) --")
                local.forEachIndexed { i, codec -> appendLine("L$i ${formatCodec(codec)}") }
                appendLine()
                appendLine("-- selectable with peer (${selectable.size}) --")
                appendLine("note: selectable is stack-filtered; it is NOT raw remote AVDTP capabilities")
                selectable.forEachIndexed { i, codec -> appendLine("S$i ${formatCodec(codec)}") }
                appendLine()
                appendLine("-- Samsung Company ID 0x0075 visible to this source stack --")
                if (samsungTypes.isEmpty()) {
                    appendLine("none")
                } else {
                    samsungTypes.forEach { (_, type) -> appendLine(formatCodecType(type)) }
                }
            }.trimEnd()
        } finally {
            runCatching { adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy) }
        }
    }.getOrElse { rootMessage("codec diagnostics failed", it) }

    private fun formatCodec(config: Any?): String {
        if (config == null) return "null"
        fun value(name: String): Any? = callNoArg(config, name)
        fun hex(v: Any?): String = when (v) {
            is Long -> "0x${java.lang.Long.toUnsignedString(v, 16)}"
            is Int -> "0x${Integer.toUnsignedString(v, 16)}"
            is Number -> "0x${java.lang.Long.toUnsignedString(v.toLong(), 16)}"
            else -> v?.toString() ?: "?"
        }

        val extended = extractExtendedType(config)
        val sampleRate = (value("getSampleRate") as? Number)?.toInt() ?: 0
        val bits = (value("getBitsPerSample") as? Number)?.toInt() ?: 0
        val channel = (value("getChannelMode") as? Number)?.toInt() ?: 0

        return buildString {
            append("ext=[${formatCodecType(extended)}]")
            append(" legacyType=${value("getCodecType")}")
            append(" rate=${hex(sampleRate)}(${decodeSampleRate(sampleRate)})")
            append(" bits=${hex(bits)}(${decodeBits(bits)})")
            append(" channel=${hex(channel)}(${decodeChannel(channel)})")
            append(" cs1=${hex(value("getCodecSpecific1"))}")
            append(" cs2=${hex(value("getCodecSpecific2"))}")
            append(" cs3=${hex(value("getCodecSpecific3"))}")
            append(" cs4=${hex(value("getCodecSpecific4"))}")
        }
    }

    private fun formatCodecType(type: Any?): String {
        if (type == null) return "null"
        val name = callNoArg(type, "getCodecName")?.toString() ?: "?"
        val id = codecId(type) ?: return "name=$name id=?"
        val normalized = normalizeCodecId(id)
        val audioCodecId = normalized and 0xffL
        val companyId = (normalized ushr 8) and 0xffffL
        val vendorCodecId = (normalized ushr 24) and 0xffffL
        val parsed = if (audioCodecId == 0xffL) {
            "vendor audio=0xff company=0x%04x vendorCodec=0x%04x".format(
                Locale.US,
                companyId,
                vendorCodecId
            )
        } else {
            "standard audio=0x%02x".format(Locale.US, audioCodecId)
        }
        val samsung = samsungLabel(id)?.let { " samsung=[$it]" }.orEmpty()
        return "name=$name id=${codecIdHex(id)} $parsed$samsung"
    }

    private fun extractExtendedType(config: Any?): Any? =
        config?.let { callNoArg(it, "getExtendedCodecType") }

    private fun codecId(type: Any?): Long? =
        (type?.let { callNoArg(it, "getCodecId") } as? Number)?.toLong()

    private fun normalizeCodecId(id: Long): Long = id and 0xffffffffffL

    private fun codecIdHex(id: Long): String =
        String.format(Locale.US, "0x%010x", normalizeCodecId(id))

    private fun isSamsungVendorCodec(id: Long): Boolean {
        val n = normalizeCodecId(id)
        return (n and 0xffL) == 0xffL && ((n ushr 8) and 0xffffL) == 0x0075L
    }

    private fun samsungLabel(id: Long): String? {
        if (!isSamsungVendorCodec(id)) return null
        val vendorCodec = ((normalizeCodecId(id) ushr 24) and 0xffffL).toInt()
        return when (vendorCodec) {
            0x0102 -> "Samsung HD"
            0x0103 -> "Samsung Scalable/Seamless family"
            0x0104 -> "Samsung UHQ candidate (community mapping; verify by S25 capture)"
            else -> "Samsung vendor codec 0x%04x".format(Locale.US, vendorCodec)
        }
    }

    private fun decodeSampleRate(mask: Int): String = decodeMask(
        mask,
        listOf(
            0x1 to "44.1k",
            0x2 to "48k",
            0x4 to "88.2k",
            0x8 to "96k",
            0x10 to "176.4k",
            0x20 to "192k"
        )
    )

    private fun decodeBits(mask: Int): String = decodeMask(
        mask,
        listOf(0x1 to "16", 0x2 to "24", 0x4 to "32")
    )

    private fun decodeChannel(mask: Int): String = decodeMask(
        mask,
        listOf(0x1 to "MONO", 0x2 to "STEREO")
    )

    private fun decodeMask(mask: Int, values: List<Pair<Int, String>>): String {
        if (mask == 0) return "none"
        val labels = values.filter { (bit, _) -> mask and bit != 0 }.map { it.second }
        return if (labels.isEmpty()) "unknown" else labels.joinToString("|")
    }

    private fun callNoArg(target: Any, methodName: String): Any? = runCatching {
        target.javaClass.getMethod(methodName).invoke(target)
    }.getOrNull()

    private fun createPrivilegedAdapter(): BluetoothAdapter {
        val uid = Process.myUid()
        val attributionUid = if (uid == 0) 1000 else uid
        val source = AttributionSource.Builder(attributionUid)
            .setPid(Process.myPid())
            .setPackageName(attributionPackageForUid(uid))
            .build()

        val binder: IBinder = SystemServiceHelper.getSystemService("bluetooth_manager")
            ?: error("ServiceManager bluetooth_manager returned null")
        val managerInterface = Class.forName("android.bluetooth.IBluetoothManager")
        val managerStub = Class.forName("android.bluetooth.IBluetoothManager\$Stub")
        val manager = managerStub.getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)
            ?: error("IBluetoothManager.Stub.asInterface returned null")

        val constructor = BluetoothAdapter::class.java.getDeclaredConstructor(
            managerInterface,
            AttributionSource::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(manager, source) as BluetoothAdapter
    }

    private fun attributionPackageForUid(uid: Int): String = when (uid) {
        0, 1000 -> "android"
        2000 -> "com.android.shell"
        else -> context.packageName
    }

    private fun invokeDeviceInt(device: BluetoothDevice, methodName: String): Int = try {
        val method = BluetoothDevice::class.java.getDeclaredMethod(methodName)
        method.isAccessible = true
        (method.invoke(device) as? Int) ?: Int.MIN_VALUE
    } catch (_: Throwable) {
        Int.MIN_VALUE
    }

    private fun invokeProfile(profile: Int, device: BluetoothDevice, methodName: String): String {
        val proxy = acquireProfile(profile) ?: return "proxy-unavailable"
        return try {
            val method = proxy.javaClass.getDeclaredMethod(methodName, BluetoothDevice::class.java)
            method.isAccessible = true
            method.invoke(proxy, device)?.toString() ?: "null"
        } catch (t: Throwable) {
            rootMessage("error", t)
        } finally {
            runCatching { adapter.closeProfileProxy(profile, proxy) }
        }
    }

    private fun queryConnectionState(profile: Int, device: BluetoothDevice): Int {
        val proxy = acquireProfile(profile) ?: return BluetoothProfile.STATE_DISCONNECTED
        return try { proxy.getConnectionState(device) }
        finally { runCatching { adapter.closeProfileProxy(profile, proxy) } }
    }

    private fun acquireProfile(profile: Int): BluetoothProfile? {
        val latch = CountDownLatch(1)
        var result: BluetoothProfile? = null
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profileId: Int, proxy: BluetoothProfile) {
                if (profileId == profile) {
                    result = proxy
                    latch.countDown()
                }
            }
            override fun onServiceDisconnected(profileId: Int) = Unit
        }
        if (!adapter.getProfileProxy(context, listener, profile)) return null
        latch.await(2500, TimeUnit.MILLISECONDS)
        return result
    }

    private fun validate(address: String) {
        require(BluetoothAdapter.checkBluetoothAddress(address)) { "invalid Bluetooth address" }
    }

    private fun stateName(state: Int): String = when (state) {
        BluetoothProfile.STATE_CONNECTED -> "connected"
        BluetoothProfile.STATE_CONNECTING -> "connecting"
        BluetoothProfile.STATE_DISCONNECTING -> "disconnecting"
        else -> "disconnected"
    }

    private fun rootMessage(prefix: String, t: Throwable): String {
        var x = t
        if (x is InvocationTargetException && x.targetException != null) x = x.targetException
        return "$prefix: ${x.javaClass.simpleName}: ${x.message ?: "(no message)"}"
    }

    override fun destroy() { System.exit(0) }
}
