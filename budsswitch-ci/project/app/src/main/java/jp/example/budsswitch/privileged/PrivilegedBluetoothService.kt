package jp.example.budsswitch.privileged

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.AttributionSource
import android.content.Context
import android.os.Process
import androidx.annotation.Keep
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs as a Shizuku UserService (normally shell uid=2000).
 *
 * API 35 already contains hidden BluetoothDevice.connect()/disconnect() methods that
 * connect/disconnect all enabled profiles. We try those first. If an OEM blocks them,
 * we fall back to hidden BluetoothA2dp/BluetoothHeadset profile calls independently.
 *
 * The adapter is deliberately created with a shell/root AttributionSource. A Shizuku
 * UserService loads the app code into a privileged process, and using the app's normal
 * AttributionSource can otherwise make Bluetooth permission attribution disagree with
 * the Binder caller UID.
 */
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
        if (allProfiles == 0) {
            return@runCatching "connectAllEnabledProfiles=SUCCESS(0)"
        }

        val a2dp = invokeProfile(BluetoothProfile.A2DP, device, "connect")
        val headset = invokeProfile(BluetoothProfile.HEADSET, device, "connect")
        "connectAllEnabledProfiles=$allProfiles; fallback A2DP=$a2dp HFP=$headset"
    }.getOrElse { rootMessage("connect failed", it) }

    override fun disconnectDevice(address: String): String = runCatching {
        validate(address)
        val device = adapter.getRemoteDevice(address)

        val allProfiles = invokeDeviceInt(device, "disconnect")
        if (allProfiles == 0) {
            return@runCatching "disconnectAllEnabledProfiles=SUCCESS(0)"
        }

        val headset = invokeProfile(BluetoothProfile.HEADSET, device, "disconnect")
        val a2dp = invokeProfile(BluetoothProfile.A2DP, device, "disconnect")
        "disconnectAllEnabledProfiles=$allProfiles; fallback HFP=$headset A2DP=$a2dp"
    }.getOrElse { rootMessage("disconnect failed", it) }

    override fun connectionSummary(address: String): String = runCatching {
        validate(address)
        val device = adapter.getRemoteDevice(address)
        val acl = invokeDeviceBoolean(device, "isConnected")
        val a2dp = queryConnectionState(BluetoothProfile.A2DP, device)
        val headset = queryConnectionState(BluetoothProfile.HEADSET, device)
        "ACL=$acl A2DP=${stateName(a2dp)} HFP=${stateName(headset)}"
    }.getOrElse { rootMessage("status failed", it) }

    private fun createPrivilegedAdapter(): BluetoothAdapter {
        val uid = Process.myUid()
        val attributionUid = if (uid == 0) 1000 else uid
        val source = AttributionSource.Builder(attributionUid)
            .setPid(Process.myPid())
            .setPackageName(attributionPackageForUid(uid))
            .build()

        val method = BluetoothAdapter::class.java.getDeclaredMethod(
            "createAdapter",
            AttributionSource::class.java
        )
        method.isAccessible = true
        return method.invoke(null, source) as? BluetoothAdapter
            ?: error("BluetoothAdapter.createAdapter returned null")
    }

    private fun attributionPackageForUid(uid: Int): String = when (uid) {
        0, 1000 -> "android"
        2000 -> "com.android.shell"
        else -> context.packageName
    }

    private fun invokeDeviceInt(device: BluetoothDevice, methodName: String): Int =
        try {
            val method = BluetoothDevice::class.java.getDeclaredMethod(methodName)
            method.isAccessible = true
            (method.invoke(device) as? Int) ?: Int.MIN_VALUE
        } catch (t: Throwable) {
            Int.MIN_VALUE
        }

    private fun invokeDeviceBoolean(device: BluetoothDevice, methodName: String): Boolean =
        runCatching {
            val method = BluetoothDevice::class.java.getDeclaredMethod(methodName)
            method.isAccessible = true
            (method.invoke(device) as? Boolean) ?: false
        }.getOrDefault(false)

    private fun invokeProfile(profile: Int, device: BluetoothDevice, methodName: String): String {
        val proxy = acquireProfile(profile) ?: return "proxy-unavailable"
        return try {
            val method = proxy.javaClass.getDeclaredMethod(methodName, BluetoothDevice::class.java)
            method.isAccessible = true
            val result = method.invoke(proxy, device)
            result?.toString() ?: "null"
        } catch (t: Throwable) {
            rootMessage("error", t)
        } finally {
            runCatching { adapter.closeProfileProxy(profile, proxy) }
        }
    }

    private fun queryConnectionState(profile: Int, device: BluetoothDevice): Int {
        val proxy = acquireProfile(profile) ?: return BluetoothProfile.STATE_DISCONNECTED
        return try {
            proxy.getConnectionState(device)
        } finally {
            runCatching { adapter.closeProfileProxy(profile, proxy) }
        }
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

    override fun destroy() {
        System.exit(0)
    }
}
