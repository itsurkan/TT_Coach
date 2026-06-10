package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.hypot

/**
 * Estimates how far the camera is from a perfect side (profile) view, in degrees,
 * from 2D foreshortening: in a true profile the shoulders overlap horizontally;
 * the wider they appear relative to torso length, the further the camera is from
 * perpendicular. KMP port of the camera-yaw compensation idea in poses_viewer's
 * MannequinEditor — the z-based math there (extractTorsoLegs.ts) needs MediaPipe z
 * and cannot work on COCO-17.
 *
 * The player moves their feet during a drill, so yaw is NOT a per-session constant:
 * orchestrators call [estimateYawForStroke] per rep (design note 10). The Phase 3
 * live loop wraps [estimateSideViewYawDeg] in a rolling ~1 s window the same way.
 *
 * Returns |yaw| only — foreshortening cannot recover the sign, and the 1/cos
 * correction (ViewGeometry.xScale) is sign-independent anyway.
 *
 * NOTE: takes the raw aspectRatio (NOT xScale) — this runs BEFORE any yaw is known.
 */
object CameraAngleEstimator {

    /**
     * Biacromial width ≈ 0.259·H, shoulder–hip torso length ≈ 0.288·H
     * (Drillis & Contini 1966 — same source as poses_viewer bone lengths)
     * → shoulder width ≈ 0.9 × torso length.
     */
    const val SHOULDER_TO_TORSO_RATIO = 0.9f
    const val DEFAULT_SAMPLE_FRAMES = 30

    /** Pre-stroke ready-stance window in ms — fps-independent (DESIGN_LIMITATIONS L-02). */
    const val DEFAULT_LOOKBACK_MS = 1000L

    private const val RAD_TO_DEG = (180.0 / PI).toFloat()
    private const val MIN_TORSO_LEN = 1e-4f

    /**
     * Per-rep yaw: estimated from the [lookbackMs] ready-stance window immediately
     * BEFORE the stroke — estimating during the swing would confound the player's
     * own body rotation with camera placement. Falls back to the stroke's own
     * window when there is no lookback (stroke at recording start).
     */
    fun estimateYawForStroke(
        frames: List<PoseFrame2D>,
        stroke: Stroke2D,
        aspectRatio: Float,
        intervalMs: Long,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE,
        lookbackMs: Long = DEFAULT_LOOKBACK_MS
    ): Float? {
        require(intervalMs > 0) { "intervalMs must be > 0, got $intervalMs" }
        val lookbackFrames = (lookbackMs / intervalMs).toInt().coerceAtLeast(1)
        val until = stroke.startFrame.coerceIn(0, frames.size)
        val from = (until - lookbackFrames).coerceAtLeast(0)
        val preStroke = frames.subList(from, until)
        estimateSideViewYawDeg(preStroke, aspectRatio, minScore)?.let { return it }
        val strokeEnd = (stroke.endFrame + 1).coerceIn(0, frames.size)
        if (until >= strokeEnd) return null
        return estimateSideViewYawDeg(frames.subList(until, strokeEnd), aspectRatio, minScore)
    }

    /** Median per-frame yaw over the first [sampleFrames] frames with a person. Null if none qualify. */
    fun estimateSideViewYawDeg(
        frames: List<PoseFrame2D>,
        aspectRatio: Float,
        minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE,
        sampleFrames: Int = DEFAULT_SAMPLE_FRAMES
    ): Float? {
        val perFrame = frames.asSequence()
            .filter { it.keypoints.isNotEmpty() }
            .take(sampleFrames)
            .mapNotNull { frameYawDeg(it.keypoints, aspectRatio, minScore) }
            .toList()
        if (perFrame.isEmpty()) return null
        return median(perFrame)
    }

    private fun frameYawDeg(kp: List<Keypoint2D>, aspectRatio: Float, minScore: Float): Float? {
        val ls = kp.getOrNull(Coco17.LEFT_SHOULDER)?.takeIf { it.score >= minScore } ?: return null
        val rs = kp.getOrNull(Coco17.RIGHT_SHOULDER)?.takeIf { it.score >= minScore } ?: return null
        val lh = kp.getOrNull(Coco17.LEFT_HIP)?.takeIf { it.score >= minScore } ?: return null
        val rh = kp.getOrNull(Coco17.RIGHT_HIP)?.takeIf { it.score >= minScore } ?: return null

        val torsoLen = hypot(
            ((ls.x + rs.x) / 2f - (lh.x + rh.x) / 2f) * aspectRatio,
            (ls.y + rs.y) / 2f - (lh.y + rh.y) / 2f
        )
        if (torsoLen < MIN_TORSO_LEN) return null

        val shoulderSepX = abs(rs.x - ls.x) * aspectRatio
        val sinYaw = (shoulderSepX / (SHOULDER_TO_TORSO_RATIO * torsoLen)).coerceIn(0f, 1f)
        return asin(sinYaw) * RAD_TO_DEG
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }
}
