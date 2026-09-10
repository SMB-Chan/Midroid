package jp.example.budsswitch.privileged

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.AttributionSource
import android.content.Context
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.Keep
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import rikka.shizuku.SystemServiceHelper

@Keep
class SscWireCaptureService(private val context: Context) : ISscWireCaptureService.Stub() {

    private val attributionSource: AttributionSource by lazy {
        val uid = Process.myUid()
        AttributionSource.Builder(if (uid == 0) 1000 else uid)
            .setPid(Process.myPid())
            .setPackageName(if (uid == 2000) "com.android.shell" else context.packageName)
            .build()
    }

    private val bluetoothManager: Any by lazy {
        val binder = SystemServiceHelper.getSystemService("bluetooth_manager")
            ?: error("bluetooth_manager binder unavailable")
        val stub = Class.forName("android.bluetooth.IBluetoothManager\$Stub")
        stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("IBluetoothManager.asInterface returned null")
    }

    private val adapter: BluetoothAdapter by lazy {
        val ctor = BluetoothAdapter::class.java.declaredConstructors.firstOrNull { c ->
            val p = c.parameterTypes
            p.size == 2 && p[0].name == "android.bluetooth.IBluetoothManager" &&
                Context::class.java.isAssignableFrom(p[1])
        } ?: error("BluetoothAdapter(IBluetoothManager, Context) constructor not found")
        ctor.isAccessible = true
        ctor.newInstance(bluetoothManager, context) as BluetoothAdapter
    }

    override fun ping(): String =
        "ok uid=${Process.myUid()} pid=${Process.myPid()} api=${android.os.Build.VERSION.SDK_INT}"

    override fun getSnoopStatus(): String = runCatching {
        val mode = shell("getprop persist.bluetooth.btsnooplogmode").trim().ifBlank { "(empty/disabled-default)" }
        val configuredPath = shell("getprop persist.bluetooth.btsnooppath").trim()
        val path = configuredPath.ifBlank { "/data/misc/bluetooth/logs/btsnoop_hci.log" }
        val filterA2dp = shell("getprop persist.bluetooth.snooplogfilter.profiles.a2dp.enabled").trim().ifBlank { "(unset)" }
        val debuggable = shell("getprop ro.debuggable").trim().ifBlank { "?" }
        val stat = shell("ls -l '$path' '${path}.last' '${path}.filtered' '${path}.filtered.last' 2>&1")
        buildString {
            appendLine("=== BLUETOOTH HCI SNOOP STATUS ===")
            appendLine("uid=${Process.myUid()} attribution=${attributionSource.packageName}")
            appendLine("persist.bluetooth.btsnooplogmode=$mode")
            appendLine("persist.bluetooth.btsnooppath=${configuredPath.ifBlank { "(unset; AOSP default)" }}")
            appendLine("effectivePath=$path")
            appendLine("persist.bluetooth.snooplogfilter.profiles.a2dp.enabled=$filterA2dp")
            appendLine("ro.debuggable=$debuggable")
            appendLine("files:")
            appendLine(stat.trimEnd())
            appendLine("RECOMMENDATION: set Developer options > Bluetooth HCI snoop log = Full before wire capture.")
            appendLine("Filtered mode may omit A2DP media payloads; Full is preferred for codec-byte analysis.")
        }.trimEnd()
    }.getOrElse { t ->
        val root = rootThrowable(t)
        "SNOOP status failed: ${root.javaClass.simpleName}: ${root.message}"
    }

    override fun runMarkedToggleExperiment(address: String): String = runCatching {
        require(BluetoothAdapter.checkBluetoothAddress(address)) { "invalid Bluetooth address" }
        val device = remoteDevice(address)
        val a2dp = directProfileService(BluetoothProfile.A2DP)
            ?: error("direct A2DP binder unavailable")

        val supported = invokeCodecTypeQuery(a2dp, "semIsCodecSupported", device, 8)
        val enabled = invokeCodecTypeQuery(a2dp, "semIsCodecEnabled", device, 8)
        val before = codecSnapshot(a2dp, device)
        if (supported != true || enabled != true) {
            return@runCatching buildString {
                appendLine("=== SSC-UHQ WIRE CAPTURE EXPERIMENT ===")
                appendLine("PRECONDITION FAILED")
                appendLine("device=$address supported=$supported enabled=$enabled")
                appendLine("before=$before")
                appendLine("No state change attempted.")
            }.trimEnd()
        }

        val report = StringBuilder()
        report.appendLine("=== SSC-UHQ WIRE CAPTURE EXPERIMENT ===")
        report.appendLine("device=$address uid=${Process.myUid()} attribution=${attributionSource.packageName}")
        report.appendLine("before=$before")
        report.appendLine(marker("CAPTURE_BEGIN", address))

        var restoreFailure: Throwable? = null
        try {
            report.appendLine(marker("UHQ_DISABLE_BEFORE_CALL", address))
            val disableResult = invokeCodecTypeSet(a2dp, device, false, 8)
            report.appendLine("semSetCodecEnabled(false,8)=$disableResult")
            report.appendLine(marker("UHQ_DISABLE_AFTER_CALL", address))
            Thread.sleep(1800L)
            report.appendLine("afterDisableEnabled=${invokeCodecTypeQuery(a2dp, "semIsCodecEnabled", device, 8)}")
            report.appendLine("afterDisable=${codecSnapshot(a2dp, device)}")
            report.appendLine(marker("UHQ_DISABLE_OBSERVED", address))
        } catch (t: Throwable) {
            val root = rootThrowable(t)
            report.appendLine("disablePhaseError=${root.javaClass.simpleName}: ${root.message}")
        } finally {
            try {
                report.appendLine(marker("UHQ_RESTORE_BEFORE_CALL", address))
                val restoreResult = invokeCodecTypeSet(a2dp, device, true, 8)
                report.appendLine("semSetCodecEnabled(true,8)=$restoreResult")
                report.appendLine(marker("UHQ_RESTORE_AFTER_CALL", address))
                Thread.sleep(1800L)
                report.appendLine("afterRestoreEnabled=${invokeCodecTypeQuery(a2dp, "semIsCodecEnabled", device, 8)}")
                report.appendLine("afterRestore=${codecSnapshot(a2dp, device)}")
                report.appendLine(marker("UHQ_RESTORE_OBSERVED", address))
            } catch (t: Throwable) {
                restoreFailure = rootThrowable(t)
                report.appendLine("RESTORE_FAILED=${restoreFailure!!.javaClass.simpleName}: ${restoreFailure!!.message}")
            }
        }
        report.appendLine(marker("CAPTURE_END", address))
        report.appendLine(if (restoreFailure == null) "verdict=CAPTURE_MARKERS_COMPLETE_AND_UHQ_RESTORED" else "verdict=RESTORE_FAILED_MANUAL_CHECK_REQUIRED")
        report.appendLine("logcatTag=$TAG markerPrefix=SSC_WIRE_MARK")
        report.toString().trimEnd()
    }.getOrElse { t ->
        val root = rootThrowable(t)
        "WIRE experiment failed: ${root.javaClass.simpleName}: ${root.message}"
    }

    private fun marker(label: String, address: String): String {
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtimeNanos()
        val text = "SSC_WIRE_MARK label=$label wallMs=$wall elapsedNs=$elapsed device=$address"
        Log.i(TAG, text)
        return "MARK $label wallMs=$wall elapsedNs=$elapsed"
    }

    private fun shell(command: String): String {
        val p = ProcessBuilder("/system/bin/sh", "-c", command).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        return text
    }

    private fun directProfileService(profile: Int): Any? {
        val core = findFieldValue(adapter, "android.bluetooth.IBluetooth")
            ?: error("IBluetooth field not initialized in runtime adapter")
        val getProfile = allMethods(core).firstOrNull { m ->
            m.name == "getProfile" && m.parameterTypes.size == 1 && isInt(m.parameterTypes[0])
        } ?: error("IBluetooth.getProfile(int) not found")
        getProfile.isAccessible = true
        val raw = getProfile.invoke(core, profile) ?: return null
        val binder = asBinder(raw) ?: return null
        val stub = Class.forName("android.bluetooth.IBluetoothA2dp\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
    }

    private fun remoteDevice(address: String): BluetoothDevice {
        runCatching { return adapter.getRemoteDevice(address) }
        val ctor = BluetoothDevice::class.java.declaredConstructors
            .filter { it.parameterTypes.isNotEmpty() && it.parameterTypes[0] == String::class.java }
            .minByOrNull { it.parameterCount }
            ?: error("BluetoothDevice constructor unavailable")
        val args = Array<Any?>(ctor.parameterCount) { i ->
            val type = ctor.parameterTypes[i]
            when {
                i == 0 -> address
                type.name == AttributionSource::class.java.name -> attributionSource
                isInt(type) -> 0
                type == Boolean::class.javaPrimitiveType -> false
                !type.isPrimitive -> null
                else -> 0
            }
        }
        ctor.isAccessible = true
        return ctor.newInstance(*args) as BluetoothDevice
    }

    private fun codecSnapshot(target: Any, device: BluetoothDevice): String = runCatching {
        val status = invokeDeviceQuery(target, "getCodecStatus", device)
        val current = status?.let { callNoArg(it, "getCodecConfig") }
        val ext = current?.let { callNoArg(it, "getExtendedCodecType") }
        buildString {
            append("codec=${ext?.let { callNoArg(it, "getCodecName") } ?: "?"}")
            append(" id=${ext?.let { numberNoArg(it, "getCodecId") }?.let(::hex) ?: "?"}")
            append(" rate=${current?.let { numberNoArg(it, "getSampleRate") }?.let(::hex) ?: "?"}")
            append(" bits=${current?.let { numberNoArg(it, "getBitsPerSample") }?.let(::hex) ?: "?"}")
            append(" channel=${current?.let { numberNoArg(it, "getChannelMode") }?.let(::hex) ?: "?"}")
            append(" cs1=${current?.let { numberNoArg(it, "getCodecSpecific1") }?.let(::hex) ?: "?"}")
            append(" cs2=${current?.let { numberNoArg(it, "getCodecSpecific2") }?.let(::hex) ?: "?"}")
            append(" cs3=${current?.let { numberNoArg(it, "getCodecSpecific3") }?.let(::hex) ?: "?"}")
            append(" cs4=${current?.let { numberNoArg(it, "getCodecSpecific4") }?.let(::hex) ?: "?"}")
        }
    }.getOrElse { t ->
        val root = rootThrowable(t)
        "codecSnapshotError=${root.javaClass.simpleName}:${root.message}"
    }

    private fun invokeCodecTypeQuery(target: Any, name: String, device: BluetoothDevice, codecType: Int): Boolean? {
        for (m in allMethods(target).filter { it.name == name }.sortedBy { it.parameterCount }) {
            val args = buildArgs(m, device, codecType, false) ?: continue
            runCatching {
                m.isAccessible = true
                return m.invoke(target, *args) as? Boolean
            }
        }
        error("$name invocation failed")
    }

    private fun invokeCodecTypeSet(target: Any, device: BluetoothDevice, enabled: Boolean, codecType: Int): String {
        for (m in allMethods(target).filter { it.name == "semSetCodecEnabled" }.sortedBy { it.parameterCount }) {
            val args = buildArgs(m, device, codecType, enabled) ?: continue
            runCatching {
                m.isAccessible = true
                val result = m.invoke(target, *args)
                return result?.toString() ?: "void"
            }
        }
        error("semSetCodecEnabled invocation failed")
    }

    private fun invokeDeviceQuery(target: Any, name: String, device: BluetoothDevice): Any? {
        for (m in allMethods(target).filter { it.name == name }.sortedBy { it.parameterCount }) {
            val args = buildArgs(m, device, 0, false) ?: continue
            runCatching {
                m.isAccessible = true
                return m.invoke(target, *args)
            }
        }
        error("$name invocation failed")
    }

    private fun buildArgs(method: Method, device: BluetoothDevice, intValue: Int, booleanValue: Boolean): Array<Any?>? {
        var intUsed = false
        var boolUsed = false
        val args = arrayOfNulls<Any?>(method.parameterCount)
        method.parameterTypes.forEachIndexed { i, type ->
            args[i] = when {
                BluetoothDevice::class.java.isAssignableFrom(type) -> device
                type.name == AttributionSource::class.java.name -> attributionSource
                isInt(type) -> if (!intUsed) { intUsed = true; intValue } else 0
                type == Boolean::class.javaPrimitiveType || type == Boolean::class.java -> if (!boolUsed) { boolUsed = true; booleanValue } else false
                type == Long::class.javaPrimitiveType || type == Long::class.java -> 0L
                !type.isPrimitive -> null
                else -> return null
            }
        }
        return args
    }

    private fun allMethods(target: Any): List<Method> {
        val out = LinkedHashMap<String, Method>()
        fun walk(cls: Class<*>?) {
            if (cls == null) return
            cls.declaredMethods.forEach { out[it.toGenericString()] = it }
            cls.interfaces.forEach(::walk)
            walk(cls.superclass)
        }
        walk(target.javaClass)
        return out.values.toList()
    }

    private fun findFieldValue(instance: Any, typeName: String): Any? {
        var cls: Class<*>? = instance.javaClass
        while (cls != null) {
            for (field in cls.declaredFields) {
                if (field.type.name != typeName) continue
                val value = runCatching {
                    field.isAccessible = true
                    field.get(if (Modifier.isStatic(field.modifiers)) null else instance)
                }.getOrNull()
                if (value != null) return value
            }
            cls = cls.superclass
        }
        return null
    }

    private fun asBinder(value: Any?): IBinder? = when (value) {
        null -> null
        is IBinder -> value
        else -> runCatching { value.javaClass.getMethod("asBinder").invoke(value) as? IBinder }.getOrNull()
    }

    private fun callNoArg(target: Any, name: String): Any? {
        val m = allMethods(target).firstOrNull { it.name == name && it.parameterCount == 0 }
            ?: error("$name() not found")
        m.isAccessible = true
        return m.invoke(target)
    }

    private fun numberNoArg(target: Any, name: String): Long? = (callNoArg(target, name) as? Number)?.toLong()
    private fun isInt(type: Class<*>): Boolean = type == Int::class.javaPrimitiveType || type == Int::class.java
    private fun rootThrowable(t: Throwable): Throwable { var cur = t; while (cur.cause != null && cur.cause !== cur) cur = cur.cause!!; return cur }
    private fun hex(v: Long): String = "0x${java.lang.Long.toUnsignedString(v, 16)} ($v)"

    override fun destroy() { System.exit(0) }

    companion object { private const val TAG = "BudsSwitchWire" }
}
