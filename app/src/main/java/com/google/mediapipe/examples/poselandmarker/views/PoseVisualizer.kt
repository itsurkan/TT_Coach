/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.poselandmarker.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlin.math.max
import kotlin.math.min

class PoseVisualizer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var frames: List<List<NormalizedLandmark>> = emptyList()
    private var currentFrameIndex = 0
    private var pointPaint = Paint()
    private var linePaint = Paint()
    private var rightArmPaint = Paint()

    private var scaleFactor: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    private var animator: ValueAnimator? = null
    private var isPlaying = false

    init {
        initPaints()
    }

    private fun initPaints() {
        linePaint.color = ContextCompat.getColor(context, R.color.mp_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.isAntiAlias = true

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL
        pointPaint.isAntiAlias = true

        rightArmPaint.color = Color.GREEN
        rightArmPaint.strokeWidth = LANDMARK_STROKE_WIDTH * 1.5f
        rightArmPaint.style = Paint.Style.STROKE
        rightArmPaint.strokeCap = Paint.Cap.ROUND
        rightArmPaint.isAntiAlias = true
    }

    fun setFrames(poseFrames: List<List<NormalizedLandmark>>) {
        this.frames = poseFrames
        currentFrameIndex = 0
        if (frames.isNotEmpty()) {
            calculateScaleAndOffset()
            invalidate()
        }
    }
    
    fun playAnimation() {
        if (frames.isEmpty()) return
        
        stopAnimation()
        isPlaying = true
        
        animator = ValueAnimator.ofInt(0, frames.size - 1).apply {
            duration = (frames.size * 33).toLong() // Approx 30fps
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                currentFrameIndex = animation.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        isPlaying = false
        animator?.cancel()
        animator = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateScaleAndOffset()
    }

    private fun calculateScaleAndOffset() {
        if (frames.isEmpty()) return
        
        // Assume normalized coordinates 0..1
        // We want to fit 0..1 into the view, maintaining aspect ratio
        // But landmarks can be slightly outside 0..1. For simplicity, we stick to 0..1
        
        val imageWidth = 1f
        val imageHeight = 1f
        
        // Inverted calculation compared to overlay because we are drawing normalized points directly
        // on a canvas of size w x h.
        
        // We actually want min scale to fit WHOLE range. 
        // For aesthetic, let's just use the view dimensions.
        // x * width, y * height
        
        // However, usually we want to keep aspect ratio.
        // Let's assume input space is square or 3:4. 
        // We will just scale normalized coordinates to view dimensions maintaining aspect ratio of the VIEW? 
        // No, normalized landmarks are usually relative to image.
        // Let's treat them as 0.0-1.0 fitting into the view.
        
        val viewRatio = width.toFloat() / height.toFloat()
        // If view is wider than tall, limiter is height
        
        // Let's just scale to fit centered.
        // Let's assume 1.0 in normalized = min(width, height) pixels for safety 
        // or just stretch to fill if we don't care about aspect ratio distortion (but we do).
        
        // Standard video aspect ratio is usually 4:3 or 16:9. 
        // Let's use a scale such that 1.0 covers the smaller dimension of the view.
        
        val contentSize = min(width, height).toFloat()
        scaleFactor = contentSize * 0.8f // 80% of the view size to leave margin
        
        offsetX = (width - scaleFactor) / 2f
        offsetY = (height - scaleFactor) / 2f
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        
        if (frames.isEmpty()) return
        
        val landmarks = frames[currentFrameIndex]
        if (landmarks.isEmpty()) return

        // Draw connections
        if (landmarks.size >= 33) {
            PoseLandmarker.POSE_LANDMARKS.forEach {
                val startIdx = it!!.start()
                val endIdx = it.end()

                val paint = if (isRightArmConnection(startIdx, endIdx)) {
                    rightArmPaint
                } else {
                    linePaint
                }

                val start = landmarks[startIdx]
                val end = landmarks[endIdx]

                canvas.drawLine(
                    start.x() * scaleFactor + offsetX,
                    start.y() * scaleFactor + offsetY,
                    end.x() * scaleFactor + offsetX,
                    end.y() * scaleFactor + offsetY,
                    paint
                )
            }
        }

        // Draw points
        for (normalizedLandmark in landmarks) {
            canvas.drawPoint(
                normalizedLandmark.x() * scaleFactor + offsetX,
                normalizedLandmark.y() * scaleFactor + offsetY,
                pointPaint
            )
        }
    }

    private fun isRightArmConnection(startIdx: Int, endIdx: Int): Boolean {
        val rightArmLandmarks = setOf(12, 14, 16, 18, 20, 22)
        return startIdx in rightArmLandmarks && endIdx in rightArmLandmarks
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F
    }
}
