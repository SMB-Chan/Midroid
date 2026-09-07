package dev.midroid.app.ui

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import dev.midroid.app.R
import dev.midroid.app.config.ReactionScale
import dev.midroid.app.config.TextScale
import dev.midroid.app.power.PowerMode

class SetupScreen(
    private val activity: Activity,
    initialUrl: String,
    initialMode: PowerMode,
    initialTextScale: TextScale,
    initialReactionScale: ReactionScale,
    private val onCopyDiagnostics: () -> Unit,
    instanceEditable: Boolean = true,
    private val onSave: (String, PowerMode, TextScale, ReactionScale) -> String?,
) : ScrollView(activity) {
    private val urlInput = EditText(activity)
    private val modeGroup = RadioGroup(activity)
    private val textScaleGroup = RadioGroup(activity)
    private val reactionScaleGroup = RadioGroup(activity)
    private val errorText = TextView(activity)

    init {
        isFillViewport = true

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(40), dp(24), dp(40))
        }

        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.app_name)
            textSize = 32f
            setTypeface(typeface, Typeface.BOLD)
        }, matchWrap())

        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.setup_intro)
            textSize = 16f
            setPadding(0, dp(8), 0, dp(28))
        }, matchWrap())

        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.misskey_instance)
            setTypeface(typeface, Typeface.BOLD)
        }, matchWrap())

        urlInput.apply {
            hint = activity.getString(R.string.misskey_instance_hint)
            setSingleLine(true)
            setText(initialUrl)
            isEnabled = instanceEditable
        }
        content.addView(urlInput, matchWrap())

        if (!instanceEditable) {
            content.addView(TextView(activity).apply {
                text = activity.getString(R.string.account_instance_managed)
                textSize = 12f
                setPadding(0, dp(6), 0, 0)
            }, matchWrap())
        }

        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.power_mode)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(24), 0, dp(8))
        }, matchWrap())

        PowerMode.entries.forEach { mode ->
            val radio = RadioButton(activity).apply {
                id = View.generateViewId()
                tag = mode.key
                text = powerModeLabel(mode)
                setPadding(0, dp(6), 0, dp(6))
                isChecked = mode == initialMode
            }
            modeGroup.addView(radio, matchWrap())
        }
        content.addView(modeGroup, matchWrap())

        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.text_scale)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(24), 0, dp(8))
        }, matchWrap())

        val densityDpi = activity.resources.displayMetrics.densityDpi
        val screenWidthDp = activity.resources.configuration.screenWidthDp
        TextScale.entries.forEach { scale ->
            val resolved = scale.resolveTextZoom(densityDpi, screenWidthDp)
            val label = textScaleLabel(scale, resolved)
            val radio = RadioButton(activity).apply {
                id = View.generateViewId()
                tag = scale.key
                text = label
                setPadding(0, dp(6), 0, dp(6))
                isChecked = scale == initialTextScale
            }
            textScaleGroup.addView(radio, matchWrap())
        }
        content.addView(textScaleGroup, matchWrap())

        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.reaction_size)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(24), 0, dp(8))
        }, matchWrap())

        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.reaction_size_description)
            textSize = 13f
            setPadding(0, 0, 0, dp(4))
        }, matchWrap())

        ReactionScale.entries.forEach { scale ->
            val radio = RadioButton(activity).apply {
                id = View.generateViewId()
                tag = scale.key
                text = reactionScaleLabel(scale)
                setPadding(0, dp(6), 0, dp(6))
                isChecked = scale == initialReactionScale
            }
            reactionScaleGroup.addView(radio, matchWrap())
        }
        content.addView(reactionScaleGroup, matchWrap())

        errorText.apply {
            visibility = GONE
            setPadding(0, dp(12), 0, 0)
        }
        content.addView(errorText, matchWrap())

        content.addView(Button(activity).apply {
            text = activity.getString(R.string.open_misskey)
            setOnClickListener {
                val error = onSave(
                    urlInput.text.toString(),
                    selectedMode(),
                    selectedTextScale(),
                    selectedReactionScale(),
                )
                if (error == null) {
                    errorText.visibility = GONE
                } else {
                    showError(error)
                }
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(24) })

        content.addView(TextView(activity).apply {
            val provider = WebView.getCurrentWebViewPackage()
            text = if (provider == null) {
                activity.getString(R.string.webview_provider_unavailable)
            } else {
                activity.getString(
                    R.string.webview_provider,
                    provider.packageName,
                    provider.versionName ?: activity.getString(R.string.unknown),
                )
            }
            textSize = 12f
            setPadding(0, dp(20), 0, 0)
        }, matchWrap())

        content.addView(Button(activity).apply {
            text = activity.getString(R.string.copy_diagnostics)
            setOnClickListener { onCopyDiagnostics() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(12) })

        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.privacy_note)
            textSize = 12f
            setPadding(0, dp(8), 0, 0)
        }, matchWrap())

        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun powerModeLabel(mode: PowerMode): String {
        val (titleId, descriptionId) = when (mode) {
            PowerMode.ECO -> R.string.power_eco_title to R.string.power_eco_description
            PowerMode.BALANCED -> R.string.power_balanced_title to R.string.power_balanced_description
            PowerMode.PERFORMANCE -> R.string.power_performance_title to R.string.power_performance_description
        }
        return "${activity.getString(titleId)}\n${activity.getString(descriptionId)}"
    }

    private fun textScaleLabel(scale: TextScale, resolved: Int): String {
        val (titleId, descriptionId) = when (scale) {
            TextScale.AUTO -> R.string.text_scale_auto_title to R.string.text_scale_auto_description
            TextScale.DEFAULT -> R.string.text_scale_100_title to R.string.text_scale_100_description
            TextScale.COMFORTABLE -> R.string.text_scale_115_title to R.string.text_scale_115_description
            TextScale.LARGE -> R.string.text_scale_130_title to R.string.text_scale_130_description
            TextScale.EXTRA_LARGE -> R.string.text_scale_145_title to R.string.text_scale_145_description
        }
        val title = activity.getString(titleId)
        val description = activity.getString(descriptionId)
        return if (scale == TextScale.AUTO) {
            activity.getString(R.string.text_scale_auto_current, title, resolved, description)
        } else {
            "$title\n$description"
        }
    }

    private fun reactionScaleLabel(scale: ReactionScale): String {
        val (titleId, descriptionId) = when (scale) {
            ReactionScale.STANDARD -> R.string.reaction_standard_title to R.string.reaction_standard_description
            ReactionScale.LARGE -> R.string.reaction_large_title to R.string.reaction_large_description
            ReactionScale.EXTRA_LARGE -> R.string.reaction_extra_large_title to R.string.reaction_extra_large_description
        }
        return "${activity.getString(titleId)}\n${activity.getString(descriptionId)}"
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = VISIBLE
    }

    private fun selectedMode(): PowerMode {
        val selected = modeGroup.findViewById<RadioButton>(modeGroup.checkedRadioButtonId)
        return PowerMode.fromKey(selected?.tag as? String)
    }

    private fun selectedTextScale(): TextScale {
        val selected = textScaleGroup.findViewById<RadioButton>(textScaleGroup.checkedRadioButtonId)
        return TextScale.fromKey(selected?.tag as? String)
    }

    private fun selectedReactionScale(): ReactionScale {
        val selected = reactionScaleGroup.findViewById<RadioButton>(reactionScaleGroup.checkedRadioButtonId)
        return ReactionScale.fromKey(selected?.tag as? String)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
