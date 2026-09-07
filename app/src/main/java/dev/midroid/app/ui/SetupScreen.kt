package dev.midroid.app.ui

import android.app.Activity
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import dev.midroid.app.power.PowerMode
import kotlin.math.roundToInt

class SetupScreen(
    activity: Activity,
    initialUrl: String,
    initialMode: PowerMode,
    initialTextScalePercent: Int,
    private val onCopyDiagnostics: () -> Unit,
    private val onSave: (String, PowerMode, Int, TextView) -> Unit,
) : ScrollView(activity) {
    private val urlInput = EditText(activity)
    private val modeGroup = RadioGroup(activity)
    private val errorText = TextView(activity)
    private var textScalePercent = initialTextScalePercent.coerceIn(80, 200)

    init {
        isFillViewport = true

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(40), dp(24), dp(40))
        }

        content.addView(TextView(activity).apply {
            text = "Midroid"
            textSize = 32f
            setTypeface(typeface, Typeface.BOLD)
        }, matchWrap())

        content.addView(TextView(activity).apply {
            text = "A lifecycle-aware Misskey runtime built around Android System WebView."
            textSize = 16f
            setPadding(0, dp(8), 0, dp(28))
        }, matchWrap())

        content.addView(TextView(activity).apply {
            text = "Misskey instance"
            setTypeface(typeface, Typeface.BOLD)
        }, matchWrap())

        urlInput.apply {
            hint = "https://misskey.example"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(initialUrl)
        }
        content.addView(urlInput, matchWrap())

        val textScaleLabel = TextView(activity).apply {
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(24), 0, dp(8))
        }
        val preview = TextView(activity).apply {
            text = "文字の大きさを確認できます。"
        }
        fun updateTextScalePreview() {
            val effectivePercent = (activity.resources.configuration.fontScale * textScalePercent).roundToInt()
            textScaleLabel.text = "文字の大きさ：${effectivePercent}%"
            preview.textSize = 16f * textScalePercent / 100f
        }
        updateTextScalePreview()
        content.addView(textScaleLabel, matchWrap())
        content.addView(TextView(activity).apply {
            text = "端末の文字サイズに連動します。スライダーでさらに調整できます。"
        }, matchWrap())
        content.addView(SeekBar(activity).apply {
            contentDescription = "文字の大きさ"
            max = 24
            progress = (textScalePercent - 80) / 5
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    textScalePercent = 80 + progress * 5
                    updateTextScalePreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, matchWrap())
        content.addView(preview, matchWrap())

        content.addView(TextView(activity).apply {
            text = "Power mode"
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(24), 0, dp(8))
        }, matchWrap())

        PowerMode.entries.forEach { mode ->
            val radio = RadioButton(activity).apply {
                id = View.generateViewId()
                tag = mode.key
                text = "${mode.title}\n${mode.description}"
                setPadding(0, dp(6), 0, dp(6))
                isChecked = mode == initialMode
            }
            modeGroup.addView(radio, matchWrap())
        }
        content.addView(modeGroup, matchWrap())

        errorText.apply {
            visibility = GONE
            setPadding(0, dp(12), 0, 0)
        }
        content.addView(errorText, matchWrap())

        content.addView(Button(activity).apply {
            text = "Open Misskey"
            setOnClickListener {
                errorText.visibility = GONE
                onSave(urlInput.text.toString(), selectedMode(), textScalePercent, errorText)
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(24) })

        content.addView(TextView(activity).apply {
            val provider = WebView.getCurrentWebViewPackage()
            val providerText = if (provider == null) {
                "WebView provider: unavailable"
            } else {
                "WebView provider: ${provider.packageName} ${provider.versionName ?: "unknown"}"
            }
            text = providerText
            textSize = 12f
            setPadding(0, dp(20), 0, 0)
        }, matchWrap())

        content.addView(Button(activity).apply {
            text = "Copy diagnostics"
            setOnClickListener { onCopyDiagnostics() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(12) })

        content.addView(TextView(activity).apply {
            text = "Midroid stores the selected instance and login cookies locally. HTTPS is required. External links open in your default browser. Diagnostic copies contain only the instance origin, device/WebView version, power mode and Android power-saver state."
            textSize = 12f
            setPadding(0, dp(8), 0, 0)
        }, matchWrap())

        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun showError(message: String) {
        errorText.text = message
        errorText.visibility = VISIBLE
    }

    private fun selectedMode(): PowerMode {
        val selected = modeGroup.findViewById<RadioButton>(modeGroup.checkedRadioButtonId)
        return PowerMode.fromKey(selected?.tag as? String)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
