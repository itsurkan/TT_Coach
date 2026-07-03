package com.ttcoachai.shared.detection

import com.ttcoachai.shared.analysis.AngleCalculations2D
import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness

/**
 * Which tracked keypoint drives [MovementDetector]'s speed signal, resolved to a
 * dominant/non-dominant side via [Handedness] (docs/superpowers/specs/
 * 2026-07-02-generic-movement-pipeline-design.md, weakness #1 — the wrist was
 * previously hard-coded).
 */
enum class SignalKeypoint {
    DOMINANT_WRIST,
    NON_DOMINANT_WRIST,
    DOMINANT_ELBOW,
    NON_DOMINANT_ELBOW,
    DOMINANT_ANKLE,
    NON_DOMINANT_ANKLE;

    /** Resolves this signal keypoint to a COCO-17 index for the given playing hand. */
    fun index(handedness: Handedness): Int {
        val opposite = if (handedness == Handedness.RIGHT) Handedness.LEFT else Handedness.RIGHT
        return when (this) {
            DOMINANT_WRIST -> Coco17.wrist(handedness)
            NON_DOMINANT_WRIST -> Coco17.wrist(opposite)
            DOMINANT_ELBOW -> Coco17.elbow(handedness)
            NON_DOMINANT_ELBOW -> Coco17.elbow(opposite)
            DOMINANT_ANKLE -> Coco17.ankle(handedness)
            NON_DOMINANT_ANKLE -> Coco17.ankle(opposite)
        }
    }
}

/**
 * Per-movement tuning for [MovementDetector] (docs/superpowers/specs/
 * 2026-07-02-generic-movement-pipeline-design.md, weakness #2 — these were
 * previously forehand-tuned constructor defaults on `StrokeDetector2D`, not a
 * profile). Defaults reproduce `StrokeDetector2D`'s original forehand-drive
 * behavior bit-for-bit.
 */
data class DetectionConfig(
    val signalKeypoint: SignalKeypoint = SignalKeypoint.DOMINANT_WRIST,
    val minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE,
    val smoothingWindowMs: Long = 300,
    val peakWindowRadiusMs: Long = 300,
    /**
     * Torso-lengths per second. Threshold applies to the SMOOTHED signal —
     * a raw peak of 2.4 torso/s smooths to ~2.0 with a 3-frame window.
     */
    val minPeakSpeed: Float = 1.0f,
    val boundaryFraction: Float = 0.3f,
    val minPeakGapMs: Long = 500
)
