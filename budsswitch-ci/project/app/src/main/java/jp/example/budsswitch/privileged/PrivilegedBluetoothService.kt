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
            val method = proxy.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
            val status = method.invoke(proxy, device)
                ?: return@runCatching "A2DP codec status unavailable (connect Buds first)"

            val current = callNoArg(status, "getCodecConfig")
            val local = callNoArg(status, "getCodecsLocalCapabilities") as? List<*> ?: emptyList<Any>()
            val selectable = callNoArg(status, "getCodecsSelectableCapabilities") as? List<*>
                ?: emptyList<Any>()

            buildString {
                appendLine("=== A2DP codec diagnostics ===")
                appendLine("device=$address")
                appendLine("current=${formatCodec(current)}")
                appendLine("-- local capabilities (${local.size}) --")
                local.forEachIndexed { i, codec -> appendLine("L$i ${formatCodec(codec)}") }
                appendLine("-- selectable with Buds (${selectable.size}) --")
                selectable.forEachIndexed { i, codec -> appendLine("S$i ${formatCodec(codec)}") }
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
            else -> v?.toString() ?: "?"
        }
        val extendedName = runCatching {
            val extended = callNoArg(config, "getExtendedCodecType") ?: return@runCatching null
            callNoArg(extended, "getCodecName")?.toString()
        }.getOrNull()

        return buildString {
            append("name=${extendedName ?: "?"}")
            append(" type=${value("getCodecType")}")
            append(" rate=${hex(value("getSampleRate"))}")
            append(" bits=${hex(value("getBitsPerSample"))}")
            append(" channel=${hex(value("getChannelMode"))}")
            append(" cs1=${hex(value("getCodecSpecific1"))}")
            append(" cs2=${hex(value("getCodecSpecific2"))}")
            append(" cs3=${hex(value("getCodecSpecific3"))}")
            append(" cs4=${hex(value("getCodecSpecific4"))}")
            append(" raw=${config.toString().replace('\n', ' ')}")
        }
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
