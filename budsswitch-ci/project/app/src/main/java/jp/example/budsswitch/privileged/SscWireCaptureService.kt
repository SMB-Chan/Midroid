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

    private data class SnoopRuntimeState(
        val activeMode: String?,
        val defaultMode: String?,
        val bluetoothOn: String,
        val dumpAvailable: Boolean
    )

    override fun ping(): String =
        "ok uid=${Process.myUid()} pid=${Process.myPid()} api=${android.os.Build.VERSION.SDK_INT}"

    override fun getSnoopStatus(): String = runCatching {
        val state = readActiveSnoopState()
        val rawPersist = shell("getprop persist.bluetooth.btsnooplogmode").trim()
        val rawPath = shell("getprop persist.bluetooth.btsnooppath").trim()
        val path = rawPath.ifBlank { "/data/misc/bluetooth/logs/btsnoop_hci.log" }
        val rawFilter = shell("getprop persist.bluetooth.snooplogfilter.profiles.a2dp.enabled").trim()
        val debuggable = shell("getprop ro.debuggable").trim().ifBlank { "?" }
        val stat = shell("ls -l '$path' '${path}.last' '${path}.filtered' '${path}.filtered.last' 2>&1")
        val ready = state.activeMode.equals("FULL", ignoreCase = true)

        buildString {
            appendLine("=== BLUETOOTH HCI SNOOP STATUS v2 ===")
            appendLine("uid=${Process.myUid()} attribution=${attributionSource.packageName}")
            appendLine("bluetooth_on=${state.bluetoothOn}")
            appendLine("active.sSnoopLogSettingAtEnable=${state.activeMode ?: "(not found)"}")
            appendLine("active.sDefaultSnoopLogSettingAtEnable=${state.defaultMode ?: "(not found)"}")
            appendLine("READY_FOR_CAPTURE=${if (ready) "YES" else "NO"}")
            appendLine("persist.bluetooth.btsnooplogmode(shell)=${rawPersist.ifBlank { "(blank/protected from shell)" }}")
            appendLine("persist.bluetooth.btsnooppath(shell)=${rawPath.ifBlank { "(blank/protected; using AOSP default)" }}")
            appendLine("effectivePath=$path")
            appendLine("persist.bluetooth.snooplogfilter.profiles.a2dp.enabled(shell)=${rawFilter.ifBlank { "(blank/protected or unset)" }}")
            appendLine("ro.debuggable=$debuggable")
            appendLine("dumpsysBluetoothManagerAvailable=${state.dumpAvailable}")
            appendLine("files:")
            appendLine(stat.trimEnd())
            when {
                ready -> appendLine("READY: Bluetooth stack was started with HCI snoop FULL.")
                state.activeMode.equals("EMPTY", true) -> {
                    appendLine("ACTION REQUIRED: persisted setting may already be Full, but the running Bluetooth stack started with EMPTY.")
                    appendLine("Use 'Bluetooth再起動（Full設定を実効化）', reconnect Buds3 Pro, then check again.")
                }
                else -> {
                    appendLine("ACTION REQUIRED: Developer options > Bluetooth HCI snoop log = Full, then restart Bluetooth.")
                }
            }
            appendLine("NOTE: Samsung user builds can deny shell reads of persist.bluetooth.* properties; active dumpsys state is authoritative for capture readiness here.")
            appendLine("NOTE: direct /data/misc/bluetooth/logs access can be denied even when Full is active; bugreport export can still carry btsnooz/HCI data.")
        }.trimEnd()
    }.getOrElse { t ->
        val root = rootThrowable(t)
        "SNOOP status failed: ${root.javaClass.simpleName}: ${root.message}"
    }

    override fun restartBluetoothForSnoop(): String = runCatching {
        val before = readActiveSnoopState()
        val started = System.currentTimeMillis()
        val disable = runCommand("/system/bin/svc", "bluetooth", "disable")
        val offObserved = waitForBluetoothState(false, 12_000L)
        Thread.sleep(700L)
        val enable = runCommand("/system/bin/svc", "bluetooth", "enable")
        val onObserved = waitForBluetoothState(true, 15_000L)
        Thread.sleep(2_000L)
        val after = readActiveSnoopState()
        buildString {
            appendLine("=== BLUETOOTH RESTART FOR HCI SNOOP ===")
            appendLine("before.activeMode=${before.activeMode ?: "?"} bluetoothOn=${before.bluetoothOn}")
            appendLine("disableExit=${disable.first} offObserved=$offObserved")
            if (disable.second.isNotBlank()) appendLine("disableOutput=${disable.second.trim()}")
            appendLine("enableExit=${enable.first} onObserved=$onObserved")
            if (enable.second.isNotBlank()) appendLine("enableOutput=${enable.second.trim()}")
            appendLine("after.activeMode=${after.activeMode ?: "?"} bluetoothOn=${after.bluetoothOn}")
            appendLine("elapsedMs=${System.currentTimeMillis() - started}")
            if (after.activeMode.equals("FULL", true)) {
                appendLine("RESTART_OK_ACTIVE_SNOOP_FULL")
                appendLine("Reconnect Galaxy Buds3 Pro and confirm SSC 96kHz before capture.")
            } else {
                appendLine("RESTART_DONE_BUT_ACTIVE_SNOOP_NOT_FULL")
                appendLine("Set Developer options > Bluetooth HCI snoop log = Full, then restart Bluetooth again.")
            }
        }.trimEnd()
    }.getOrElse { t ->
        val root = rootThrowable(t)
        "Bluetooth restart failed: ${root.javaClass.simpleName}: ${root.message}"
    }

    override fun runMarkedToggleExperiment(address: String): String = runCatching {
        require(BluetoothAdapter.checkBluetoothAddress(address)) { "invalid Bluetooth address" }
        val snoopState = readActiveSnoopState()
        if (!snoopState.activeMode.equals("FULL", ignoreCase = true)) {
            return@runCatching buildString {
                appendLine("=== SSC-UHQ WIRE CAPTURE EXPERIMENT ===")
                appendLine("CAPTURE_ABORTED_ACTIVE_SNOOP_NOT_FULL")
                appendLine("active.sSnoopLogSettingAtEnable=${snoopState.activeMode ?: "(not found)"}")
                appendLine("bluetooth_on=${snoopState.bluetoothOn}")
                appendLine("Set Developer options > Bluetooth HCI snoop log = Full, restart Bluetooth, reconnect Buds3 Pro, then retry.")
                appendLine("No codec state change was attempted.")
            }.trimEnd()
        }

        val device = remoteDevice(address)
        val a2dp = directProfileService(BluetoothProfile.A2DP)
            ?: error("direct A2DP binder unavailable")

        val supported = invokeCodecTypeQuery(a2dp, "semIsCodecSupported", device, 8)
        val enabled = invokeCodecTypeQuery(a2dp, "semIsCodecEnabled", device, 8)
        val before = codecSnapshot(a2dp, device)
        if (supported != true || enabled != true || !before.contains("codec=SSC") || !before.contains("rate=0x8")) {
            return@runCatching buildString {
                appendLine("=== SSC-UHQ WIRE CAPTURE EXPERIMENT ===")
                appendLine("PRECONDITION FAILED")
                appendLine("device=$address supported=$supported enabled=$enabled")
                appendLine("before=$before")
                appendLine("Expected active SSC-UHQ (SSC / rate=0x8) before changing state.")
                appendLine("No state change attempted.")
            }.trimEnd()
        }

        val report = StringBuilder()
        report.appendLine("=== SSC-UHQ WIRE CAPTURE EXPERIMENT ===")
        report.appendLine("device=$address uid=${Process.myUid()} attribution=${attributionSource.packageName}")
        report.appendLine("activeSnoopMode=${snoopState.activeMode}")
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

    override fun generateBugreport(prefix: String): String = runCatching {
        val safePrefix = prefix.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').ifBlank { "BudsSwitch-SSC-Wire" }
        val started = System.currentTimeMillis()
        val result = runCommand("/system/bin/bugreportz", "-p")
        val source = result.second.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("OK:") }
            ?.removePrefix("OK:")
            ?.trim()

        if (result.first != 0 || source.isNullOrBlank()) {
            return@runCatching buildString {
                appendLine("=== BUGREPORT EXPORT ===")
                appendLine("bugreportzExit=${result.first}")
                appendLine("BUGREPORT_FAILED")
                appendLine(result.second.trim())
            }.trimEnd()
        }

        runCommand("/system/bin/mkdir", "-p", "/sdcard/Download")
        val dest = "/sdcard/Download/${safePrefix}-${System.currentTimeMillis()}.zip"
        val copy = runCommand("/system/bin/cp", source, dest)
        if (copy.first != 0) error("copy failed exit=${copy.first}: ${copy.second.trim()}")
        runCommand("/system/bin/chmod", "0644", dest)
        val stat = runCommand("/system/bin/ls", "-l", dest).second.trim()

        buildString {
            appendLine("=== BUGREPORT EXPORT ===")
            appendLine("activeSnoopMode=${readActiveSnoopState().activeMode ?: "?"}")
            appendLine("bugreportzExit=${result.first}")
            appendLine("source=$source")
            appendLine("saved=$dest")
            appendLine("elapsedMs=${System.currentTimeMillis() - started}")
            appendLine("stat=$stat")
            appendLine("BUGREPORT_READY")
            appendLine("PRIVACY: Android bugreports can contain device, app, network and account-related diagnostics. Share only with a party you trust.")
        }.trimEnd()
    }.getOrElse { t ->
        val root = rootThrowable(t)
        "BUGREPORT export failed: ${root.javaClass.simpleName}: ${root.message}"
    }

    private fun readActiveSnoopState(): SnoopRuntimeState {
        val dump = shell("dumpsys bluetooth_manager 2>&1")
        val active = Regex("(?m)^\\s*sSnoopLogSettingAtEnable\\s*=\\s*([A-Za-z0-9_-]+)")
            .find(dump)?.groupValues?.getOrNull(1)
        val default = Regex("(?m)^\\s*sDefaultSnoopLogSettingAtEnable\\s*=\\s*([A-Za-z0-9_-]+)")
            .find(dump)?.groupValues?.getOrNull(1)
        val bluetoothOn = shell("settings get global bluetooth_on 2>/dev/null").trim().ifBlank { "?" }
        return SnoopRuntimeState(active, default, bluetoothOn, dump.isNotBlank() && !dump.contains("Permission Denial", true))
    }

    private fun waitForBluetoothState(enabled: Boolean, timeoutMs: Long): Boolean {
        val expected = if (enabled) "1" else "0"
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val state = shell("settings get global bluetooth_on 2>/dev/null").trim()
            if (state == expected) return true
            Thread.sleep(250L)
        }
        return false
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

    private fun runCommand(vararg args: String): Pair<Int, String> {
        val p = ProcessBuilder(*args).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().use { it.readText() }
        val code = p.waitFor()
        return code to text
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
