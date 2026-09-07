package dev.midroid.app.power

enum class PowerMode(
    val key: String,
    val title: String,
    val description: String,
) {
    ECO(
        key = "eco",
        title = "Eco",
        description = "60 Hz preference, reduced motion and conservative media behavior.",
    ),
    BALANCED(
        key = "balanced",
        title = "Balanced",
        description = "Full Misskey motion while aggressively suspending background work.",
    ),
    PERFORMANCE(
        key = "performance",
        title = "Performance",
        description = "No foreground frame-rate preference and highest renderer priority.",
    );

    companion object {
        fun fromKey(key: String?): PowerMode = entries.firstOrNull { it.key == key } ?: BALANCED
    }
}
