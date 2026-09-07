package dev.midroid.app.config

enum class ReactionScale(
    val key: String,
    val title: String,
    val description: String,
) {
    STANDARD(
        key = "standard",
        title = "Standard",
        description = "Comfortable 52px reaction controls.",
    ),
    LARGE(
        key = "large",
        title = "Large",
        description = "Larger 60px reactions for easier tapping and recognition.",
    ),
    EXTRA_LARGE(
        key = "extra_large",
        title = "Extra large",
        description = "Maximum 68px reactions for high visibility.",
    );

    companion object {
        fun fromKey(key: String?): ReactionScale {
            return entries.firstOrNull { it.key == key } ?: LARGE
        }
    }
}
