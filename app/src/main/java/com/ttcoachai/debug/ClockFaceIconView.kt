package com.ttcoachai.debug

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Tiny analog-clock-face indicator for the drill editor. Draws the clock
 * outline + 12 hour ticks + a dot at the center (player) + a red dot at
 * the current camera position.
 *
 * Yaw semantics: `cameraYawDeg` = 0 means camera at 6 o'clock (flat
 * frontal). Positive yaw rotates counterclockwise on the clock (toward
 * 7:00). Canvas angle is `90 + yaw` because 6 o'clock maps to canvas
 * angle 90° (downward) in the +y-down coord system.
 */
class ClockFaceIconView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var cameraYawDeg: Float = 0f

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#666666")
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#888888")
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#AAAAAA")
    }
    private val cameraDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E53935")
    }

    fun setCameraYawDeg(yaw: Float) {
        cameraYawDeg = yaw
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val outerRadius = min(cx, cy) - 4f
        if (outerRadius <= 0f) return

        canvas.drawCircle(cx, cy, outerRadius, outlinePaint)

        // 12 hour ticks
        val tickInner = outerRadius - 4f
        for (hour in 0 until 12) {
            val a = Math.toRadians((hour * 30 - 90).toDouble()) // 12 o'clock = -90 in canvas angle
            val c = cos(a).toFloat(); val s = sin(a).toFloat()
            canvas.drawLine(
                cx + c * tickInner, cy + s * tickInner,
                cx + c * outerRadius, cy + s * outerRadius,
                tickPaint
            )
        }

        // Center player dot
        canvas.drawCircle(cx, cy, 2.5f, centerPaint)

        // Camera dot at cameraYawDeg (yaw=0 → 6 o'clock → canvas angle 90°)
        val camAngleRad = Math.toRadians((90.0 + cameraYawDeg).toDouble())
        val camX = cx + cos(camAngleRad).toFloat() * outerRadius
        val camY = cy + sin(camAngleRad).toFloat() * outerRadius
        canvas.drawCircle(camX, camY, 4f, cameraDotPaint)
    }
}
