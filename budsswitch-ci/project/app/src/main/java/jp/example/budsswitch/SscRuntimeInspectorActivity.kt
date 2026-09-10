package jp.example.budsswitch

import android.content.ClipData
import android.content.ClipboardManager
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

class SscRuntimeInspectorActivity : AppCompatActivity() {

    private lateinit var output: TextView

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
            text = "SSC Runtime Inspector v0.2.9"
            textSize = 25f
        })
        root.addView(TextView(this).apply {
            text = "Samsung frameworkのSSC/UHQ定数に加えて、関連method/field/constructorを列挙し、SEM_CODEC_TYPE_SSC_UHQ=0x8がどのAPI層に接続されるかを絞ります。"
            textSize = 14f
            setPadding(0, dp(8), 0, dp(12))
        })

        val runButton = Button(this).apply { text = "SSC/UHQ API構造を解析" }
        val copyButton = Button(this).apply { text = "結果をクリップボードへコピー" }
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

        runButton.setOnClickListener { output.text = inspectRuntime() }
        copyButton.setOnClickListener {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("SSC runtime inspection", output.text?.toString().orEmpty()))
        }

        output.text = inspectRuntime()
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
            appendLine("=== SSC runtime/API inspection v2 ===")
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
                if (cls.interfaces.isNotEmpty()) {
                    appendLine("interfaces=${cls.interfaces.joinToString { it.name }}")
                }

                val fields = cls.declaredFields
                    .filter { isInterestingField(it.name, it.type.name) }
                    .sortedBy { it.name }
                appendLine("-- fields (${fields.size}) --")
                if (fields.isEmpty()) appendLine("(none)")
                fields.forEach { field ->
                    val isStatic = Modifier.isStatic(field.modifiers)
                    val valueResult = if (isStatic) runCatching {
                        field.isAccessible = true
                        field.get(null)
                    } else null
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
                    if (n.contains("sem") || n.contains("ssc") || n.contains("uhq") || n.contains("seamless") || n.contains("scalable")) {
                        semMethods += "$className :: $line"
                    }
                    if (n.contains("codec") || method.parameterTypes.any { it.name.contains("Codec") } || method.returnType.name.contains("Codec")) {
                        codecMethods += "$className :: $line"
                    }
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
            } else {
                appendLine("No BITS_PER_SAMPLE*/BIT_DEPTH* constant with value 0x8 was found.")
            }
            if (sscUhqFields.any { it.contains("SEM_CODEC_TYPE_SSC_UHQ") && it.contains("0x8") }) {
                appendLine("Samsung runtime explicitly defines SEM_CODEC_TYPE_SSC_UHQ=0x8.")
                appendLine("The remaining question is whether selected bits=0x8 is the same marker reused in BluetoothCodecConfig or merely a numerically equal Samsung mode flag.")
            }
        }.trimEnd()
    }

    private fun isInterestingField(name: String, typeName: String): Boolean {
        val n = name.uppercase(Locale.US)
        return n.contains("SAMPLE_RATE") || n.contains("SAMPLERATE") ||
            n.contains("BITS_PER_SAMPLE") || n.contains("BIT_DEPTH") ||
            n.contains("CODEC") || n.contains("SSC") || n.contains("UHQ") ||
            n.contains("SEAMLESS") || n.contains("SCALABLE") ||
            typeName.contains("Codec")
    }

    private fun isInterestingMethod(name: String, params: List<String>, returnType: String): Boolean {
        val n = name.lowercase(Locale.US)
        return n.contains("codec") || n.contains("sem") || n.contains("ssc") || n.contains("uhq") ||
            n.contains("optional") || n.contains("priority") || n.contains("preference") ||
            params.any { it.contains("BluetoothCodec") } || returnType.contains("BluetoothCodec")
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
