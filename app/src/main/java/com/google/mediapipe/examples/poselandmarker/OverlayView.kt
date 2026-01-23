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
package com.ttcoachai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.ttcoachai.models.StrokePhase
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var rawLandmarks: List<List<NormalizedLandmark>>? = null
    private var pointPaint = Paint()
    private var linePaint = Paint()
    private var rightArmPaint = Paint()  // Separate paint for right arm highlighting

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    // Stroke phase for color-coding
    private var currentPhase: StrokePhase = StrokePhase.READY
    private var phaseColoringEnabled: Boolean = false

    init {
        initPaints()
    }

    fun clear() {
        results = null
        rawLandmarks = null
        currentPhase = StrokePhase.READY
        pointPaint.reset()
        linePaint.reset()
        rightArmPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color =
            ContextCompat.getColor(context!!, R.color.mp_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL

        rightArmPaint.color = Color.GREEN
        rightArmPaint.strokeWidth = LANDMARK_STROKE_WIDTH * 1.5f
        rightArmPaint.style = Paint.Style.STROKE
    }

    /**
     * Set the current stroke phase for color-coding
     */
    fun setStrokePhase(phase: StrokePhase) {
        currentPhase = phase
        if (phaseColoringEnabled) {
            updatePhaseColors()
        }
    }

    /**
     * Enable/disable phase-based coloring
     */
    fun setPhaseColoringEnabled(enabled: Boolean) {
        phaseColoringEnabled = enabled
        if (enabled) {
            updatePhaseColors()
        } else {
            initPaints()
        }
    }

    /**
     * Update paint colors based on current stroke phase
     */
    private fun updatePhaseColors() {
        val phaseColor = when (currentPhase) {
            StrokePhase.READY -> ContextCompat.getColor(context, R.color.phase_ready)
            StrokePhase.BACKSWING -> ContextCompat.getColor(context, R.color.phase_backswing)
            StrokePhase.FORWARD_SWING -> ContextCompat.getColor(context, R.color.phase_forward_swing)
            StrokePhase.CONTACT -> ContextCompat.getColor(context, R.color.phase_contact)
            StrokePhase.FOLLOW_THROUGH -> ContextCompat.getColor(context, R.color.phase_follow_through)
            StrokePhase.RECOVERY -> ContextCompat.getColor(context, R.color.phase_recovery)
        }

        // Right arm gets the phase color (playing arm)
        rightArmPaint.color = phaseColor
        rightArmPaint.strokeWidth = LANDMARK_STROKE_WIDTH * 1.5f

        // Points also get phase color during active phases
        if (currentPhase != StrokePhase.READY && currentPhase != StrokePhase.RECOVERY) {
            pointPaint.color = phaseColor
        } else {
            pointPaint.color = Color.YELLOW
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        // Use rawLandmarks if available, otherwise use results
        val landmarks = rawLandmarks ?: results?.landmarks() ?: return

        for (landmark in landmarks) {
            for (normalizedLandmark in landmark) {
                canvas.drawPoint(
                    normalizedLandmark.x() * imageWidth * scaleFactor + offsetX,
                    normalizedLandmark.y() * imageHeight * scaleFactor + offsetY,
                    pointPaint
                )
            }

            if (landmarks.isNotEmpty() && landmarks[0].size >= 33) {
                PoseLandmarker.POSE_LANDMARKS.forEach {
                    val startIdx = it!!.start()
                    val endIdx = it.end()

                    // Use rightArmPaint for right arm connections when phase coloring is enabled
                    val paint = if (phaseColoringEnabled && isRightArmConnection(startIdx, endIdx)) {
                        rightArmPaint
                    } else {
                        linePaint
                    }

                    canvas.drawLine(
                        landmarks[0][startIdx].x() * imageWidth * scaleFactor + offsetX,
                        landmarks[0][startIdx].y() * imageHeight * scaleFactor + offsetY,
                        landmarks[0][endIdx].x() * imageWidth * scaleFactor + offsetX,
                        landmarks[0][endIdx].y() * imageHeight * scaleFactor + offsetY,
                        paint
                    )
                }
            }
        }
    }

    /**
     * Check if connection is part of the right arm (playing arm for forehand)
     * Right arm landmarks: 12 (shoulder), 14 (elbow), 16 (wrist), 18 (pinky), 20 (index), 22 (thumb)
     */
    private fun isRightArmConnection(startIdx: Int, endIdx: Int): Boolean {
        val rightArmLandmarks = setOf(12, 14, 16, 18, 20, 22)
        return startIdx in rightArmLandmarks && endIdx in rightArmLandmarks
    }

    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = poseLandmarkerResults
        rawLandmarks = null
        updateScaleAndOffset(imageHeight, imageWidth, runningMode)
    }

    fun setResults(
        landmarks: List<List<NormalizedLandmark>>,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = null
        rawLandmarks = landmarks
        updateScaleAndOffset(imageHeight, imageWidth, runningMode)
    }

    private fun updateScaleAndOffset(imageHeight: Int, imageWidth: Int, runningMode: RunningMode) {
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                // Calculate scale to fit video/image within view (letterbox/pillarbox)
                scaleFactor = min(width * 1f / imageWidth, height * 1f / imageHeight)
                // Calculate offsets to center the content (matching VideoView behavior)
                val scaledWidth = imageWidth * scaleFactor
                val scaledHeight = imageHeight * scaleFactor
                offsetX = (width - scaledWidth) / 2f
                offsetY = (height - scaledHeight) / 2f
            }
            RunningMode.LIVE_STREAM -> {
                // PreviewView is in FILL_START mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed.
                scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
                offsetX = 0f
                offsetY = 0f
            }
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 12F
    }
}