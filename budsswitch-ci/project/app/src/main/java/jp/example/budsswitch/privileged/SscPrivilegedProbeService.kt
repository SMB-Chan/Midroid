package jp.example.budsswitch.privileged

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.AttributionSource
import android.content.Context
import android.os.IBinder
import android.os.Process
import androidx.annotation.Keep
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import rikka.shizuku.SystemServiceHelper

@Keep
class SscPrivilegedProbeService(private val context: Context) :
    ISscPrivilegedProbeService.Stub() {

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
            p.size == 2 &&
                p[0].name == "android.bluetooth.IBluetoothManager" &&
                Context::class.java.isAssignableFrom(p[1])
        } ?: error("BluetoothAdapter(IBluetoothManager, Context) constructor not found")
        ctor.isAccessible = true
        ctor.newInstance(bluetoothManager, context) as BluetoothAdapter
    }

    override fun ping(): String =
        "ok uid=${Process.myUid()} pid=${Process.myPid()} api=${android.os.Build.VERSION.SDK_INT}"

    override fun probe(address: String): String = runCatching {
        require(BluetoothAdapter.checkBluetoothAddress(address)) { "invalid Bluetooth address" }
        val device = remoteDevice(address)
        val a2dp = directProfileService(BluetoothProfile.A2DP)
            ?: error("direct A2DP binder unavailable")

        val supported = invokeCodecTypeQuery(a2dp, "semIsCodecSupported", device, 8)
        val enabled = invokeCodecTypeQuery(a2dp, "semIsCodecEnabled", device, 8)
        val snapshot = codecSnapshot(a2dp, device)

        buildString {
            appendLine("=== PRIVILEGED SSC-UHQ PROBE ===")
            appendLine("uid=${Process.myUid()} attribution=${attributionSource.packageName}")
            appendLine("device=$address")
            appendLine("SEM_CODEC_TYPE_SSC_UHQ=8")
            appendLine("semIsCodecSupported(device,8)=$supported")
            appendLine("semIsCodecEnabled(device,8)=$enabled")
            appendLine(snapshot)
            appendLine("binderClass=${a2dp.javaClass.name}")
            appendLine("codecMethods=${codecMethodNames(a2dp)}")
            appendLine(
                when {
                    supported == true && enabled == true -> "verdict=CONFIRMED_SUPPORTED_AND_ENABLED"
                    supported == true && enabled == false -> "verdict=SUPPORTED_BUT_DISABLED"
                    supported == false -> "verdict=REPORTED_UNSUPPORTED"
                    else -> "verdict=QUERY_INCONCLUSIVE"
                }
            )
            appendLine("NOTE: read-only; semSetCodecEnabled was NOT called")
        }.trimEnd()
    }.getOrElse { t ->
        val root = rootThrowable(t)
        "PRIVILEGED probe failed: ${root.javaClass.simpleName}: ${root.message}"
    }

    override fun runToggleExperiment(address: String): String = runCatching {
        require(BluetoothAdapter.checkBluetoothAddress(address)) { "invalid Bluetooth address" }
        val device = remoteDevice(address)
        val a2dp = directProfileService(BluetoothProfile.A2DP)
            ?: error("direct A2DP binder unavailable")

        val supportedBefore = invokeCodecTypeQuery(a2dp, "semIsCodecSupported", device, 8)
        val enabledBefore = invokeCodecTypeQuery(a2dp, "semIsCodecEnabled", device, 8)
        val before = codecSnapshot(a2dp, device)

        if (supportedBefore != true || enabledBefore != true) {
            return@runCatching buildString {
                appendLine("=== SSC-UHQ CAUSAL TOGGLE EXPERIMENT ===")
                appendLine("device=$address")
                appendLine("PRECONDITION FAILED")
                appendLine("supportedBefore=$supportedBefore enabledBefore=$enabledBefore")
                appendLine("before: $before")
                appendLine("No state change was attempted.")
            }.trimEnd()
        }

        val report = StringBuilder()
        report.appendLine("=== SSC-UHQ CAUSAL TOGGLE EXPERIMENT ===")
        report.appendLine("uid=${Process.myUid()} attribution=${attributionSource.packageName}")
        report.appendLine("device=$address")
        report.appendLine("type=8 / SEM_CODEC_TYPE_SSC_UHQ")
        report.appendLine("beforeEnabled=$enabledBefore")
        report.appendLine("before: $before")
        report.appendLine("--- phase 1: disable type=8 ---")

        var disableObserved: Boolean? = null
        var restoreObserved: Boolean? = null
        var afterDisableSnapshot = "unavailable"
        var afterRestoreSnapshot = "unavailable"
        var restoreFailure: Throwable? = null

        try {
            val disableResult = invokeCodecTypeSet(a2dp, device, enabled = false, codecType = 8)
            report.appendLine("semSetCodecEnabled(device,false,8)=$disableResult")
            Thread.sleep(1800L)
            disableObserved = invokeCodecTypeQuery(a2dp, "semIsCodecEnabled", device, 8)
            afterDisableSnapshot = codecSnapshot(a2dp, device)
            report.appendLine("enabledAfterDisable=$disableObserved")
            report.appendLine("afterDisable: $afterDisableSnapshot")
        } catch (t: Throwable) {
            val root = rootThrowable(t)
            report.appendLine("disablePhaseError=${root.javaClass.simpleName}: ${root.message}")
        } finally {
            report.appendLine("--- phase 2: restore type=8 ---")
            try {
                val restoreResult = invokeCodecTypeSet(a2dp, device, enabled = true, codecType = 8)
                report.appendLine("semSetCodecEnabled(device,true,8)=$restoreResult")
                Thread.sleep(1800L)
                restoreObserved = invokeCodecTypeQuery(a2dp, "semIsCodecEnabled", device, 8)
                afterRestoreSnapshot = codecSnapshot(a2dp, device)
                report.appendLine("enabledAfterRestore=$restoreObserved")
                report.appendLine("afterRestore: $afterRestoreSnapshot")
            } catch (t: Throwable) {
                restoreFailure = rootThrowable(t)
                report.appendLine(
                    "RESTORE_FAILED=${restoreFailure!!.javaClass.simpleName}: ${restoreFailure!!.message}"
                )
            }
        }

        report.appendLine("--- verdict ---")
        report.appendLine(
            when {
                restoreFailure != null -> "verdict=RESTORE_FAILED_MANUAL_CHECK_REQUIRED"
                disableObserved == false && restoreObserved == true -> "verdict=TYPE8_CONTROL_CONFIRMED"
                disableObserved == true && restoreObserved == true -> "verdict=SET_CALL_ACCEPTED_BUT_ENABLE_FLAG_DID_NOT_DROP"
                else -> "verdict=INCONCLUSIVE"
            }
        )
        report.appendLine("NOTE: experiment automatically restored type=8 to enabled=true in finally.")
        report.toString().trimEnd()
    }.getOrElse { t ->
        val root = rootThrowable(t)
        "TOGGLE experiment failed before execution: ${root.javaClass.simpleName}: ${root.message}"
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
        val rate = current?.let { numberNoArg(it, "getSampleRate") }
        val bits = current?.let { numberNoArg(it, "getBitsPerSample") }
        val channel = current?.let { numberNoArg(it, "getChannelMode") }
        val ext = current?.let { callNoArg(it, "getExtendedCodecType") }
        val codecId = ext?.let { numberNoArg(it, "getCodecId") }
        val codecName = ext?.let { callNoArg(it, "getCodecName")?.toString() }
        val cs1 = current?.let { numberNoArg(it, "getCodecSpecific1") }
        val cs2 = current?.let { numberNoArg(it, "getCodecSpecific2") }
        val cs3 = current?.let { numberNoArg(it, "getCodecSpecific3") }
        val cs4 = current?.let { numberNoArg(it, "getCodecSpecific4") }
        buildString {
            append("codec=${codecName ?: "?"}")
            append(" id=${codecId?.let(::hex) ?: "?"}")
            append(" rate=${rate?.let(::hex) ?: "?"}")
            append(" bits=${bits?.let(::hex) ?: "?"}")
            append(" channel=${channel?.let(::hex) ?: "?"}")
            append(" cs1=${cs1?.let(::hex) ?: "?"}")
            append(" cs2=${cs2?.let(::hex) ?: "?"}")
            append(" cs3=${cs3?.let(::hex) ?: "?"}")
            append(" cs4=${cs4?.let(::hex) ?: "?"}")
        }
    }.getOrElse { t ->
        val root = rootThrowable(t)
        "codecSnapshotError=${root.javaClass.simpleName}:${root.message}"
    }

    private fun invokeCodecTypeQuery(
        target: Any,
        name: String,
        device: BluetoothDevice,
        codecType: Int
    ): Boolean? {
        val methods = allMethods(target).filter { it.name == name }
        if (methods.isEmpty()) error("$name not found on ${target.javaClass.name}")
        val failures = mutableListOf<String>()
        for (m in methods.sortedBy { it.parameterCount }) {
            val args = buildArgs(m, device, codecType, booleanValue = false) ?: continue
            try {
                m.isAccessible = true
                return m.invoke(target, *args) as? Boolean
            } catch (t: Throwable) {
                failures += "${m.parameterTypes.joinToString(",") { it.simpleName }}:${rootThrowable(t).javaClass.simpleName}:${rootThrowable(t).message}"
            }
        }
        error("$name invocation failed: ${failures.joinToString(" | ")}")
    }

    private fun invokeCodecTypeSet(
        target: Any,
        device: BluetoothDevice,
        enabled: Boolean,
        codecType: Int
    ): String {
        val methods = allMethods(target).filter { it.name == "semSetCodecEnabled" }
        if (methods.isEmpty()) error("semSetCodecEnabled not found on ${target.javaClass.name}")
        val failures = mutableListOf<String>()
        for (m in methods.sortedBy { it.parameterCount }) {
            val args = buildArgs(m, device, codecType, booleanValue = enabled) ?: continue
            try {
                m.isAccessible = true
                val result = m.invoke(target, *args)
                return result?.toString() ?: "void"
            } catch (t: Throwable) {
                failures += "${m.parameterTypes.joinToString(",") { it.simpleName }}:${rootThrowable(t).javaClass.simpleName}:${rootThrowable(t).message}"
            }
        }
        error("semSetCodecEnabled invocation failed: ${failures.joinToString(" | ")}")
    }

    private fun invokeDeviceQuery(target: Any, name: String, device: BluetoothDevice): Any? {
        val methods = allMethods(target).filter { it.name == name }
        if (methods.isEmpty()) error("$name not found on ${target.javaClass.name}")
        val failures = mutableListOf<String>()
        for (m in methods.sortedBy { it.parameterCount }) {
            val args = buildArgs(m, device, 0, booleanValue = false) ?: continue
            try {
                m.isAccessible = true
                return m.invoke(target, *args)
            } catch (t: Throwable) {
                failures += "${m.parameterTypes.joinToString(",") { it.simpleName }}:${rootThrowable(t).javaClass.simpleName}:${rootThrowable(t).message}"
            }
        }
        error("$name invocation failed: ${failures.joinToString(" | ")}")
    }

    private fun buildArgs(
        method: Method,
        device: BluetoothDevice,
        intValue: Int,
        booleanValue: Boolean
    ): Array<Any?>? {
        var intUsed = false
        var boolUsed = false
        val args = arrayOfNulls<Any?>(method.parameterCount)
        method.parameterTypes.forEachIndexed { i, type ->
            args[i] = when {
                BluetoothDevice::class.java.isAssignableFrom(type) -> device
                type.name == AttributionSource::class.java.name -> attributionSource
                isInt(type) -> {
                    if (!intUsed) {
                        intUsed = true
                        intValue
                    } else 0
                }
                type == Boolean::class.javaPrimitiveType || type == Boolean::class.java -> {
                    if (!boolUsed) {
                        boolUsed = true
                        booleanValue
                    } else false
                }
                type == Long::class.javaPrimitiveType || type == Long::class.java -> 0L
                !type.isPrimitive -> null
                else -> return null
            }
        }
        return args
    }

    private fun codecMethodNames(target: Any): String = allMethods(target)
        .map { it.name }
        .filter {
            val n = it.lowercase()
            n.contains("codec") || n.contains("sem") || n.contains("uhq") || n.contains("ssc")
        }
        .distinct()
        .sorted()
        .joinToString(",")
        .ifBlank { "none" }

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

    private fun numberNoArg(target: Any, name: String): Long? =
        (callNoArg(target, name) as? Number)?.toLong()

    private fun isInt(type: Class<*>): Boolean =
        type == Int::class.javaPrimitiveType || type == Int::class.java

    private fun rootThrowable(t: Throwable): Throwable {
        var cur = t
        while (cur.cause != null && cur.cause !== cur) cur = cur.cause!!
        return cur
    }

    private fun hex(v: Long): String = "0x${java.lang.Long.toUnsignedString(v, 16)} ($v)"

    override fun destroy() {
        System.exit(0)
    }
}
