package jp.example.budsswitch.privileged

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.AttributionSource
import android.content.Context
import android.os.IBinder
import android.os.Process
import androidx.annotation.Keep
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import rikka.shizuku.SystemServiceHelper

@Keep
class PrivilegedBluetoothService(private val context: Context) :
    IBluetoothPrivilegedService.Stub() {

    @Volatile private var adapterBootstrap = "not-initialized"
    @Volatile private var profileBackend = "not-initialized"

    private val attributionSource: AttributionSource by lazy {
        val uid = Process.myUid()
        AttributionSource.Builder(if (uid == 0) 1000 else uid)
            .setPid(Process.myPid())
            .setPackageName(attributionPackageForUid(uid))
            .build()
    }

    private val bluetoothManager: Any by lazy {
        val binder = SystemServiceHelper.getSystemService("bluetooth_manager")
            ?: error("ServiceManager bluetooth_manager returned null")
        val stub = Class.forName("android.bluetooth.IBluetoothManager\$Stub")
        stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("IBluetoothManager.Stub.asInterface returned null")
    }

    private val adapter: BluetoothAdapter by lazy { createPrivilegedAdapter() }

    override fun ping(): String = buildString {
        append("ok uid=${Process.myUid()} pid=${Process.myPid()} api=${android.os.Build.VERSION.SDK_INT}")
        append(" attribution=${attributionPackageForUid(Process.myUid())}")
        runCatching { adapter }.onFailure { adapterBootstrap = "FAILED:${rootMessageOnly(it)}" }
        append(" adapter=$adapterBootstrap")
        append(" profile=$profileBackend")
    }

    override fun connectDevice(address: String): String = runCatching {
        validate(address)
        val device = remoteDevice(address)
        val allProfiles = invokeDeviceInt(device, "connect")
        if (allProfiles == 0) {
            return@runCatching "all-profiles=success; adapter=$adapterBootstrap"
        }

        val a2dp = invokeProfileAction(BluetoothProfile.A2DP, device, "connect")
        val headset = invokeProfileAction(BluetoothProfile.HEADSET, device, "connect")
        val primary = if (allProfiles == Int.MIN_VALUE) "all-profiles API unavailable"
        else "all-profiles result=$allProfiles"
        "$primary; fallback A2DP=$a2dp HFP=$headset; adapter=$adapterBootstrap; profile=$profileBackend"
    }.getOrElse { rootMessage("connect failed", it) }

    override fun disconnectDevice(address: String): String = runCatching {
        validate(address)
        val device = remoteDevice(address)
        val allProfiles = invokeDeviceInt(device, "disconnect")
        if (allProfiles == 0) {
            return@runCatching "all-profiles=success; adapter=$adapterBootstrap"
        }

        val headset = invokeProfileAction(BluetoothProfile.HEADSET, device, "disconnect")
        val a2dp = invokeProfileAction(BluetoothProfile.A2DP, device, "disconnect")
        val primary = if (allProfiles == Int.MIN_VALUE) "all-profiles API unavailable"
        else "all-profiles result=$allProfiles"
        "$primary; fallback HFP=$headset A2DP=$a2dp; adapter=$adapterBootstrap; profile=$profileBackend"
    }.getOrElse { rootMessage("disconnect failed", it) }

    override fun connectionSummary(address: String): String = runCatching {
        validate(address)
        val device = remoteDevice(address)
        val a2dp = queryConnectionState(BluetoothProfile.A2DP, device)
        val headset = queryConnectionState(BluetoothProfile.HEADSET, device)
        "A2DP=${stateName(a2dp)} HFP=${stateName(headset)} backend=$profileBackend"
    }.getOrElse { rootMessage("status failed", it) }

    override fun codecDiagnostics(address: String): String = runCatching {
        validate(address)
        val device = remoteDevice(address)

        // Android 16 / Samsung path: bypass BluetoothAdapter.getProfileProxy(),
        // whose BluetoothServiceManager can be null inside Shizuku app_process.
        val direct = runCatching { directProfileService(BluetoothProfile.A2DP) }.getOrNull()
        if (direct != null) {
            profileBackend = "direct-IBluetooth.getProfile"
            return@runCatching buildCodecDiagnostics(
                address = address,
                device = device,
                service = direct,
                backend = profileBackend
            )
        }

        // Android 15 / Motorola compatibility path.
        val proxy = acquireLegacyProfile(BluetoothProfile.A2DP)
            ?: return@runCatching "A2DP service unavailable / adapter=$adapterBootstrap / profile=$profileBackend"
        try {
            profileBackend = "BluetoothAdapter.getProfileProxy"
            buildCodecDiagnostics(address, device, proxy, profileBackend)
        } finally {
            closeLegacyProfile(proxy)
        }
    }.getOrElse { rootMessage("codec diagnostics failed", it) }

    private fun buildCodecDiagnostics(
        address: String,
        device: BluetoothDevice,
        service: Any,
        backend: String
    ): String {
        val status = invokeCompatible(service, "getCodecStatus", device)
            ?: return "A2DP codec status unavailable (connect Buds first) / backend=$backend"

        val current = callNoArg(status, "getCodecConfig")
        val local = callNoArg(status, "getCodecsLocalCapabilities") as? List<*> ?: emptyList<Any>()
        val selectable = callNoArg(status, "getCodecsSelectableCapabilities") as? List<*>
            ?: emptyList<Any>()
        val supported = (invokeCompatible(service, "getSupportedCodecTypes", null) as? Collection<*>)
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

        return buildString {
            appendLine("=== A2DP codec diagnostics v4 ===")
            appendLine("device=$address")
            appendLine("adapterBootstrap=$adapterBootstrap")
            appendLine("backend=$backend")
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
            if (samsungTypes.isEmpty()) appendLine("none")
            else samsungTypes.forEach { (_, type) -> appendLine(formatCodecType(type)) }
        }.trimEnd()
    }

    /**
     * Android 16 obtains profile binders from IBluetooth.getProfile().  We call
     * that chain directly instead of BluetoothAdapter.getProfileProxy(), which
     * depends on BluetoothFrameworkInitializer/BluetoothServiceManager and is
     * not initialized in Samsung's Shizuku app_process environment.
     */
    private fun directProfileService(profile: Int): Any? {
        val core = directBluetoothCoreService() ?: return null
        val getProfile = methodsNamed(core, "getProfile")
            .firstOrNull { m -> m.parameterTypes.size == 1 && isIntType(m.parameterTypes[0]) }
            ?: return null
        getProfile.isAccessible = true
        val raw = getProfile.invoke(core, profile) ?: return null
        val binder = asBinder(raw) ?: return null
        val stubName = when (profile) {
            BluetoothProfile.A2DP -> "android.bluetooth.IBluetoothA2dp\$Stub"
            BluetoothProfile.HEADSET -> "android.bluetooth.IBluetoothHeadset\$Stub"
            else -> return null
        }
        val stub = Class.forName(stubName)
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
    }

    /** Return the process's already-registered IBluetooth service, or register it directly. */
    private fun directBluetoothCoreService(): Any? {
        // A runtime-constructed adapter often has mService initialized even if
        // its Samsung BluetoothServiceManager field is null. Prefer that object.
        runCatching {
            findFieldValue(adapter, "android.bluetooth.IBluetooth")
        }.getOrNull()?.let { return it }

        val callback = findManagerCallback()
            ?: error("IBluetoothManagerCallback field not found in BluetoothAdapter runtime")
        val register = methodsNamed(bluetoothManager, "registerAdapter")
            .firstOrNull { it.parameterTypes.size == 1 }
            ?: error("IBluetoothManager.registerAdapter method not found")
        register.isAccessible = true
        val raw = register.invoke(bluetoothManager, callback) ?: return null
        val binder = asBinder(raw) ?: error("registerAdapter result has no Binder")
        val stub = Class.forName("android.bluetooth.IBluetooth\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
    }

    private fun findManagerCallback(): Any? {
        val expected = "android.bluetooth.IBluetoothManagerCallback"

        fun inspect(instance: Any?, cls: Class<*>): Any? {
            var current: Class<*>? = cls
            while (current != null) {
                current.declaredFields.forEach { field ->
                    if (field.type.name != expected) return@forEach
                    runCatching {
                        field.isAccessible = true
                        field.get(if (Modifier.isStatic(field.modifiers)) null else instance)
                    }.getOrNull()?.let { return it }
                }
                current = current.superclass
            }
            return null
        }

        inspect(adapter, BluetoothAdapter::class.java)?.let { return it }
        return inspect(adapter, adapter.javaClass)
    }

    private fun findFieldValue(instance: Any, typeName: String): Any? {
        var cls: Class<*>? = instance.javaClass
        while (cls != null) {
            cls.declaredFields.forEach { field ->
                if (field.type.name != typeName) return@forEach
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
        else -> runCatching {
            value.javaClass.getMethod("asBinder").invoke(value) as? IBinder
        }.getOrNull()
    }

    private fun invokeProfileAction(profile: Int, device: BluetoothDevice, methodName: String): String {
        runCatching {
            val direct = directProfileService(profile) ?: error("direct profile unavailable")
            val result = invokeCompatible(direct, methodName, device)
            profileBackend = "direct-IBluetooth.getProfile"
            return "direct:${result ?: "null"}"
        }.onFailure {
            // Keep the reason only when fallback also fails.
        }

        val proxy = acquireLegacyProfile(profile) ?: return "proxy-unavailable"
        return try {
            profileBackend = "BluetoothAdapter.getProfileProxy"
            val method = findDeviceMethod(proxy, methodName)
                ?: return "method-unavailable"
            method.isAccessible = true
            method.invoke(proxy, device)?.toString() ?: "null"
        } catch (t: Throwable) {
            rootMessage("error", t)
        } finally {
            closeLegacyProfile(proxy)
        }
    }

    private fun queryConnectionState(profile: Int, device: BluetoothDevice): Int {
        runCatching {
            val direct = directProfileService(profile) ?: error("direct profile unavailable")
            val result = invokeCompatible(direct, "getConnectionState", device) as? Number
                ?: error("direct state unavailable")
            profileBackend = "direct-IBluetooth.getProfile"
            return result.toInt()
        }

        val proxy = acquireLegacyProfile(profile) ?: return BluetoothProfile.STATE_DISCONNECTED
        return try {
            profileBackend = "BluetoothAdapter.getProfileProxy"
            proxy.getConnectionState(device)
        } finally {
            closeLegacyProfile(proxy)
        }
    }

    private fun invokeCompatible(target: Any, methodName: String, device: BluetoothDevice?): Any? {
        val failures = mutableListOf<String>()
        val candidates = methodsNamed(target, methodName)
            .sortedBy { it.parameterTypes.size }
        for (method in candidates) {
            val args = mutableListOf<Any?>()
            var compatible = true
            for (type in method.parameterTypes) {
                when {
                    BluetoothDevice::class.java.isAssignableFrom(type) -> {
                        if (device == null) compatible = false else args += device
                    }
                    type.name == AttributionSource::class.java.name -> args += attributionSource
                    type == Boolean::class.javaPrimitiveType || type == Boolean::class.java -> args += false
                    isIntType(type) -> args += 0
                    !type.isPrimitive -> args += null
                    else -> compatible = false
                }
            }
            if (!compatible) continue
            try {
                method.isAccessible = true
                return method.invoke(target, *args.toTypedArray())
            } catch (t: Throwable) {
                failures += "${method.parameterTypes.joinToString(",") { it.simpleName }}:${shortThrowable(t)}"
            }
        }
        if (candidates.isEmpty()) error("$methodName not found on ${target.javaClass.name}")
        error("$methodName invocation failed: ${failures.joinToString(" | ")}")
    }

    private fun methodsNamed(target: Any, name: String): List<Method> {
        val out = LinkedHashMap<String, Method>()
        fun add(cls: Class<*>?) {
            if (cls == null) return
            cls.declaredMethods.filter { it.name == name }.forEach { m -> out[m.toGenericString()] = m }
            cls.interfaces.forEach { add(it) }
            add(cls.superclass)
        }
        add(target.javaClass)
        return out.values.toList()
    }

    private fun findDeviceMethod(target: Any, name: String): Method? =
        methodsNamed(target, name).firstOrNull { method ->
            method.parameterTypes.size == 1 &&
                BluetoothDevice::class.java.isAssignableFrom(method.parameterTypes[0])
        }

    private fun remoteDevice(address: String): BluetoothDevice {
        runCatching { return adapter.getRemoteDevice(address) }

        val constructors = BluetoothDevice::class.java.declaredConstructors
            .filter { it.parameterTypes.isNotEmpty() && it.parameterTypes[0] == String::class.java }
            .sortedBy { it.parameterCount }
        for (ctor in constructors) {
            val args = Array<Any?>(ctor.parameterCount) { index ->
                val type = ctor.parameterTypes[index]
                when {
                    index == 0 -> address
                    isIntType(type) -> 0
                    type.name == AttributionSource::class.java.name -> attributionSource
                    !type.isPrimitive -> null
                    else -> 0
                }
            }
            val result = runCatching {
                ctor.isAccessible = true
                ctor.newInstance(*args) as BluetoothDevice
            }.getOrNull() ?: continue
            runCatching {
                val setter = result.javaClass.getDeclaredMethod("setAttributionSource", AttributionSource::class.java)
                setter.isAccessible = true
                setter.invoke(result, attributionSource)
            }
            return result
        }
        error("Unable to construct BluetoothDevice for $address")
    }

    private fun acquireLegacyProfile(profile: Int): BluetoothProfile? {
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
        return try {
            if (!adapter.getProfileProxy(context, listener, profile)) return null
            latch.await(2500, TimeUnit.MILLISECONDS)
            result
        } catch (_: Throwable) {
            null
        }
    }

    private fun closeLegacyProfile(proxy: BluetoothProfile) {
        runCatching {
            val method = proxy.javaClass.methods.firstOrNull { it.name == "close" && it.parameterCount == 0 }
                ?: proxy.javaClass.declaredMethods.firstOrNull { it.name == "close" && it.parameterCount == 0 }
            if (method != null) {
                method.isAccessible = true
                method.invoke(proxy)
            } else {
                adapter.closeProfileProxy(0, proxy)
            }
        }
    }

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
                Locale.US, companyId, vendorCodecId
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
    private fun codecIdHex(id: Long): String = String.format(Locale.US, "0x%010x", normalizeCodecId(id))

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
        listOf(0x1 to "44.1k", 0x2 to "48k", 0x4 to "88.2k", 0x8 to "96k", 0x10 to "176.4k", 0x20 to "192k")
    )
    private fun decodeBits(mask: Int): String = decodeMask(mask, listOf(0x1 to "16", 0x2 to "24", 0x4 to "32"))
    private fun decodeChannel(mask: Int): String = decodeMask(mask, listOf(0x1 to "MONO", 0x2 to "STEREO"))

    private fun decodeMask(mask: Int, values: List<Pair<Int, String>>): String {
        if (mask == 0) return "none"
        val labels = values.filter { (bit, _) -> mask and bit != 0 }.map { it.second }
        return if (labels.isEmpty()) "unknown" else labels.joinToString("|")
    }

    private fun callNoArg(target: Any, methodName: String): Any? = runCatching {
        val method = methodsNamed(target, methodName).firstOrNull { it.parameterCount == 0 }
            ?: return@runCatching null
        method.isAccessible = true
        method.invoke(target)
    }.getOrNull()

    private fun createPrivilegedAdapter(): BluetoothAdapter {
        val failures = mutableListOf<String>()

        runCatching {
            val m = BluetoothAdapter::class.java.getDeclaredMethod(
                "createAdapter", AttributionSource::class.java
            )
            m.isAccessible = true
            (m.invoke(null, attributionSource) as? BluetoothAdapter)
                ?: error("createAdapter returned null")
        }.onSuccess {
            adapterBootstrap = "static-createAdapter"
            return it
        }.onFailure { failures += "createAdapter:${shortThrowable(it)}" }

        runCatching {
            BluetoothAdapter.getDefaultAdapter() ?: error("getDefaultAdapter returned null")
        }.onSuccess {
            adapterBootstrap = "getDefaultAdapter"
            return it
        }.onFailure { failures += "getDefaultAdapter:${shortThrowable(it)}" }

        val binder: IBinder = SystemServiceHelper.getSystemService("bluetooth_manager")
            ?: error("ServiceManager bluetooth_manager returned null; ${failures.joinToString(" | ")}")
        val managerInterface = Class.forName("android.bluetooth.IBluetoothManager")
        val constructors = BluetoothAdapter::class.java.declaredConstructors
            .sortedWith(compareBy<Constructor<*>> { it.parameterCount }.thenBy { it.toString() })

        for (constructor in constructors) {
            val params = constructor.parameterTypes
            if (params.none { it.isInstance(bluetoothManager) || it.name == managerInterface.name }) continue

            val args = Array<Any?>(params.size) { index ->
                constructorArgument(params[index], bluetoothManager, managerInterface, binder)
            }
            runCatching {
                constructor.isAccessible = true
                constructor.newInstance(*args) as BluetoothAdapter
            }.onSuccess {
                adapterBootstrap = "runtime-ctor(${params.joinToString(",") { it.simpleName }})"
                return it
            }.onFailure {
                failures += "ctor(${params.joinToString(",") { it.simpleName }}):${shortThrowable(it)}"
            }
        }

        val inventory = constructors.joinToString(" ; ") { c ->
            "(${c.parameterTypes.joinToString(",") { it.name }})"
        }
        error(
            "No compatible BluetoothAdapter bootstrap. failures=${failures.joinToString(" | ")} constructors=$inventory"
        )
    }

    private fun constructorArgument(
        type: Class<*>,
        manager: Any,
        managerInterface: Class<*>,
        binder: IBinder
    ): Any? = when {
        type.isInstance(manager) || type.name == managerInterface.name -> manager
        type == AttributionSource::class.java -> attributionSource
        Context::class.java.isAssignableFrom(type) -> context
        IBinder::class.java.isAssignableFrom(type) -> binder
        type == String::class.java -> attributionPackageForUid(Process.myUid())
        type == Boolean::class.javaPrimitiveType || type == Boolean::class.java -> false
        isIntType(type) -> 0
        type == Long::class.javaPrimitiveType || type == Long::class.java -> 0L
        type == Short::class.javaPrimitiveType || type == Short::class.java -> 0.toShort()
        type == Byte::class.javaPrimitiveType || type == Byte::class.java -> 0.toByte()
        type == Char::class.javaPrimitiveType || type == Char::class.java -> '\u0000'
        type == Float::class.javaPrimitiveType || type == Float::class.java -> 0f
        type == Double::class.javaPrimitiveType || type == Double::class.java -> 0.0
        else -> null
    }

    private fun invokeDeviceInt(device: BluetoothDevice, methodName: String): Int = try {
        val method = BluetoothDevice::class.java.getDeclaredMethod(methodName)
        method.isAccessible = true
        (method.invoke(device) as? Number)?.toInt() ?: Int.MIN_VALUE
    } catch (_: Throwable) {
        Int.MIN_VALUE
    }

    private fun validate(address: String) {
        require(BluetoothAdapter.checkBluetoothAddress(address)) { "invalid Bluetooth address" }
    }

    private fun isIntType(type: Class<*>): Boolean =
        type == Int::class.javaPrimitiveType || type == Int::class.java

    private fun attributionPackageForUid(uid: Int): String = when (uid) {
        0, 1000 -> "android"
        2000 -> "com.android.shell"
        else -> context.packageName
    }

    private fun stateName(state: Int): String = when (state) {
        BluetoothProfile.STATE_CONNECTED -> "connected"
        BluetoothProfile.STATE_CONNECTING -> "connecting"
        BluetoothProfile.STATE_DISCONNECTING -> "disconnecting"
        else -> "disconnected"
    }

    private fun shortThrowable(t: Throwable): String {
        var x = t
        if (x is InvocationTargetException && x.targetException != null) x = x.targetException
        return "${x.javaClass.simpleName}:${x.message ?: "(no message)"}"
    }

    private fun rootMessageOnly(t: Throwable): String = shortThrowable(t)

    private fun rootMessage(prefix: String, t: Throwable): String =
        "$prefix: ${shortThrowable(t)}"

    override fun destroy() { System.exit(0) }
}
