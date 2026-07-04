package com.ttcoachai.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.ttcoachai.R

/**
 * Horizontal filmstrip of recent reps for the training feedback-explanation bottom sheet: one
 * rounded-rect segment per rep, gold-dark design system. Data source: recent per-rep pass/fail
 * flags for a single [com.ttcoachai.shared.models.CorrectionType], derived from session history
 * (`TrainingStateManager` / captured `FeedbackItem`s) — this view only renders booleans, it does
 * not know about correction types or landmarks.
 */
class RepStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var flags: List<Boolean> = emptyList()
    private var selectedIndex: Int? = null

    /**
     * Invoked with the index into the ORIGINAL (pre-[MAX_SEGMENTS] windowing) flags list passed
     * to [setReps] when the user taps a segment. Null when tap handling is not wired up.
     */
    var onRepClick: ((Int) -> Unit)? = null
        set(value) {
            field = value
            isClickable = value != null
        }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val cleanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.ttc_success)
    }
    private val flaggedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.ttc_amber)
    }
    private val emptyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.ttc_outline)
        strokeWidth = dp(1.5f)
    }
    private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.ttc_text_1)
        strokeWidth = dp(2f)
    }

    private val segmentRect = RectF()

    /** Chronological per-rep flags; true = this correction type fired on that rep. */
    fun setReps(flags: List<Boolean>) {
        this.flags = flags
        selectedIndex = null
        invalidate()
    }

    /** Marks segment [index] (index into the original flags list passed to [setReps]) as selected. */
    fun setSelected(index: Int?) {
        selectedIndex = index
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (flags.isEmpty()) {
            val inset = emptyStrokePaint.strokeWidth / 2f
            segmentRect.set(inset, inset, w - inset, h - inset)
            canvas.drawRoundRect(segmentRect, dp(CORNER_RADIUS_DP), dp(CORNER_RADIUS_DP), emptyStrokePaint)
            return
        }

        val visible = flags.takeLast(MAX_SEGMENTS)
        val count = visible.size
        val gap = dp(GAP_DP)
        val totalGap = gap * (count - 1)
        val segmentWidth = (w - totalGap) / count
        if (segmentWidth <= 0f) return

        val originalStart = flags.size - count
        val corner = dp(CORNER_RADIUS_DP)
        for ((i, flagged) in visible.withIndex()) {
            val left = i * (segmentWidth + gap)
            segmentRect.set(left, 0f, left + segmentWidth, h)
            val paint = if (flagged) flaggedPaint else cleanPaint
            canvas.drawRoundRect(segmentRect, corner, corner, paint)

            if (selectedIndex == originalStart + i) {
                val inset = selectedStrokePaint.strokeWidth / 2f
                segmentRect.set(left + inset, inset, left + segmentWidth - inset, h - inset)
                canvas.drawRoundRect(segmentRect, corner, corner, selectedStrokePaint)
            }
        }
    }

    /**
     * Maps a touch x-coordinate to an index into the ORIGINAL flags list (accounting for the
     * [MAX_SEGMENTS] `takeLast` windowing in [onDraw]), or null if [x] doesn't land on a segment.
     */
    private fun hitTestOriginalIndex(x: Float): Int? {
        val w = width.toFloat()
        if (w <= 0f || flags.isEmpty()) return null

        val visible = flags.takeLast(MAX_SEGMENTS)
        val count = visible.size
        val gap = dp(GAP_DP)
        val totalGap = gap * (count - 1)
        val segmentWidth = (w - totalGap) / count
        if (segmentWidth <= 0f) return null

        val stride = segmentWidth + gap
        val visibleIndex = (x / stride).toInt().coerceIn(0, count - 1)
        // Reject hits that land in the inter-segment gap.
        val segLeft = visibleIndex * stride
        if (x < segLeft || x > segLeft + segmentWidth) return null

        val originalStart = flags.size - count
        return originalStart + visibleIndex
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onRepClick == null || flags.isEmpty()) return super.onTouchEvent(event)

        return when (event.action) {
            MotionEvent.ACTION_DOWN -> hitTestOriginalIndex(event.x) != null
            MotionEvent.ACTION_UP -> {
                val index = hitTestOriginalIndex(event.x)
                if (index != null) {
                    performClick()
                    onRepClick?.invoke(index)
                    true
                } else {
                    false
                }
            }
            else -> super.onTouchEvent(event)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        private const val MAX_SEGMENTS = 20
        private const val GAP_DP = 2f
        private const val CORNER_RADIUS_DP = 3f
    }
}
