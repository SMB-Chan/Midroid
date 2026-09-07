package dev.midroid.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Browser container with a small movable side handle for Midroid controls.
 *
 * Starting a gesture at the physical screen edge conflicts with Android's back gesture, so the
 * handle intentionally sits inward from the edge. It can be tapped or dragged inward to open the
 * Midroid menu, and dragged vertically to a comfortable position. Only the small handle touch
 * target overlays the WebView.
 */
class EdgeSwipeMenuLayout(context: Context) : FrameLayout(context) {
    var onMenuSwipe: (() -> Unit)? = null

    private val preferences = context.getSharedPreferences("midroid_side_handle", Context.MODE_PRIVATE)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val edgeInsetPx = dp(22)
    private val touchWidthPx = dp(34)
    private val touchHeightPx = dp(84)
    private val triggerDistancePx = dp(18)
    private val verticalMarginPx = dp(8)

    private var positionInitialized = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var startTopMargin = 0
    private var draggingVertically = false
    private var menuTriggered = false

    private val handle = FrameLayout(context).apply {
        isClickable = true
        isFocusable = true
        contentDescription = "Midroid menu"
        alpha = 0.82f

        addView(
            View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(0xA8D7DBE2.toInt())
                    setStroke(dp(1), 0x55FFFFFF)
                }
            },
            LayoutParams(dp(5), dp(56), Gravity.CENTER),
        )

        setOnTouchListener { _, event -> handleTouch(event) }
    }

    init {
        clipChildren = false
        clipToPadding = false
        addView(
            handle,
            LayoutParams(touchWidthPx, touchHeightPx, Gravity.TOP or Gravity.END).apply {
                marginEnd = edgeInsetPx
                topMargin = dp(128)
            },
        )
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (child !== handle) handle.bringToFront()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!positionInitialized && h > touchHeightPx) {
            val ratio = preferences.getFloat("vertical_ratio", 0.32f).coerceIn(0.05f, 0.95f)
            val available = (h - touchHeightPx).coerceAtLeast(1)
            setHandleTop((available * ratio).toInt())
            positionInitialized = true
        } else if (positionInitialized) {
            setHandleTop(currentTopMargin())
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downRawX = event.rawX
                downRawY = event.rawY
                startTopMargin = currentTopMargin()
                draggingVertically = false
                menuTriggered = false
                handle.alpha = 1f
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val inward = downRawX - event.rawX
                val vertical = event.rawY - downRawY

                if (!draggingVertically && abs(vertical) > touchSlop && abs(vertical) > abs(inward)) {
                    draggingVertically = true
                }

                if (draggingVertically) {
                    setHandleTop(startTopMargin + vertical.toInt())
                } else if (!menuTriggered && inward >= triggerDistancePx) {
                    menuTriggered = true
                    onMenuSwipe?.invoke()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (draggingVertically) {
                    persistHandlePosition()
                } else if (!menuTriggered) {
                    onMenuSwipe?.invoke()
                }
                finishTouch()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (draggingVertically) persistHandlePosition()
                finishTouch()
                return true
            }
        }

        return true
    }

    private fun finishTouch() {
        parent?.requestDisallowInterceptTouchEvent(false)
        draggingVertically = false
        menuTriggered = false
        handle.alpha = 0.82f
    }

    private fun currentTopMargin(): Int {
        return (handle.layoutParams as? LayoutParams)?.topMargin ?: verticalMarginPx
    }

    private fun setHandleTop(requestedTop: Int) {
        val maxTop = (height - touchHeightPx - verticalMarginPx).coerceAtLeast(verticalMarginPx)
        val clamped = requestedTop.coerceIn(verticalMarginPx, maxTop)
        val params = handle.layoutParams as LayoutParams
        if (params.topMargin != clamped) {
            params.topMargin = clamped
            handle.layoutParams = params
        }
    }

    private fun persistHandlePosition() {
        val available = (height - touchHeightPx).coerceAtLeast(1)
        val ratio = (currentTopMargin().toFloat() / available.toFloat()).coerceIn(0f, 1f)
        preferences.edit().putFloat("vertical_ratio", ratio).apply()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
