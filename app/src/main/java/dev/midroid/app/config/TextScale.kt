package dev.midroid.app.config

enum class TextScale(
    val key: String,
    val title: String,
    val description: String,
    private val fixedTextZoom: Int? = null,
) {
    AUTO(
        key = "auto",
        title = "Auto",
        description = "Adapts to screen density and width.",
    ),
    DEFAULT(
        key = "100",
        title = "100%",
        description = "Use the site default text size.",
        fixedTextZoom = 100,
    ),
    COMFORTABLE(
        key = "115",
        title = "115%",
        description = "Slightly larger text for everyday use.",
        fixedTextZoom = 115,
    ),
    LARGE(
        key = "130",
        title = "130%",
        description = "Larger text for easier reading.",
        fixedTextZoom = 130,
    ),
    EXTRA_LARGE(
        key = "145",
        title = "145%",
        description = "Maximum built-in text enlargement.",
        fixedTextZoom = 145,
    );

    fun resolveTextZoom(densityDpi: Int, screenWidthDp: Int): Int {
        return fixedTextZoom ?: autoTextZoom(densityDpi, screenWidthDp)
    }

    companion object {
        fun fromKey(key: String?): TextScale {
            return entries.firstOrNull { it.key == key } ?: AUTO
        }

        fun autoTextZoom(densityDpi: Int, screenWidthDp: Int): Int {
            val densityBoost = when {
                densityDpi >= 560 -> 16
                densityDpi >= 480 -> 13
                densityDpi >= 420 -> 10
                densityDpi >= 360 -> 7
                else -> 0
            }
            val widthBoost = when (screenWidthDp) {
                in 1..360 -> 10
                in 361..411 -> 7
                in 412..480 -> 4
                else -> 0
            }
            return (100 + densityBoost + widthBoost).coerceIn(100, 130)
        }
    }
}
