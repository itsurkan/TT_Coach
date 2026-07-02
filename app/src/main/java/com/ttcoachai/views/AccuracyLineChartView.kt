package com.ttcoachai.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.ttcoachai.R

/**
 * Lightweight accuracy-through-session line chart (no charting dependency).
 * Grid at 40/60/80, gold polyline + 10% gold fill, filled dot at peak, hollow
 * dot at last point. All colors from ttc_* tokens. X labels are drawn by the layout.
 */
class AccuracyLineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var values: List<Float> = emptyList()
    private var peakIndex: Int = 0

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ttc_chart_grid)
        strokeWidth = dp(1f)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.ttc_gold_bright)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.ttc_gold_bright)
        alpha = 26 // ~10%
    }
    private val dotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.ttc_gold_bright)
    }
    private val dotHollowStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = ContextCompat.getColor(context, R.color.ttc_gold_bright)
    }
    private val dotHollowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.ttc_surface)
    }

    fun setData(values: List<Float>, peakIndex: Int) {
        this.values = values
        this.peakIndex = peakIndex
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val (lo, hi) = ChartGeometry.domain(values)

        // gridlines at 40 / 60 / 80 (only those inside [lo,hi])
        for (g in intArrayOf(40, 60, 80)) {
            if (g in lo.toInt()..hi.toInt()) {
                val y = ChartGeometry.valueToY(g.toFloat(), lo, hi) / 130f * h
                canvas.drawLine(0f, y, w, y, gridPaint)
            }
        }
        if (values.size < 2) return

        fun px(i: Int) = if (values.size == 1) w / 2f else i.toFloat() / (values.size - 1) * w
        fun py(i: Int) = ChartGeometry.valueToY(values[i], lo, hi) / 130f * h

        // fill under the curve
        val fill = Path().apply {
            moveTo(px(0), h)
            for (i in values.indices) lineTo(px(i), py(i))
            lineTo(px(values.lastIndex), h)
            close()
        }
        canvas.drawPath(fill, fillPaint)

        // polyline
        val line = Path().apply {
            moveTo(px(0), py(0))
            for (i in 1 until values.size) lineTo(px(i), py(i))
        }
        canvas.drawPath(line, linePaint)

        // filled peak dot
        if (peakIndex in values.indices) {
            canvas.drawCircle(px(peakIndex), py(peakIndex), dp(4f), dotFillPaint)
        }
        // hollow last dot
        val last = values.lastIndex
        canvas.drawCircle(px(last), py(last), dp(4f), dotHollowFill)
        canvas.drawCircle(px(last), py(last), dp(4f), dotHollowStroke)
    }
}
