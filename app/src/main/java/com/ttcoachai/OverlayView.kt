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
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.ttcoachai.shared.models.BallDetection
import com.ttcoachai.shared.models.BallDetectionStatus
import com.ttcoachai.shared.models.BallPosition2D
import com.ttcoachai.shared.models.StrokePhase
import com.ttcoachai.shared.models.SynchronizedFrame
import com.ttcoachai.shared.models.TrajectorySegment
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.Optional
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

    // Ball detection overlay
    private var ballDetection: BallDetection? = null
    private var ballPaint = Paint()
    private var ballOutlinePaint = Paint()

    // Trajectory segment overlay
    private var trajectorySegments: List<TrajectorySegment> = emptyList()
    private val trajectoryPath = Path()
    private val trajectoryPaints: List<Paint> = TRAJECTORY_SEGMENT_COLORS.map { color ->
        Paint().apply {
            this.color = color
            strokeWidth = TRAJECTORY_STROKE_WIDTH
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    // Dev-only additive renderers (Phase 7: BaselinePreviewActivity).
    // Non-null jointTint returns a highlight color for landmarks that should stand
    // out (e.g., a rule failed at this frame for a metric tied to this joint).
    // Humanization draws filled body parts on top of the wireframe so an
    // unfamiliar pose reads as a human figure instead of a cloud of dots.
    private var jointTint: ((Int) -> Int?)? = null
    private var humanizationEnabled: Boolean = false
    private val humanizedBonePaint = Paint().apply {
        color = Color.argb(200, 60, 140, 230)
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private val humanizedHeadPaint = Paint().apply {
        color = Color.argb(220, 230, 200, 170)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val humanizedTorsoPaint = Paint().apply {
        color = Color.argb(180, 60, 100, 170)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val humanizedRacketPaint = Paint().apply {
        color = Color.argb(230, 30, 30, 30)
        strokeWidth = LANDMARK_STROKE_WIDTH * 1.3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private val highlightedJointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    init {
        initPaints()
    }

    fun clear() {
        results = null
        rawLandmarks = null
        ballDetection = null
        trajectorySegments = emptyList()
        currentPhase = StrokePhase.READY
        pointPaint.reset()
        linePaint.reset()
        rightArmPaint.reset()
        ballPaint.reset()
        ballOutlinePaint.reset()
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

        // Ball detection: filled semi-transparent circle with solid outline
        ballPaint.color = Color.argb(160, 255, 165, 0)  // semi-transparent orange
        ballPaint.style = Paint.Style.FILL
        ballPaint.isAntiAlias = true

        ballOutlinePaint.color = Color.WHITE
        ballOutlinePaint.strokeWidth = 3f
        ballOutlinePaint.style = Paint.Style.STROKE
        ballOutlinePaint.isAntiAlias = true
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
     * Update ball detection for the current frame.
     * Pass null to clear any previously drawn ball.
     * Should be called from the main thread (or postInvalidate will be called).
     */
    fun setBallDetection(detection: BallDetection?) {
        ballDetection = detection
        postInvalidate()
    }

    /**
     * Update trajectory segments to draw on the overlay.
     * Pass an empty list to clear any previously drawn trajectory.
     */
    fun setTrajectorySegments(segments: List<TrajectorySegment>) {
        trajectorySegments = segments
        postInvalidate()
    }

    /**
     * Feed a [SynchronizedFrame] to the overlay so that the ball marker and skeleton
     * are rendered from the same unified data point, aligned to within 1 frame.
     *
     * - If [frame.ball] is non-null it is forwarded to the ball detection renderer.
     * - If [frame.pose] is non-null, its landmarks are forwarded to the skeleton renderer.
     * - Null fields clear the corresponding overlay layer.
     *
     * Should be called from the main thread (or [postInvalidate] will be used internally).
     */
    fun setSynchronizedFrame(frame: SynchronizedFrame) {
        ballDetection = frame.ball

        val poseFrame = frame.pose
        if (poseFrame != null) {
            // Convert shared Landmark3D list to the NormalizedLandmark format expected by draw()
            val normalizedLandmarks: List<NormalizedLandmark> = poseFrame.landmarks.map { lm ->
                NormalizedLandmark.create(lm.x, lm.y, lm.z,
                    Optional.of(lm.visibility), Optional.of(lm.presence))
            }
            rawLandmarks = listOf(normalizedLandmarks)
            results = null
        } else {
            rawLandmarks = null
            results = null
        }

        postInvalidate()
    }

    /**
     * Dev-only (Phase 7): provide a per-landmark-index color override. Returning
     * null keeps the default point paint. Pass null to clear.
     */
    fun setJointTint(tint: ((Int) -> Int?)?) {
        jointTint = tint
        postInvalidate()
    }

    /**
     * Dev-only (Phase 7): toggle the humanized body overlay (filled bones,
     * head circle, torso polygon, racket stick). Drawn on top of the
     * wireframe so the default pose-landmark debugging view still works.
     */
    fun setHumanizationEnabled(enabled: Boolean) {
        humanizationEnabled = enabled
        postInvalidate()
    }

    /**
     * Convenience for the editor — lets it render a static [com.ttcoachai.shared.models.PoseFrame]
     * without constructing a [com.ttcoachai.shared.models.SynchronizedFrame].
     */
    fun setPoseFrame(frame: com.ttcoachai.shared.models.PoseFrame?) {
        if (frame == null) {
            rawLandmarks = null
            results = null
            postInvalidate()
            return
        }
        val normalized: List<NormalizedLandmark> = frame.landmarks.map { lm ->
            NormalizedLandmark.create(
                lm.x, lm.y, lm.z,
                Optional.of(lm.visibility), Optional.of(lm.presence)
            )
        }
        rawLandmarks = listOf(normalized)
        results = null
        postInvalidate()
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

        // Draw trajectory curves beneath the ball marker and skeleton
        drawTrajectorySegments(canvas)

        // Draw ball detection marker (drawn beneath skeleton so skeleton stays readable)
        drawBallDetection(canvas)

        // Use rawLandmarks if available, otherwise use results
        val landmarks = rawLandmarks ?: results?.landmarks() ?: return

        for (landmark in landmarks) {
            if (humanizationEnabled && landmark.size >= 33) {
                drawHumanizedFigure(canvas, landmark)
            }

            for ((idx, normalizedLandmark) in landmark.withIndex()) {
                val px = normalizedLandmark.x() * imageWidth * scaleFactor + offsetX
                val py = normalizedLandmark.y() * imageHeight * scaleFactor + offsetY
                val tint = jointTint?.invoke(idx)
                if (tint != null) {
                    highlightedJointPaint.color = tint
                    canvas.drawCircle(px, py, LANDMARK_STROKE_WIDTH * 0.9f, highlightedJointPaint)
                } else {
                    canvas.drawPoint(px, py, pointPaint)
                }
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
     * Phase 7 humanization Tier 1. Draws filled torso polygon, capsule bones
     * for arms/legs, head circle at nose, and a racket stick from the right
     * wrist. Kept simple — this is a dev-only orientation aid, not production UI.
     */
    private fun drawHumanizedFigure(canvas: Canvas, lm: List<NormalizedLandmark>) {
        fun px(i: Int) = lm[i].x() * imageWidth * scaleFactor + offsetX
        fun py(i: Int) = lm[i].y() * imageHeight * scaleFactor + offsetY

        // Torso polygon: right shoulder (12) → left shoulder (11) → left hip (23) → right hip (24)
        val torso = Path().apply {
            moveTo(px(12), py(12))
            lineTo(px(11), py(11))
            lineTo(px(23), py(23))
            lineTo(px(24), py(24))
            close()
        }
        canvas.drawPath(torso, humanizedTorsoPaint)

        // Capsule bones — stroke-style thick lines with rounded caps approximate the look.
        humanizedBonePaint.style = Paint.Style.STROKE
        humanizedBonePaint.strokeWidth = LANDMARK_STROKE_WIDTH * 2.2f
        val capsuleBones = listOf(
            11 to 13, 13 to 15,   // left arm
            12 to 14, 14 to 16,   // right arm
            23 to 25, 25 to 27,   // left leg
            24 to 26, 26 to 28    // right leg
        )
        for ((a, b) in capsuleBones) canvas.drawLine(px(a), py(a), px(b), py(b), humanizedBonePaint)
        humanizedBonePaint.style = Paint.Style.FILL

        // Head circle sized to ~shoulder-to-nose distance
        val noseX = px(0); val noseY = py(0)
        val shoulderMidX = (px(11) + px(12)) / 2f
        val shoulderMidY = (py(11) + py(12)) / 2f
        val headRadius = kotlin.math.hypot(noseX - shoulderMidX, noseY - shoulderMidY) * 0.55f
        if (headRadius > 0f) canvas.drawCircle(noseX, noseY, headRadius, humanizedHeadPaint)

        // Racket stick: extend from right wrist (16) along wrist→index (16→20).
        // Falls back to forearm direction (14→16) when index finger visibility is low.
        val wristX = px(16); val wristY = py(16)
        val indexVisibility = lm.getOrNull(20)?.visibility()?.orElse(0f) ?: 0f
        val (dirX, dirY) = if (indexVisibility >= 0.5f) {
            px(20) - wristX to py(20) - wristY
        } else {
            wristX - px(14) to wristY - py(14)
        }
        val dirLen = kotlin.math.hypot(dirX, dirY)
        if (dirLen > 1e-3f) {
            val racketLen = headRadius.coerceAtLeast(LANDMARK_STROKE_WIDTH * 6f) * 2.2f
            val tipX = wristX + dirX / dirLen * racketLen
            val tipY = wristY + dirY / dirLen * racketLen
            canvas.drawLine(wristX, wristY, tipX, tipY, humanizedRacketPaint)
            canvas.drawCircle(tipX, tipY, racketLen * 0.18f, humanizedRacketPaint)
        }
    }

    /**
     * Draw trajectory curves for all segments, each in a distinct colour.
     * Uses the segment's fittedPositions (which include interpolated gap frames)
     * to draw a smooth connected polyline approximating the parabolic arc.
     */
    private fun drawTrajectorySegments(canvas: Canvas) {
        if (trajectorySegments.isEmpty()) return

        for (segment in trajectorySegments) {
            val positions = segment.fittedPositions
            if (positions.size < 2) continue

            val paint = trajectoryPaints[segment.segmentIndex % trajectoryPaints.size]
            trajectoryPath.reset()

            var first = true
            for (pos in positions) {
                val px = pos.x * imageWidth * scaleFactor + offsetX
                val py = pos.y * imageHeight * scaleFactor + offsetY
                if (first) {
                    trajectoryPath.moveTo(px, py)
                    first = false
                } else {
                    trajectoryPath.lineTo(px, py)
                }
            }
            canvas.drawPath(trajectoryPath, paint)
        }
    }

    /**
     * Draw the detected ball as a semi-transparent filled circle with a white outline.
     * Does nothing if no detection is set or status is not DETECTED.
     */
    private fun drawBallDetection(canvas: Canvas) {
        val detection = ballDetection ?: return
        if (detection.status != BallDetectionStatus.DETECTED) return

        val cx = detection.x * imageWidth * scaleFactor + offsetX
        val cy = detection.y * imageHeight * scaleFactor + offsetY
        // Use detected radius if available; fall back to a fixed display radius
        val radius = if (detection.radiusPx > 0f) {
            detection.radiusPx * scaleFactor
        } else {
            BALL_DISPLAY_RADIUS
        }

        canvas.drawCircle(cx, cy, radius, ballPaint)
        canvas.drawCircle(cx, cy, radius, ballOutlinePaint)
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
        private const val BALL_DISPLAY_RADIUS = 18F      // Fallback display radius in pixels
        private const val TRAJECTORY_STROKE_WIDTH = 4F   // Trajectory curve stroke width

        // Colour palette for trajectory segments (cycles through for segment index % n)
        private val TRAJECTORY_SEGMENT_COLORS = listOf(
            Color.argb(200, 0,   200, 255),  // Cyan-blue   — segment 0
            Color.argb(200, 255, 100,   0),  // Orange      — segment 1
            Color.argb(200,   0, 220, 100),  // Green       — segment 2
            Color.argb(200, 220,   0, 220),  // Magenta     — segment 3
            Color.argb(200, 255, 220,   0),  // Yellow      — segment 4
            Color.argb(200, 100, 100, 255)   // Purple-blue — segment 5+
        )
    }
}