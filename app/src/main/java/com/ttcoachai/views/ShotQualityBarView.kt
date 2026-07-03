package com.ttcoachai.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.ttcoachai.R

/**
 * Fully-rounded two-segment shot-quality pill: clean (gold) + error (coral) over a
 * sink track. Colors from ttc_* tokens; segment widths from [ChartGeometry].
 */
class ShotQualityBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var cleanCount = 0
    private var errorCount = 0

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ttc_sink)
    }
    private val cleanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ttc_gold_bright)
    }
    private val errorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ttc_shot_error)
    }

    fun setCounts(cleanCount: Int, errorCount: Int) {
        this.cleanCount = cleanCount
        this.errorCount = errorCount
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f

        canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, trackPaint)

        val cleanFrac = ChartGeometry.cleanFraction(cleanCount, errorCount)
        val errorFrac = ChartGeometry.errorFraction(cleanCount, errorCount)
        if (cleanFrac <= 0f && errorFrac <= 0f) return

        val cleanW = w * cleanFrac
        canvas.drawRoundRect(RectF(0f, 0f, (cleanW + r).coerceAtMost(w), h), r, r, cleanPaint)
        if (errorFrac > 0f) {
            val errorStart = cleanW
            canvas.drawRoundRect(RectF((errorStart - r).coerceAtLeast(0f), 0f, w, h), r, r, errorPaint)
        }
    }
}
