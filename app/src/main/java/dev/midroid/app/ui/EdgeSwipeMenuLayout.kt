package dev.midroid.app.ui

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Full-screen browser container that observes a deliberate inward swipe near the right edge
 * without reserving a permanent overlay button above the web content.
 *
 * Events continue to the child WebView. The menu gesture therefore only adds behaviour after a
 * sufficiently horizontal swipe and does not create a persistent touch-blocking surface.
 */
class EdgeSwipeMenuLayout(context: Context) : FrameLayout(context) {
    var onMenuSwipe: (() -> Unit)? = null

    private val edgeWidthPx = dp(72)
    private val triggerDistancePx = dp(48)
    private val verticalTolerancePx = dp(64)

    private var tracking = false
    private var triggered = false
    private var downX = 0f
    private var downY = 0f

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                triggered = false
                tracking = width > 0 && downX >= width - edgeWidthPx
            }

            MotionEvent.ACTION_MOVE -> {
                if (tracking && !triggered) {
                    val inward = downX - event.x
                    val vertical = abs(event.y - downY)
                    if (inward >= triggerDistancePx && vertical <= verticalTolerancePx && inward > vertical) {
                        triggered = true
                        tracking = false
                        onMenuSwipe?.invoke()
                    } else if (vertical > verticalTolerancePx * 2) {
                        tracking = false
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                triggered = false
            }
        }

        return super.dispatchTouchEvent(event)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
