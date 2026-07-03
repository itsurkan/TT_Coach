package com.ttcoachai.shared.drill

import com.ttcoachai.shared.detection.StrokeDetector2D
import com.ttcoachai.shared.drill.movements.ForehandDrive
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PersonalBaseline
import com.ttcoachai.shared.models.PoseSequence2D

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
 * [maxCameraYawDeg] — or whose yaw is UNMEASURABLE (estimator returned null: no
 * scored shoulders+hips in the pre-stroke or stroke window; an unverifiable rep
 * must not enter a baseline) — are excluded; [CameraPlacementException] fires only when placement
 * exclusions are what drops the count below [minRepCount] (if even the unfiltered stroke
 * count was short, deriveFromMetrics reports "Insufficient valid reps" instead) — a
 * baseline built from a badly placed camera would poison every later feedback session.
 *
 * Thin delegating wrapper: the generalized calibration — any [MovementDefinition],
 * not just forehand drive — lives in [MovementCalibrator], driven by
 * [ForehandDrive.DEFINITION] (docs/superpowers/specs/
 * 2026-07-02-generic-movement-pipeline-design.md). [CameraPlacementException] and
 * [DEFAULT_MAX_CAMERA_YAW_DEG] stay here as the shared, movement-agnostic contract.
 * Kept for API compatibility; behavior for forehand drive is unchanged bit-for-bit.
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
        maxCameraYawDeg: Float = DEFAULT_MAX_CAMERA_YAW_DEG,
        /** Locomotion gate (L-30): drop reps whose hip-mid travels more than this many
         *  torso-lengths (walking). ≤ 0 disables. On by default so a walking step is not
         *  calibrated as a stroke. */
        hipTravelMaxTorso: Float = LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO
    ): PersonalBaseline {
        val definition = ForehandDrive.DEFINITION.copy(
            id = drillType,
            repValidation = ForehandDrive.DEFINITION.repValidation.copy(hipTravelMaxTorso = hipTravelMaxTorso)
        )
        return MovementCalibrator.calibrate(
            sequence = sequence,
            definition = definition,
            createdAtMs = createdAtMs,
            handedness = handedness,
            minRepCount = minRepCount,
            outlierSigmaThreshold = outlierSigmaThreshold,
            pipeline = MovementRepPipeline(definition, detector.asMovementDetector()),
            cameraYawDeg = cameraYawDeg,
            maxCameraYawDeg = maxCameraYawDeg
        )
    }

    /**
     * Non-throwing calibration entry point for iOS and other FFI contexts.
     * Wraps calibrate() and returns a sealed class result to prevent Kotlin
     * exceptions from crashing Swift bridge code.
     *
     * This is the preferred entry point for Phase 3 iOS; the desktop 003 path
     * continues using calibrate() directly.
     */
    fun calibrateChecked(
        sequence: PoseSequence2D,
        drillType: String,
        createdAtMs: Long,
        handedness: Handedness = Handedness.RIGHT,
        minRepCount: Int = 10,
        outlierSigmaThreshold: Double = 2.0,
        detector: StrokeDetector2D = StrokeDetector2D(),
        cameraYawDeg: Float? = null,
        maxCameraYawDeg: Float = DEFAULT_MAX_CAMERA_YAW_DEG
    ): CalibrationOutcome {
        return try {
            val baseline = calibrate(
                sequence = sequence,
                drillType = drillType,
                createdAtMs = createdAtMs,
                handedness = handedness,
                minRepCount = minRepCount,
                outlierSigmaThreshold = outlierSigmaThreshold,
                detector = detector,
                cameraYawDeg = cameraYawDeg,
                maxCameraYawDeg = maxCameraYawDeg
            )
            CalibrationOutcome.Success(baseline)
        } catch (e: CameraPlacementException) {
            CalibrationOutcome.PlacementError(e.message ?: "Camera placement invalid")
        } catch (e: Exception) {
            CalibrationOutcome.Failed("Calibration failed: ${e.message}")
        }
    }
}
