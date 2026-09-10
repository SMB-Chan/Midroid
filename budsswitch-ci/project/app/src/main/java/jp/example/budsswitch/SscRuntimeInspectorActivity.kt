package jp.example.budsswitch

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import java.util.Locale
import org.lsposed.hiddenapibypass.HiddenApiBypass

class SscRuntimeInspectorActivity : AppCompatActivity() {

    private lateinit var output: TextView
    private lateinit var liveOutput: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        root.addView(TextView(this).apply {
            text = "SSC Runtime Inspector v0.3.0"
            textSize = 25f
        })
        root.addView(TextView(this).apply {
            text = "Samsung SSC-UHQ type 8を、Galaxy Buds実機に対するsemIsCodecSupported/semIsCodecEnabledで直接照会します。状態変更は行いません。"
            textSize = 14f
            setPadding(0, dp(8), 0, dp(12))
        })

        val liveButton = Button(this).apply { text = "Buds3 ProのSSC-UHQ(type=8)を実機照会" }
        root.addView(liveButton)

        liveOutput = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            text = "未実行"
            setTextIsSelectable(true)
            setPadding(0, dp(10), 0, dp(18))
        }
        root.addView(liveOutput)

        val runButton = Button(this).apply { text = "SSC/UHQ API構造を解析" }
        val copyButton = Button(this).apply { text = "全結果をクリップボードへコピー" }
        root.addView(runButton)
        root.addView(copyButton)

        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(output)
        setContentView(scroll)

        liveButton.setOnClickListener { probeUhqState() }
        runButton.setOnClickListener { output.text = inspectRuntime() }
        copyButton.setOnClickListener {
            val all = liveOutput.text.toString() + "\n\n" + output.text.toString()
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("SSC runtime inspection", all))
        }

        output.text = inspectRuntime()
    }

    private fun probeUhqState() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            liveOutput.text = "BLUETOOTH_CONNECT permission is not granted. BudsSwitch本体でAndroid権限を許可してください。"
            return
        }

        val adapter = getSystemService(BluetoothManager::class.java).adapter
        if (adapter == null) {
            liveOutput.text = "BluetoothAdapter unavailable"
            return
        }

        val device = runCatching {
            adapter.bondedDevices.firstOrNull {
                val name = it.name.orEmpty()
                name.contains("Buds", ignoreCase = true)
            }
        }.getOrNull()

        if (device == null) {
            liveOutput.text = "ペアリング済みGalaxy Budsが見つかりません"
            return
        }

        liveOutput.text = "${device.name} に type=8 を照会中…"

        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile != BluetoothProfile.A2DP) return
                try {
                    val supported = invokeHidden(proxy, "semIsCodecSupported", device, 8)
                    val enabled = invokeHidden(proxy, "semIsCodecEnabled", device, 8)
                    val status = invokeHidden(proxy, "getCodecStatus", device)
                    val current = status?.let { invokeNoArg(it, "getCodecConfig") }
                    val rate = current?.let { numberNoArg(it, "getSampleRate") }
                    val bits = current?.let { numberNoArg(it, "getBitsPerSample") }
                    val ext = current?.let { invokeNoArg(it, "getExtendedCodecType") }
                    val codecId = ext?.let { numberNoArg(it, "getCodecId") }
                    val codecName = ext?.let { invokeNoArg(it, "getCodecName")?.toString() }

                    val verdict = when {
                        supported == true && enabled == true -> "CONFIRMED: type=8 is supported AND enabled for this Buds connection"
                        supported == true && enabled == false -> "SUPPORTED but currently disabled"
                        supported == false -> "type=8 reported unsupported for this device"
                        else -> "result could not be reduced to boolean"
                    }

                    liveOutput.text = buildString {
                        appendLine("=== LIVE SSC-UHQ PROBE ===")
                        appendLine("device=${device.name} [${device.address}]")
                        appendLine("SEM_CODEC_TYPE_SSC_UHQ=8")
                        appendLine("semIsCodecSupported(device,8)=$supported")
                        appendLine("semIsCodecEnabled(device,8)=$enabled")
                        appendLine("currentCodec=${codecName ?: "?"}")
                        appendLine("currentCodecId=${codecId?.let { hexNumber(it) } ?: "?"}")
                        appendLine("sampleRate=${rate?.let { hexNumber(it) } ?: "?"}")
                        appendLine("bitsPerSample=${bits?.let { hexNumber(it) } ?: "?"}")
                        appendLine("verdict=$verdict")
                        appendLine("NOTE: read-only probe; semSetCodecEnabled() was NOT called")
                    }.trimEnd()
                } catch (t: Throwable) {
                    liveOutput.text = "LIVE probe failed: ${rootThrowable(t).javaClass.simpleName}: ${rootThrowable(t).message}"
                } finally {
                    runCatching { adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy) }
                }
            }

            override fun onServiceDisconnected(profile: Int) = Unit
        }

        if (!adapter.getProfileProxy(this, listener, BluetoothProfile.A2DP)) {
            liveOutput.text = "BluetoothAdapter.getProfileProxy(A2DP) returned false"
        }
    }

    private fun invokeHidden(target: Any, name: String, vararg args: Any?): Any? {
        val direct = runCatching {
            val candidates = (target.javaClass.methods.asList() + target.javaClass.declaredMethods.asList())
                .filter { it.name == name && it.parameterCount == args.size }
            var last: Throwable? = null
            for (method in candidates) {
                try {
                    method.isAccessible = true
                    return@runCatching method.invoke(target, *args)
                } catch (t: Throwable) {
                    last = t
                }
            }
            if (last != null) throw last
            error("$name/${args.size} not found on ${target.javaClass.name}")
        }
        if (direct.isSuccess) return direct.getOrNull()
        return HiddenApiBypass.invoke(target.javaClass, target, name, *args)
    }

    private fun invokeNoArg(target: Any, name: String): Any? = runCatching {
        val method = (target.javaClass.methods.asList() + target.javaClass.declaredMethods.asList())
            .first { it.name == name && it.parameterCount == 0 }
        method.isAccessible = true
        method.invoke(target)
    }.getOrElse {
        HiddenApiBypass.invoke(target.javaClass, target, name)
    }

    private fun numberNoArg(target: Any, name: String): Long? =
        (invokeNoArg(target, name) as? Number)?.toLong()

    private fun rootThrowable(t: Throwable): Throwable {
        var cur = t
        while (cur.cause != null && cur.cause !== cur) cur = cur.cause!!
        return cur
    }

    private fun inspectRuntime(): String {
        val value8Matches = mutableListOf<String>()
        val sampleRate8Matches = mutableListOf<String>()
        val bitDepth8Matches = mutableListOf<String>()
        val sscUhqFields = mutableListOf<String>()
        val semMethods = mutableListOf<String>()
        val codecMethods = mutableListOf<String>()

        val classes = listOf(
            "android.bluetooth.BluetoothCodecConfig",
            "android.bluetooth.BluetoothCodecStatus",
            "android.bluetooth.BluetoothCodecType",
            "android.bluetooth.BluetoothA2dp",
            "android.bluetooth.IBluetoothA2dp",
            "android.bluetooth.IBluetooth",
            "android.bluetooth.IBluetoothManager",
            "android.bluetooth.SemBluetoothCodecConfig",
            "android.bluetooth.SemBluetoothCodecType",
            "com.samsung.android.bluetooth.SemBluetoothCodecConfig",
            "com.samsung.android.bluetooth.SemBluetoothCodecType",
            "com.samsung.android.bluetooth.SemBluetoothA2dp"
        )

        return buildString {
            appendLine("=== SSC runtime/API inspection v3 ===")
            appendLine("manufacturer=${Build.MANUFACTURER}")
            appendLine("model=${Build.MODEL}")
            appendLine("device=${Build.DEVICE}")
            appendLine("sdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE}")
            appendLine()
            appendLine("Observed on S25 + Buds3 Pro:")
            appendLine("  SSC id=0x00030075ff")
            appendLine("  company=0x0075 vendorCodec=0x0003")
            appendLine("  selected rate=0x8 (96kHz)")
            appendLine("  selected bits=0x8")
            appendLine("  BluetoothA2dp.SEM_CODEC_TYPE_SSC_UHQ=0x8")
            appendLine()

            classes.forEach { className ->
                appendLine("==============================")
                appendLine("CLASS $className")
                val cls = runCatching { Class.forName(className) }.getOrElse {
                    appendLine("CLASS_NOT_FOUND: ${it.javaClass.simpleName}: ${it.message}")
                    appendLine()
                    return@forEach
                }

                appendLine("modifiers=${Modifier.toString(cls.modifiers)}")
                appendLine("super=${cls.superclass?.name ?: "none"}")
                if (cls.interfaces.isNotEmpty()) appendLine("interfaces=${cls.interfaces.joinToString { it.name }}")

                val fields = cls.declaredFields.filter { isInterestingField(it.name, it.type.name) }.sortedBy { it.name }
                appendLine("-- fields (${fields.size}) --")
                if (fields.isEmpty()) appendLine("(none)")
                fields.forEach { field ->
                    val isStatic = Modifier.isStatic(field.modifiers)
                    val valueResult = if (isStatic) runCatching { field.isAccessible = true; field.get(null) } else null
                    val rendered = when {
                        !isStatic -> "<instance>"
                        valueResult == null -> "<unknown>"
                        valueResult.isSuccess -> renderValue(valueResult.getOrNull())
                        else -> "<inaccessible:${valueResult.exceptionOrNull()?.javaClass?.simpleName}>"
                    }
                    val line = "${Modifier.toString(field.modifiers)} ${field.type.name} ${field.name} = $rendered"
                    appendLine(line)

                    val upper = field.name.uppercase(Locale.US)
                    if (upper.contains("SSC") || upper.contains("UHQ") || upper.contains("SEAMLESS") || upper.contains("SCALABLE")) {
                        sscUhqFields += "$className.${field.name}=$rendered"
                    }
                    val numeric = valueResult?.getOrNull() as? Number
                    if (numeric?.toLong() == 8L) {
                        val qualified = "$className.${field.name}"
                        value8Matches += qualified
                        if (upper.contains("SAMPLE_RATE") || upper.contains("SAMPLERATE")) sampleRate8Matches += qualified
                        if (upper.contains("BITS_PER_SAMPLE") || upper.contains("BIT_DEPTH")) bitDepth8Matches += qualified
                    }
                }

                val constructors = cls.declaredConstructors.sortedBy { it.parameterCount }
                appendLine("-- constructors (${constructors.size}) --")
                constructors.forEach { appendLine(formatConstructor(it)) }

                val methods = cls.declaredMethods
                    .filter { isInterestingMethod(it.name, it.parameterTypes.map { t -> t.name }, it.returnType.name) }
                    .sortedWith(compareBy({ it.name }, { it.parameterCount }, { it.toGenericString() }))
                appendLine("-- interesting methods (${methods.size}) --")
                if (methods.isEmpty()) appendLine("(none)")
                methods.forEach { method ->
                    val line = formatMethod(method)
                    appendLine(line)
                    val n = method.name.lowercase(Locale.US)
                    if (n.contains("sem") || n.contains("ssc") || n.contains("uhq") || n.contains("seamless") || n.contains("scalable")) semMethods += "$className :: $line"
                    if (n.contains("codec") || method.parameterTypes.any { it.name.contains("Codec") } || method.returnType.name.contains("Codec")) codecMethods += "$className :: $line"
                }
                appendLine()
            }

            appendLine("=== SSC/UHQ NAMED FIELDS ===")
            if (sscUhqFields.isEmpty()) appendLine("none") else sscUhqFields.distinct().forEach { appendLine(it) }
            appendLine()
            appendLine("=== VALUE 0x8 MATCHES ===")
            if (value8Matches.isEmpty()) appendLine("none readable") else value8Matches.distinct().forEach { appendLine(it) }
            appendLine()
            appendLine("=== SEM/SSC/UHQ METHOD CANDIDATES ===")
            if (semMethods.isEmpty()) appendLine("none") else semMethods.distinct().forEach { appendLine(it) }
            appendLine()
            appendLine("=== CODEC API CANDIDATES ===")
            if (codecMethods.isEmpty()) appendLine("none") else codecMethods.distinct().forEach { appendLine(it) }
            appendLine()
            appendLine("=== INTERPRETATION ===")
            if (sampleRate8Matches.isNotEmpty()) {
                appendLine("rate=0x8 mapping:")
                sampleRate8Matches.distinct().forEach { appendLine("  $it") }
            }
            if (bitDepth8Matches.isNotEmpty()) {
                appendLine("bits=0x8 standard mapping:")
                bitDepth8Matches.distinct().forEach { appendLine("  $it") }
            } else appendLine("No BITS_PER_SAMPLE*/BIT_DEPTH* constant with value 0x8 was found.")
            if (sscUhqFields.any { it.contains("SEM_CODEC_TYPE_SSC_UHQ") && it.contains("0x8") }) {
                appendLine("Samsung runtime explicitly defines SEM_CODEC_TYPE_SSC_UHQ=0x8.")
                appendLine("Use the LIVE probe above to test whether type=8 is supported/enabled for the connected Buds.")
            }
        }.trimEnd()
    }

    private fun isInterestingField(name: String, typeName: String): Boolean {
        val n = name.uppercase(Locale.US)
        return n.contains("SAMPLE_RATE") || n.contains("SAMPLERATE") || n.contains("BITS_PER_SAMPLE") || n.contains("BIT_DEPTH") || n.contains("CODEC") || n.contains("SSC") || n.contains("UHQ") || n.contains("SEAMLESS") || n.contains("SCALABLE") || typeName.contains("Codec")
    }

    private fun isInterestingMethod(name: String, params: List<String>, returnType: String): Boolean {
        val n = name.lowercase(Locale.US)
        return n.contains("codec") || n.contains("sem") || n.contains("ssc") || n.contains("uhq") || n.contains("optional") || n.contains("priority") || n.contains("preference") || params.any { it.contains("BluetoothCodec") } || returnType.contains("BluetoothCodec")
    }

    private fun formatConstructor(member: Member): String = member.toString()

    private fun formatMethod(method: java.lang.reflect.Method): String {
        val mods = Modifier.toString(method.modifiers)
        val params = method.parameterTypes.joinToString(",") { it.simpleName }
        return "$mods ${method.returnType.simpleName} ${method.name}($params)"
    }

    private fun renderValue(value: Any?): String = when (value) {
        null -> "null"
        is Byte -> hexNumber(value.toLong())
        is Short -> hexNumber(value.toLong())
        is Int -> hexNumber(value.toLong())
        is Long -> hexNumber(value)
        is Number -> value.toString()
        else -> value.toString()
    }

    private fun hexNumber(value: Long): String = "0x${java.lang.Long.toUnsignedString(value, 16)} ($value)"
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
