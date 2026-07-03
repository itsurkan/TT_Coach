package com.ttcoachai.pose

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.ttcoachai.shared.models.Keypoint2D

/**
 * Lightweight custom View that draws the 17 COCO keypoints + bones over the camera preview.
 *
 * All projection math (normalized keypoint -> pixel, score gating, bone table) delegates to
 * the pure [Coco17OverlayScaling] (C1). This class only performs Canvas drawing. Independent
 * of the frozen [com.ttcoachai.OverlayView] — does not read or write it.
 */
class Coco17OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    @Volatile
    private var keypoints: List<Keypoint2D>? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 193, 7) // gold accent
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 235, 130) // lighter gold
        style = Paint.Style.FILL
    }

    private val pointRadius = 8f

    /** Updates the latest keypoints and triggers a redraw. Null/empty clears the overlay. */
    fun setKeypoints(keypoints: List<Keypoint2D>?) {
        this.keypoints = if (keypoints.isNullOrEmpty()) null else keypoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val kps = keypoints ?: return
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        for ((startIdx, endIdx) in Coco17OverlayScaling.BONES) {
            val start = kps.getOrNull(startIdx) ?: continue
            val end = kps.getOrNull(endIdx) ?: continue
            val startPoint = Coco17OverlayScaling.project(start, w, h) ?: continue
            val endPoint = Coco17OverlayScaling.project(end, w, h) ?: continue
            canvas.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y, linePaint)
        }

        for (kp in kps) {
            val point = Coco17OverlayScaling.project(kp, w, h) ?: continue
            canvas.drawCircle(point.x, point.y, pointRadius, pointPaint)
        }
    }
}
