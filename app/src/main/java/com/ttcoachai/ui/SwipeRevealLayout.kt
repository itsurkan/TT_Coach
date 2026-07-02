package com.ttcoachai.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.customview.widget.ViewDragHelper
import com.ttcoachai.R
import kotlin.math.abs

/**
 * A swipe-to-reveal row: the [foreground] card slides horizontally over two fixed-width action
 * panels (Clone on the right edge, Delete on the left) and STAYS open until the revealed button
 * is tapped or the row is closed. Only the foreground is draggable; the settle decision and drag
 * clamp live in the pure [SwipeSettle] helpers so they can be unit-tested.
 */
class SwipeRevealLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private lateinit var foreground: View
    private lateinit var clonePanel: View
    private lateinit var deletePanel: View

    private var buttonWidth = 0
    private var deleteEnabled = true

    var state: SwipeState = SwipeState.CLOSED
        private set

    val isOpen: Boolean get() = state != SwipeState.CLOSED

    /** Fires whenever the settled state changes (open or close). Used by the single-open coordinator. */
    var onStateChanged: ((SwipeState) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f

    private val dragHelper = ViewDragHelper.create(this, 1f, DragCallback())

    override fun onFinishInflate() {
        super.onFinishInflate()
        deletePanel = findViewById(R.id.swipe_delete_panel)
        clonePanel = findViewById(R.id.swipe_clone_panel)
        foreground = findViewById(R.id.swipe_foreground)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        buttonWidth = clonePanel.width
        // super.onLayout puts the match_parent foreground at left=0; restore the open offset
        // (matters when a recycled/laid-out row should stay open).
        val target = leftFor(state)
        if (foreground.left != target) foreground.offsetLeftAndRight(target - foreground.left)
    }

    /** Locks/unlocks the right (Delete) side. Built-in presets pass false. */
    fun setDeleteEnabled(enabled: Boolean) {
        deleteEnabled = enabled
    }

    fun close(animate: Boolean) = moveTo(SwipeState.CLOSED, animate)

    fun open(side: SwipeState, animate: Boolean) {
        if (side == SwipeState.OPEN_DELETE && !deleteEnabled) return
        moveTo(side, animate)
    }

    private fun leftFor(s: SwipeState): Int = when (s) {
        SwipeState.CLOSED -> 0
        SwipeState.OPEN_CLONE -> -buttonWidth
        SwipeState.OPEN_DELETE -> buttonWidth
    }

    private fun moveTo(target: SwipeState, animate: Boolean) {
        val finalLeft = leftFor(target)
        if (animate) {
            if (dragHelper.smoothSlideViewTo(foreground, finalLeft, foreground.top)) {
                postInvalidateOnAnimation()
            }
        } else {
            dragHelper.abort()
            foreground.offsetLeftAndRight(finalLeft - foreground.left)
        }
        setStateInternal(target)
    }

    private fun setStateInternal(newState: SwipeState) {
        if (newState != state) {
            state = newState
            onStateChanged?.invoke(newState)
        }
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) postInvalidateOnAnimation()
    }

    private fun isTouchInForeground(ev: MotionEvent): Boolean {
        val x = ev.x.toInt()
        return x >= foreground.left && x <= foreground.right
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (dragHelper.shouldInterceptTouchEvent(ev)) return true
        // When open, a tap on the slid-away foreground closes the row instead of passing through
        // as a select. Taps on the exposed panel (outside the foreground bounds) are NOT
        // intercepted, so the Clone/Delete buttons still receive their clicks.
        return isOpen && ev.actionMasked == MotionEvent.ACTION_DOWN && isTouchInForeground(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> downX = event.x
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val moved = abs(event.x - downX) > touchSlop
                if (!moved && isOpen && isTouchInForeground(event)) {
                    dragHelper.cancel()
                    close(animate = true)
                    return true
                }
            }
        }
        dragHelper.processTouchEvent(event)
        return true
    }

    private inner class DragCallback : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean = child === foreground

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int =
            clampSwipeOffset(left.toFloat(), buttonWidth.toFloat(), deleteEnabled).toInt()

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int = child.top

        override fun getViewHorizontalDragRange(child: View): Int = buttonWidth

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val target = decideSwipeSettle(
                offsetX = foreground.left.toFloat(),
                velocityX = xvel,
                buttonWidth = buttonWidth.toFloat(),
                deleteEnabled = deleteEnabled,
            )
            moveTo(target, animate = true)
        }
    }
}
