package jp.example.budsswitch.autoswitch

class PriorityEngine(
    private val mediaHoldMs: Long = 1500L,
    private val minimumSwitchGapMs: Long = 5000L
) {
    enum class Reason(val score: Int) {
        CALL(100),
        MEDIA(50)
    }

    private var mediaStartedAt: Long? = null
    private var lastSwitchAt: Long = Long.MIN_VALUE / 2

    fun onMediaChanged(active: Boolean, now: Long): Boolean {
        if (!active) {
            mediaStartedAt = null
            return false
        }
        if (mediaStartedAt == null) {
            mediaStartedAt = now
            return false
        }
        if (now - mediaStartedAt!! < mediaHoldMs) return false
        return canSwitch(now)
    }

    fun onCallActive(now: Long): Boolean = canSwitch(now, force = true)

    fun markSwitched(now: Long) {
        lastSwitchAt = now
    }

    private fun canSwitch(now: Long, force: Boolean = false): Boolean {
        if (force) return true
        return now - lastSwitchAt >= minimumSwitchGapMs
    }
}
