package jp.example.budsswitch

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        root.addView(TextView(this).apply {
            text = "SSC Runtime Inspector v0.2.8"
            textSize = 25f
        })

        root.addView(TextView(this).apply {
            text = "Galaxy S25 runtimeのBluetooth定数を直接列挙し、SSC-UHQで観測された rate=0x8 / bits=0x8 の意味を調べます。"
            textSize = 14f
            setPadding(0, dp(8), 0, dp(12))
        })

        val runButton = Button(this).apply { text = "Samsung Bluetooth定数を解析" }
        val copyButton = Button(this).apply { text = "結果をクリップボードへコピー" }
        root.addView(runButton)
        root.addView(copyButton)

        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(output)
        setContentView(scroll)

        runButton.setOnClickListener { output.text = inspectRuntime() }
        copyButton.setOnClickListener {
            val text = output.text?.toString().orEmpty()
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("SSC runtime constants", text))
        }

        output.text = inspectRuntime()
    }

    private fun inspectRuntime(): String {
        val value8Matches = mutableListOf<String>()
        val bitDepth8Matches = mutableListOf<String>()
        val sampleRate8Matches = mutableListOf<String>()

        val classes = listOf(
            "android.bluetooth.BluetoothCodecConfig",
            "android.bluetooth.BluetoothCodecType",
            "android.bluetooth.BluetoothA2dp",
            "android.bluetooth.SemBluetoothCodecConfig",
            "android.bluetooth.SemBluetoothCodecType",
            "com.samsung.android.bluetooth.SemBluetoothCodecConfig",
            "com.samsung.android.bluetooth.SemBluetoothCodecType",
            "com.samsung.android.bluetooth.SemBluetoothA2dp"
        )

        return buildString {
            appendLine("=== SSC runtime constant inspection v1 ===")
            appendLine("manufacturer=${Build.MANUFACTURER}")
            appendLine("model=${Build.MODEL}")
            appendLine("device=${Build.DEVICE}")
            appendLine("sdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE}")
            appendLine()
            appendLine("Observed on S25+Buds3 Pro from BudsSwitch:")
            appendLine("  SSC id=0x00030075ff")
            appendLine("  company=0x0075 vendorCodec=0x0003")
            appendLine("  selected rate=0x8 (96kHz)")
            appendLine("  selected bits=0x8 (framework-standard decode unknown)")
            appendLine()

            classes.forEach { className ->
                appendLine("--- $className ---")
                val cls = runCatching { Class.forName(className) }.getOrElse {
                    appendLine("CLASS_NOT_FOUND: ${it.javaClass.simpleName}: ${it.message}")
                    appendLine()
                    return@forEach
                }

                val fields = cls.declaredFields
                    .filter { Modifier.isStatic(it.modifiers) }
                    .filter { isInterestingField(it.name) }
                    .sortedBy { it.name }

                if (fields.isEmpty()) {
                    appendLine("(no matching static fields)")
                }

                fields.forEach { field ->
                    val value = runCatching {
                        field.isAccessible = true
                        field.get(null)
                    }
                    val rendered = value.fold(
                        onSuccess = { renderValue(it) },
                        onFailure = { "<inaccessible:${it.javaClass.simpleName}:${it.message}>" }
                    )
                    appendLine("${field.name} : ${field.type.simpleName} = $rendered")

                    val numeric = value.getOrNull() as? Number
                    if (numeric?.toLong() == 8L) {
                        val qualified = "$className.${field.name}"
                        value8Matches += qualified
                        val upper = field.name.uppercase(Locale.US)
                        if (upper.contains("BITS_PER_SAMPLE") || upper.contains("BIT_DEPTH")) {
                            bitDepth8Matches += qualified
                        }
                        if (upper.contains("SAMPLE_RATE") || upper.contains("SAMPLERATE")) {
                            sampleRate8Matches += qualified
                        }
                    }
                }
                appendLine()
            }

            appendLine("=== VALUE 0x8 MATCHES ===")
            if (value8Matches.isEmpty()) appendLine("none readable")
            else value8Matches.distinct().forEach { appendLine(it) }
            appendLine()

            appendLine("=== INTERPRETATION ===")
            if (sampleRate8Matches.isNotEmpty()) {
                appendLine("rate=0x8 mapping found:")
                sampleRate8Matches.distinct().forEach { appendLine("  $it") }
            } else {
                appendLine("No readable SAMPLE_RATE* field with value 0x8 was found.")
            }

            if (bitDepth8Matches.isNotEmpty()) {
                appendLine("bits=0x8 mapping found:")
                bitDepth8Matches.distinct().forEach { appendLine("  $it") }
            } else {
                appendLine("No readable BITS_PER_SAMPLE*/BIT_DEPTH* field with value 0x8 was found.")
                appendLine("If Samsung still reports selected SSC bits=0x8, that strongly suggests a vendor-private UHQ marker rather than an AOSP public bit-depth constant.")
            }
        }.trimEnd()
    }

    private fun isInterestingField(name: String): Boolean {
        val n = name.uppercase(Locale.US)
        return n.contains("SAMPLE_RATE") ||
            n.contains("SAMPLERATE") ||
            n.contains("BITS_PER_SAMPLE") ||
            n.contains("BIT_DEPTH") ||
            n.contains("CODEC") ||
            n.contains("SSC") ||
            n.contains("UHQ") ||
            n.contains("SEAMLESS") ||
            n.contains("SCALABLE")
    }

    private fun renderValue(value: Any?): String = when (value) {
        null -> "null"
        is Byte -> hexNumber(value.toLong())
        is Short -> hexNumber(value.toLong())
        is Int -> hexNumber(value.toLong())
        is Long -> hexNumber(value)
        is Number -> "$value"
        else -> value.toString()
    }

    private fun hexNumber(value: Long): String =
        "0x${java.lang.Long.toUnsignedString(value, 16)} ($value)"

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
