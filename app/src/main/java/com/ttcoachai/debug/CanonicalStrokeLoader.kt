package com.ttcoachai.debug

import android.content.Context
import com.ttcoachai.shared.analysis.CanonicalStrokeBuilder
import com.ttcoachai.shared.detection.JsonStrokeDetector
import com.ttcoachai.shared.models.PoseFrame

/**
 * Loads the canonical stroke for a drill: fixture → JsonStrokeDetector →
 * [CanonicalStrokeBuilder]. Falls back to the raw fixture if the detector
 * finds no strokes (bad data / config mismatch) so the editor still boots.
 *
 * Per-drill [CanonicalStrokeBuilder.Config] overrides are looked up by
 * drillType and fall back to sensible universal defaults. Tune a drill by
 * adding an entry to [DRILL_CONFIGS] rather than editing the core algorithm.
 */
object CanonicalStrokeLoader {

    data class Result(
        val frames: List<PoseFrame>,
        val intervalMs: Long,
        val strokeCount: Int,
        val meanStrokeLength: Int,
        val selectedStrokeIndex: Int?,
        val motionAmplitude: Float
    )

    fun loadForehandDrive(context: Context): Result = load(context, "forehand_drive")

    fun load(
        context: Context,
        drillType: String,
        configOverride: CanonicalStrokeBuilder.Config? = null
    ): Result {
        val loaded = AssetPoseFrameLoader.load(context, "fixtures/$drillType.json")
        val detection = JsonStrokeDetector().detect(loaded.frames)
        if (detection.strokes.isEmpty()) {
            return Result(
                frames = loaded.frames,
                intervalMs = loaded.intervalMs,
                strokeCount = 0,
                meanStrokeLength = loaded.frames.size,
                selectedStrokeIndex = null,
                motionAmplitude = 0f
            )
        }

        val config = configOverride ?: DRILL_CONFIGS[drillType] ?: DEFAULT_CONFIG
        val built = CanonicalStrokeBuilder.build(
            allFrames = loaded.frames,
            strokes = detection.strokes,
            intervalMs = loaded.intervalMs,
            config = config
        )
        if (built.frames.isEmpty()) {
            return Result(
                frames = loaded.frames,
                intervalMs = loaded.intervalMs,
                strokeCount = detection.strokes.size,
                meanStrokeLength = loaded.frames.size,
                selectedStrokeIndex = null,
                motionAmplitude = 0f
            )
        }
        return Result(
            frames = built.frames,
            intervalMs = built.intervalMs,
            strokeCount = built.strokeCount,
            meanStrokeLength = built.frames.size,
            selectedStrokeIndex = built.selectedStrokeIndex,
            motionAmplitude = built.motionAmplitude
        )
    }

    private val DEFAULT_CONFIG = CanonicalStrokeBuilder.Config(
        method = CanonicalStrokeBuilder.Method.BEST_REP,
        phaseAlign = true,
        smoothingRadius = 1,
        loopBlend = true,
        trimLeadingFrames = 0
    )

    private val DRILL_CONFIGS: Map<String, CanonicalStrokeBuilder.Config> = mapOf(
        // Short fixture (~2 reps); trim leading ready stance to match the
        // old-pipeline visual output.
        "forehand_drive" to DEFAULT_CONFIG.copy(trimLeadingFrames = 5),
        // Longer fixture (~10+ reps); pick the rep closest to the centroid
        // and phase-align so timing jitter across reps doesn't wash out motion.
        "forehand_andrii" to DEFAULT_CONFIG.copy(
            method = CanonicalStrokeBuilder.Method.BEST_REP,
            phaseAlign = true
        )
    )
}
