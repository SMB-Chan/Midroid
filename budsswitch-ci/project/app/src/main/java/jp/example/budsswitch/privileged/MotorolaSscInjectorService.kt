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
class MotorolaSscInjectorService(private val context: Context) : IMotorolaSscInjectorService.Stub() {

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

    private val adapter: BluetoothAdapter by lazy { createRuntimeAdapter() }

    override fun ping(): String =
        "ok uid=${Process.myUid()} pid=${Process.myPid()} api=${android.os.Build.VERSION.SDK_INT} adapter=${adapter.javaClass.name}"

    override fun diagnose(address: String): String = runCatching {
        require(BluetoothAdapter.checkBluetoothAddress(address)) { "invalid Bluetooth address" }
        val device = remoteDevice(address)
        val a2dp = directProfileService(BluetoothProfile.A2DP)
            ?: error("direct A2DP binder unavailable")
        val status = invokeDeviceQuery(a2dp, "getCodecStatus", device)
        val current = status?.let { callNoArg(it, "getCodecConfig") }
        val local = status?.let { callNoArg(it, "getCodecsLocalCapabilities") as? Collection<*> }.orEmpty()
        val selectable = status?.let { callNoArg(it, "getCodecsSelectableCapabilities") as? Collection<*> }.orEmpty()
        val supportedTypes = invokeNoDeviceQuery(a2dp, "getSupportedCodecTypes") as? Collection<*>

        val localHasSsc = local.any { configCodecId(it) == SSC_CODEC_ID }
        val selectableHasSsc = selectable.any { configCodecId(it) == SSC_CODEC_ID }
        val platformHasSsc = supportedTypes?.any { codecTypeId(it) == SSC_CODEC_ID } == true
        val samsungSemField = runCatching {
            Class.forName("android.bluetooth.BluetoothA2dp").getDeclaredField("SEM_CODEC_TYPE_SSC_UHQ").apply { isAccessible = true }.getInt(null)
        }.getOrNull()

        buildString {
            appendLine("=== MOTOROLA SSC-UHQ READINESS ===")
            appendLine("device=$address uid=${Process.myUid()} attribution=${attributionSource.packageName}")
            appendLine("manufacturer=${android.os.Build.MANUFACTURER} model=${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT}")
            appendLine("targetSSC=0x${SSC_CODEC_ID.toString(16)} company=0x0075 vendorCodec=0x0003 legacyType=7")
            appendLine("current=${describeConfig(current)}")
            appendLine("platformSupportedHasSSC=$platformHasSsc")
            appendLine("localCapabilitiesHasSSC=$localHasSsc")
            appendLine("selectableWithPeerHasSSC=$selectableHasSsc")
            appendLine("SEM_CODEC_TYPE_SSC_UHQ=${samsungSemField?.let { "0x${it.toString(16)}" } ?: "absent"}")
            appendLine("-- platform supported source codec types --")
            if (supportedTypes == null) appendLine("unavailable") else supportedTypes.forEachIndexed { i, v -> appendLine("P$i ${describeCodecType(v)}") }
            appendLine("-- local capabilities --")
            local.forEachIndexed { i, v -> appendLine("L$i ${describeConfig(v)}") }
            appendLine("-- selectable with Buds --")
            selectable.forEachIndexed { i, v -> appendLine("S$i ${describeConfig(v)}") }
            appendLine("-- injector candidate construction --")
            appendLine("SSC48=${describeConfig(buildSscConfig(false))}")
            appendLine("SSCUHQ=${describeConfig(buildSscConfig(true))}")
            appendLine("verdict=${if (platformHasSsc || localHasSsc) "SSC_NATIVE_PATH_PRESENT" else "SSC_NATIVE_PATH_NOT_REGISTERED"}")
            if (!platformHasSsc && !localHasSsc) {
                appendLine("NOTE: framework injection can still be attempted, but success requires a native/source codec implementation below BluetoothCodecConfig.")
            }
        }.trimEnd()
    }.getOrElse { t ->
        val root = rootThrowable(t)
        "DIAGNOSE failed: ${root.javaClass.simpleName}: ${root.message}"
    }

    override fun tryActivate(address: String, uhq: Boolean): String = runCatching {
        require(BluetoothAdapter.checkBluetoothAddress(address)) { "invalid Bluetooth address" }
        val device = remoteDevice(address)
        val a2dp = directProfileService(BluetoothProfile.A2DP)
            ?: error("direct A2DP binder unavailable")
        val beforeStatus = invokeDeviceQuery(a2dp, "getCodecStatus", device)
        val beforeConfig = beforeStatus?.let { callNoArg(it, "getCodecConfig") }
        val candidate = buildSscConfig(uhq)
        val desiredRate = if (uhq) 0x8L else 0x2L
        val desiredBits = if (uhq) 0x8L else 0x2L

        val report = StringBuilder()
        report.appendLine("=== MOTOROLA SSC ${if (uhq) "UHQ 96k" else "48k/24"} ACTIVATION ATTEMPT ===")
        report.appendLine("device=$address uid=${Process.myUid()} attribution=${attributionSource.packageName}")
        report.appendLine("before=${describeConfig(beforeConfig)}")
        report.appendLine("candidate=${describeConfig(candidate)}")

        val callResult = invokeSetCodecPreference(a2dp, device, candidate)
        report.appendLine("setCodecConfigPreference=$callResult")
        Thread.sleep(2200L)

        val afterStatus = invokeDeviceQuery(a2dp, "getCodecStatus", device)
        val afterConfig = afterStatus?.let { callNoArg(it, "getCodecConfig") }
        val afterId = configCodecId(afterConfig)
        val afterRate = numberNoArgOrNull(afterConfig, "getSampleRate")
        val afterBits = numberNoArgOrNull(afterConfig, "getBitsPerSample")
        val success = afterId == SSC_CODEC_ID && afterRate == desiredRate && afterBits == desiredBits
        report.appendLine("after=${describeConfig(afterConfig)}")

        if (success) {
            report.appendLine("verdict=SSC_${if (uhq) "UHQ" else "48"}_ACTIVATED")
            report.appendLine("NOTE: activation succeeded; SSC is intentionally left selected.")
        } else {
            report.appendLine("verdict=FRAMEWORK_REQUEST_REJECTED_OR_FELL_BACK")
            if (beforeConfig != null) {
                val restore = runCatching { invokeSetCodecPreference(a2dp, device, beforeConfig) }
                    .getOrElse { "restore error: ${rootThrowable(it).message}" }
                report.appendLine("restorePrevious=$restore")
                Thread.sleep(900L)
                val restoredStatus = runCatching { invokeDeviceQuery(a2dp, "getCodecStatus", device) }.getOrNull()
                val restored = restoredStatus?.let { runCatching { callNoArg(it, "getCodecConfig") }.getOrNull() }
                report.appendLine("restored=${describeConfig(restored)}")
            }
            report.appendLine("INTERPRETATION: if SSC is absent from platform/local capabilities and this request falls back, an APK/Shizuku layer alone cannot supply the missing SSC encoder; native codec work is required.")
        }
        report.toString().trimEnd()
    }.getOrElse { t ->
        val root = rootThrowable(t)
        "ACTIVATE failed: ${root.javaClass.simpleName}: ${root.message}"
    }

    private fun buildSscConfig(uhq: Boolean): Any {
        val codecTypeClass = Class.forName("android.bluetooth.BluetoothCodecType")
        val codecTypeCtor = codecTypeClass.declaredConstructors.firstOrNull { c ->
            val p = c.parameterTypes
            p.size == 3 && isInt(p[0]) && (p[1] == Long::class.javaPrimitiveType || p[1] == Long::class.java) && p[2] == String::class.java
        } ?: error("BluetoothCodecType(int,long,String) constructor unavailable")
        codecTypeCtor.isAccessible = true
        val codecType = codecTypeCtor.newInstance(SSC_LEGACY_TYPE, SSC_CODEC_ID, "SSC")

        val builderClass = Class.forName("android.bluetooth.BluetoothCodecConfig\$Builder")
        val builder = builderClass.getConstructor().newInstance()
        builderClass.getMethod("setExtendedCodecType", codecTypeClass).invoke(builder, codecType)
        invokeBuilderInt(builderClass, builder, "setCodecPriority", 1_000_000)
        invokeBuilderInt(builderClass, builder, "setSampleRate", if (uhq) 0x8 else 0x2)
        invokeBuilderInt(builderClass, builder, "setBitsPerSample", if (uhq) 0x8 else 0x2)
        invokeBuilderInt(builderClass, builder, "setChannelMode", 0x2)
        return builderClass.getMethod("build").invoke(builder)
            ?: error("BluetoothCodecConfig.Builder.build returned null")
    }

    private fun invokeBuilderInt(cls: Class<*>, target: Any, name: String, value: Int) {
        cls.getMethod(name, Int::class.javaPrimitiveType).invoke(target, value)
    }

    private fun invokeSetCodecPreference(target: Any, device: BluetoothDevice, config: Any): String {
        val methods = allMethods(target).filter { it.name == "setCodecConfigPreference" }.sortedBy { it.parameterCount }
        var last: Throwable? = null
        for (m in methods) {
            val args = buildArgs(m, device, config) ?: continue
            try {
                m.isAccessible = true
                val r = m.invoke(target, *args)
                return r?.toString() ?: "void"
            } catch (t: Throwable) {
                last = rootThrowable(t)
            }
        }
        error("setCodecConfigPreference invocation failed${last?.message?.let { ": $it" } ?: ""}")
    }

    private fun invokeDeviceQuery(target: Any, name: String, device: BluetoothDevice): Any? {
        var last: Throwable? = null
        for (m in allMethods(target).filter { it.name == name }.sortedBy { it.parameterCount }) {
            val args = buildArgs(m, device, null) ?: continue
            try {
                m.isAccessible = true
                return m.invoke(target, *args)
            } catch (t: Throwable) {
                last = rootThrowable(t)
            }
        }
        error("$name invocation failed${last?.message?.let { ": $it" } ?: ""}")
    }

    private fun invokeNoDeviceQuery(target: Any, name: String): Any? {
        var last: Throwable? = null
        for (m in allMethods(target).filter { it.name == name }.sortedBy { it.parameterCount }) {
            val args = buildArgs(m, null, null) ?: continue
            try {
                m.isAccessible = true
                return m.invoke(target, *args)
            } catch (t: Throwable) {
                last = rootThrowable(t)
            }
        }
        if (last != null) throw last
        return null
    }

    private fun buildArgs(method: Method, device: BluetoothDevice?, config: Any?): Array<Any?>? {
        val args = arrayOfNulls<Any?>(method.parameterCount)
        method.parameterTypes.forEachIndexed { i, type ->
            args[i] = when {
                BluetoothDevice::class.java.isAssignableFrom(type) -> device ?: return null
                type.name == AttributionSource::class.java.name -> attributionSource
                type.name == "android.bluetooth.BluetoothCodecConfig" -> config ?: return null
                isInt(type) -> 0
                type == Boolean::class.javaPrimitiveType || type == Boolean::class.java -> false
                type == Long::class.javaPrimitiveType || type == Long::class.java -> 0L
                type == String::class.java -> attributionSource.packageName
                !type.isPrimitive -> null
                else -> return null
            }
        }
        return args
    }

    private fun createRuntimeAdapter(): BluetoothAdapter {
        val constructors = BluetoothAdapter::class.java.declaredConstructors.sortedBy { it.parameterCount }
        var last: Throwable? = null
        for (ctor in constructors) {
            val p = ctor.parameterTypes
            if (p.isEmpty() || p[0].name != "android.bluetooth.IBluetoothManager") continue
            val args = arrayOfNulls<Any?>(p.size)
            var valid = true
            p.forEachIndexed { i, type ->
                args[i] = when {
                    i == 0 -> bluetoothManager
                    Context::class.java.isAssignableFrom(type) -> context
                    type.name == AttributionSource::class.java.name -> attributionSource
                    isInt(type) -> 0
                    type == Boolean::class.javaPrimitiveType || type == Boolean::class.java -> false
                    type == Long::class.javaPrimitiveType || type == Long::class.java -> 0L
                    !type.isPrimitive -> null
                    else -> { valid = false; null }
                }
            }
            if (!valid) continue
            try {
                ctor.isAccessible = true
                return ctor.newInstance(*args) as BluetoothAdapter
            } catch (t: Throwable) {
                last = rootThrowable(t)
            }
        }
        throw IllegalStateException("No usable BluetoothAdapter runtime constructor", last)
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

    private fun describeConfig(config: Any?): String {
        if (config == null) return "null"
        val ext = runCatching { callNoArg(config, "getExtendedCodecType") }.getOrNull()
        return buildString {
            append("codec=${ext?.let { runCatching { callNoArg(it, "getCodecName") }.getOrNull() } ?: "?"}")
            append(" id=${configCodecId(config)?.let { "0x${it.toString(16)}" } ?: "?"}")
            append(" legacy=${numberNoArgOrNull(config, "getCodecType") ?: "?"}")
            append(" rate=${numberNoArgOrNull(config, "getSampleRate")?.let { "0x${it.toString(16)}" } ?: "?"}")
            append(" bits=${numberNoArgOrNull(config, "getBitsPerSample")?.let { "0x${it.toString(16)}" } ?: "?"}")
            append(" channel=${numberNoArgOrNull(config, "getChannelMode")?.let { "0x${it.toString(16)}" } ?: "?"}")
            append(" cs1=${numberNoArgOrNull(config, "getCodecSpecific1")?.let { "0x${it.toString(16)}" } ?: "?"}")
            append(" cs2=${numberNoArgOrNull(config, "getCodecSpecific2")?.let { "0x${it.toString(16)}" } ?: "?"}")
            append(" cs3=${numberNoArgOrNull(config, "getCodecSpecific3")?.let { "0x${it.toString(16)}" } ?: "?"}")
            append(" cs4=${numberNoArgOrNull(config, "getCodecSpecific4")?.let { "0x${it.toString(16)}" } ?: "?"}")
        }
    }

    private fun describeCodecType(value: Any?): String {
        if (value == null) return "null"
        val name = runCatching { callNoArg(value, "getCodecName") }.getOrNull()
        val id = codecTypeId(value)
        return "name=${name ?: "?"} id=${id?.let { "0x${it.toString(16)}" } ?: "?"}"
    }

    private fun configCodecId(config: Any?): Long? {
        if (config == null) return null
        val ext = runCatching { callNoArg(config, "getExtendedCodecType") }.getOrNull() ?: return null
        return codecTypeId(ext)
    }

    private fun codecTypeId(value: Any?): Long? {
        if (value == null) return null
        return runCatching { numberNoArg(value, "getCodecId") }.getOrNull()
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

    private fun numberNoArg(target: Any, name: String): Long = (callNoArg(target, name) as Number).toLong()
    private fun numberNoArgOrNull(target: Any?, name: String): Long? = if (target == null) null else runCatching { numberNoArg(target, name) }.getOrNull()
    private fun isInt(type: Class<*>): Boolean = type == Int::class.javaPrimitiveType || type == Int::class.java
    private fun rootThrowable(t: Throwable): Throwable { var cur = t; while (cur.cause != null && cur.cause !== cur) cur = cur.cause!!; return cur }

    override fun destroy() { System.exit(0) }

    companion object {
        private const val SSC_LEGACY_TYPE = 7
        private const val SSC_CODEC_ID = 0x00030075ffL
    }
}
