package com.ttcoachai.shared.drill

import com.ttcoachai.shared.detection.MovementDetector
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.Stroke2D

/**
 * The single owner of the rep-detection pipeline order (docs/superpowers/specs/
 * 2026-07-02-generic-movement-pipeline-design.md): detect → (directionGate?)
 * ForwardStrokeFilter → (banding?) RepFilter → locomotion gate. This order was
 * previously copy-pasted identically in [DrillCalibrator] and
 * [ForehandDriveDrillAnalyzer] — that duplication was the bug risk this class
 * removes; both now delegate here.
 *
 * REORDERING THIS SILENTLY CORRUPTS BASELINES: ForwardStrokeFilter must run before
 * RepFilter (recovery swings share strokes' speed/duration class, so RepFilter's
 * banding cannot separate them on its own — direction must be resolved first), and
 * the locomotion gate must run last (it inspects hip-mid travel of whatever strokes
 * survive the first two stages).
 */
class MovementRepPipeline(
    private val definition: MovementDefinition,
    private val detector: MovementDetector = MovementDetector(definition.detection)
) {

    /**
     * Detection runs on plain aspect (xScale = [PoseSequence2D.aspectRatio], yaw 0):
     * peak finding tolerates uncorrected ≤30° yaw (≤15% speed-magnitude error), and
     * per-rep corrected xScale is applied later, only to metric extraction — the
     * same rationale as the original DrillCalibrator/ForehandDriveDrillAnalyzer
     * callers.
     */
    fun detectReps(sequence: PoseSequence2D, handedness: Handedness): List<Stroke2D> {
        val detected = detector.detect(sequence.frames, handedness, sequence.aspectRatio, sequence.intervalMs)

        val directed = if (definition.repValidation.directionGate) {
            ForwardStrokeFilter.filter(detected, sequence.frames, handedness)
        } else {
            detected
        }

        val banded = if (definition.repValidation.banding) {
            RepFilter.filter(directed)
        } else {
            directed
        }

        return LocomotionFilter.filterStationary(
            banded, sequence.frames, sequence.aspectRatio, definition.repValidation.hipTravelMaxTorso
        )
    }
}
