package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.BaselineDeriver
import com.ttcoachai.shared.analysis.CameraAngleEstimator
import com.ttcoachai.shared.detection.StrokeDetector2D
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PersonalBaseline
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.ViewGeometry
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 2D pivot calibration: detect reps in a calibration recording, extract per-rep
 * metrics at each wrist-speed peak, and derive a PersonalBaseline via the 003 path
 * (BaselineDeriver — design decision 2: calibrate, don't re-teach).
 *
 * Pipeline: detect → ForwardStrokeFilter (recovery swings out) → RepFilter (junk
 * peaks out) → per-rep yaw gate → per-rep metrics → deriveFromMetrics.
 *
 * Camera yaw is resolved PER REP (the player moves their feet): [cameraYawDeg]
 * override if given, else CameraAngleEstimator.estimateYawForStroke. Reps beyond
 * [maxCameraYawDeg] are excluded; [CameraPlacementException] fires only when those
 * exclusions leave fewer than [minRepCount] reps — a baseline built from a badly
 * placed camera would poison every later feedback session.
 */
object DrillCalibrator {

    /** Camera too far off the required side view; reposition and re-record. */
    class CameraPlacementException(message: String) : IllegalStateException(message)

    /** First-order 1/cos correction is trustworthy up to roughly here. */
    const val DEFAULT_MAX_CAMERA_YAW_DEG = 30f

    fun calibrate(
        sequence: PoseSequence2D,
        drillType: String,
        createdAtMs: Long,
        handedness: Handedness = Handedness.RIGHT,
        minRepCount: Int = 10,
        outlierSigmaThreshold: Double = 2.0,
        detector: StrokeDetector2D = StrokeDetector2D(),
        /** Explicit camera-yaw override applied to all reps; null → per-rep auto-estimate. */
        cameraYawDeg: Float? = null,
        maxCameraYawDeg: Float = DEFAULT_MAX_CAMERA_YAW_DEG
    ): PersonalBaseline {
        // Detection on plain aspect: peak finding tolerates uncorrected ≤30° yaw
        // (≤15% speed-magnitude error); metrics below use per-rep corrected xScale.
        val detected = detector.detect(sequence.frames, handedness, sequence.aspectRatio, sequence.intervalMs)
        val strokes = RepFilter.filter(
            ForwardStrokeFilter.filter(detected, sequence.frames, handedness)
        )

        val strokesWithYaw = strokes.map { stroke ->
            val yaw = cameraYawDeg
                ?: CameraAngleEstimator.estimateYawForStroke(
                    sequence.frames, stroke, sequence.aspectRatio, sequence.intervalMs
                )
                ?: 0f
            stroke to yaw
        }
        val placed = strokesWithYaw.filter { (_, yaw) -> abs(yaw) <= maxCameraYawDeg }
        if (placed.size < minRepCount && placed.size < strokes.size) {
            throw CameraPlacementException(
                "Only ${placed.size} of ${strokes.size} reps had the camera within " +
                    "${maxCameraYawDeg.roundToInt()}° of the side view (need $minRepCount) — " +
                    "reposition the camera and re-record calibration"
            )
        }

        val repMetrics = placed.map { (stroke, yaw) ->
            val view = ViewGeometry(sequence.aspectRatio, yaw)
            DrillMetrics.extractAtPeak(
                sequence.frames, stroke.peakFrame, handedness, view.xScale, sequence.intervalMs
            )
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
            drillType = drillType,
            createdAtMs = createdAtMs,
            drillerHandedness = handedness.baselineString,
            minRepCount = minRepCount,
            outlierSigmaThreshold = outlierSigmaThreshold
        )
    }
}
