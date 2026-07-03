package com.ttcoachai.shared.detection

import com.ttcoachai.shared.analysis.AngleCalculations2D
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D

/**
 * Stroke detection via wrist-speed local maximum (context doc §4), adapted from the
 * phase-FSM approach of [StrokePhaseDetector] to the 2D COCO topology. Batch API for
 * the fixture-driven phase; the smoothed-speed core is streaming-compatible (Phase 3).
 *
 * Units (DESIGN_LIMITATIONS L-01/L-02):
 *  - speeds are in TORSO-LENGTHS PER SECOND — invariant to camera distance/zoom
 *    (L-01) and to capture fps (L-02); torso = median xScale-corrected
 *    shoulder-mid→hip-mid distance over the sequence;
 *  - all tuning windows are in MILLISECONDS, converted to frame counts via
 *    [detect]'s intervalMs — Phase 3 capture fps is configurable 30/60/120, so
 *    frame-count tuning would silently change meaning with every fps setting.
 *
 * This class is now a thin delegating wrapper: the generalized algorithm — any
 * tracked keypoint, not just the dominant wrist — lives in [MovementDetector],
 * driven by [DetectionConfig] (docs/superpowers/specs/
 * 2026-07-02-generic-movement-pipeline-design.md). Kept for API compatibility;
 * behavior for the wrist-signal / forehand-drive defaults is unchanged bit-for-bit.
 */
@Deprecated(
    "Use MovementDetector with DetectionConfig",
    ReplaceWith(
        "MovementDetector(DetectionConfig(minScore = minScore, smoothingWindowMs = smoothingWindowMs, " +
            "peakWindowRadiusMs = peakWindowRadiusMs, minPeakSpeed = minPeakSpeed, " +
            "boundaryFraction = boundaryFraction, minPeakGapMs = minPeakGapMs))",
        "com.ttcoachai.shared.detection.MovementDetector",
        "com.ttcoachai.shared.detection.DetectionConfig"
    ),
    level = DeprecationLevel.WARNING
)
class StrokeDetector2D(
    private val minScore: Float = AngleCalculations2D.DEFAULT_MIN_SCORE,
    private val smoothingWindowMs: Long = 300,
    private val peakWindowRadiusMs: Long = 300,
    /**
     * Torso-lengths per second. Threshold applies to the SMOOTHED signal —
     * a raw peak of 2.4 torso/s smooths to ~2.0 with a 3-frame window.
     */
    private val minPeakSpeed: Float = 1.0f,
    private val boundaryFraction: Float = 0.3f,
    private val minPeakGapMs: Long = 500
) {

    private val delegate = MovementDetector(
        DetectionConfig(
            signalKeypoint = SignalKeypoint.DOMINANT_WRIST,
            minScore = minScore,
            smoothingWindowMs = smoothingWindowMs,
            peakWindowRadiusMs = peakWindowRadiusMs,
            minPeakSpeed = minPeakSpeed,
            boundaryFraction = boundaryFraction,
            minPeakGapMs = minPeakGapMs
        )
    )

    /**
     * Detects strokes from the wrist-speed signal of [frames].
     *
     * Adjacent strokes never overlap: when boundary walks meet, both are clamped
     * at the inter-peak valley (they may share that single boundary frame).
     *
     * NOTE: [intervalMs] is integer milliseconds. At 120 fps (true 8.33 ms/frame),
     * truncation to 8 ms inflates computed speeds by ~4 %. Phase 3 live loop should
     * derive dt from actual frame timestamps rather than a fixed interval (deferred,
     * registry follow-up).
     */
    fun detect(
        frames: List<PoseFrame2D>,
        handedness: Handedness,
        xScale: Float,
        intervalMs: Long
    ): List<Stroke2D> = delegate.detect(frames, handedness, xScale, intervalMs)

    /**
     * Exposes the underlying [MovementDetector] so callers migrating to
     * [com.ttcoachai.shared.drill.MovementRepPipeline] can build a pipeline around
     * an existing (possibly custom-tuned) `StrokeDetector2D` instance without
     * re-deriving its [DetectionConfig].
     */
    fun asMovementDetector(): MovementDetector = delegate
}
