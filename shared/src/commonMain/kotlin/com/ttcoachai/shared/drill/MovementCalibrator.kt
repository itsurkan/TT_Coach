package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.BaselineDeriver
import com.ttcoachai.shared.analysis.CameraAngleEstimator
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PersonalBaseline
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.ViewGeometry
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Generic 2D pivot calibration (docs/superpowers/specs/
 * 2026-07-02-generic-movement-pipeline-design.md): detect reps in a calibration
 * recording for the given [definition], extract per-rep metrics at each detected
 * peak, and derive a PersonalBaseline via the 003 path (BaselineDeriver — design
 * decision 2: calibrate, don't re-teach). [DrillCalibrator.calibrate] is now a thin
 * delegating wrapper around this object with [com.ttcoachai.shared.drill.movements.ForehandDrive.DEFINITION].
 *
 * Pipeline: [MovementRepPipeline] (detect → ForwardStrokeFilter → RepFilter →
 * locomotion gate, per [definition]'s [RepValidationConfig]) → per-rep yaw gate →
 * per-rep metrics → deriveFromMetrics.
 *
 * Camera yaw is resolved PER REP (the player moves their feet): [cameraYawDeg]
 * override if given, else CameraAngleEstimator.estimateYawForStroke. Reps beyond
 * [maxCameraYawDeg] — or whose yaw is UNMEASURABLE (estimator returned null: no
 * scored shoulders+hips in the pre-stroke or stroke window; an unverifiable rep
 * must not enter a baseline) — are excluded; [DrillCalibrator.CameraPlacementException]
 * fires only when placement exclusions are what drops the count below [minRepCount]
 * (if even the unfiltered stroke count was short, deriveFromMetrics reports
 * "Insufficient valid reps" instead) — a baseline built from a badly placed camera
 * would poison every later feedback session.
 */
object MovementCalibrator {

    fun calibrate(
        sequence: PoseSequence2D,
        definition: MovementDefinition,
        createdAtMs: Long,
        handedness: Handedness = Handedness.RIGHT,
        minRepCount: Int = 10,
        outlierSigmaThreshold: Double = 2.0,
        pipeline: MovementRepPipeline = MovementRepPipeline(definition),
        /** Explicit camera-yaw override applied to all reps; null → per-rep auto-estimate. */
        cameraYawDeg: Float? = null,
        maxCameraYawDeg: Float = DrillCalibrator.DEFAULT_MAX_CAMERA_YAW_DEG
    ): PersonalBaseline {
        require(maxCameraYawDeg <= ViewGeometry.MAX_YAW_DEG) {
            "maxCameraYawDeg must be <= ViewGeometry.MAX_YAW_DEG (${ViewGeometry.MAX_YAW_DEG}°), " +
                "got $maxCameraYawDeg — the 1/cos xScale correction is undefined beyond it"
        }
        // Detection on plain aspect: peak finding tolerates uncorrected ≤30° yaw
        // (≤15% speed-magnitude error); metrics below use per-rep corrected xScale.
        val strokes = pipeline.detectReps(sequence, handedness)

        val strokesWithYaw = strokes.map { stroke ->
            stroke to (cameraYawDeg
                ?: CameraAngleEstimator.estimateYawForStroke(
                    sequence.frames, stroke, sequence.aspectRatio, sequence.intervalMs
                ))
        }
        // Null yaw = placement unverifiable → excluded exactly like over-yaw
        // (conservatism: an unverifiable rep must not enter a baseline).
        val placed = strokesWithYaw.mapNotNull { (stroke, yaw) ->
            if (yaw != null && abs(yaw) <= maxCameraYawDeg) stroke to yaw else null
        }
        if (placed.size < minRepCount && placed.size < strokes.size && strokes.size >= minRepCount) {
            throw DrillCalibrator.CameraPlacementException(
                "Only ${placed.size} of ${strokes.size} reps had the camera within " +
                    "${maxCameraYawDeg.roundToInt()}° of the side view (need $minRepCount) — " +
                    "reposition the camera and re-record calibration"
            )
        }

        val repMetrics = placed.map { (stroke, yaw) ->
            val view = ViewGeometry(sequence.aspectRatio, yaw)
            MovementMetrics.extractAtPeak(
                sequence.frames, stroke.peakFrame, handedness, view.xScale, sequence.intervalMs, definition.metrics
            ) + DerivedMetrics.merge(sequence.frames, stroke, handedness, view.xScale, sequence.intervalMs)
        }
        val repPhases = placed.map { (stroke, _) ->
            mapOf(
                // Backswing segmentation is deferred (needs direction-reversal analysis);
                // forward-swing and total cover the rhythm rules for v1.
                BaselineDeriver.PHASE_FORWARD_SWING_MS to
                    (stroke.peakFrame - stroke.startFrame) * sequence.intervalMs.toDouble(),
                BaselineDeriver.PHASE_STROKE_TOTAL_MS to
                    (stroke.endFrame - stroke.startFrame) * sequence.intervalMs.toDouble()
            )
        }
        return BaselineDeriver.deriveFromMetrics(
            repMetrics = repMetrics,
            repPhaseDurations = repPhases,
            drillType = definition.id,
            createdAtMs = createdAtMs,
            drillerHandedness = handedness.baselineString,
            minRepCount = minRepCount,
            outlierSigmaThreshold = outlierSigmaThreshold
        )
    }
}
