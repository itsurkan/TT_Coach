package com.ttcoachai.shared.drill.movements

import com.ttcoachai.shared.drill.CoreMessageTemplates
import com.ttcoachai.shared.drill.CoreMetricSpecs
import com.ttcoachai.shared.drill.LocomotionFilter
import com.ttcoachai.shared.drill.MovementDefinition
import com.ttcoachai.shared.drill.RepValidationConfig

/**
 * "Накат справа General" (EN: "Forehand Drive General") — the movement-tolerant
 * general-practice profile for the forehand drive (docs/superpowers/specs/
 * 2026-07-02-generic-movement-pipeline-design.md). Same [MovementDetector]
 * tuning and the same five core in-plane metrics/messages as [ForehandDrive] —
 * only [RepValidationConfig.hipTravelMaxTorso] differs, widened from
 * [LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO] (0.4 torso-lengths, tuned for a
 * fixed/structured drill where the player stays planted) to
 * [MOVEMENT_TOLERANT_HIP_TRAVEL] (0.8 torso-lengths), so the small steps a
 * player takes between shots during free/general practice don't silence
 * feedback by tripping [LocomotionFilter] as if they were walking away.
 *
 * The direction gate ([RepValidationConfig.directionGate]) and banding
 * ([RepValidationConfig.banding]) stay ON: recovery swings and junk speed
 * peaks must never reach feedback just because the practice is unstructured —
 * only the locomotion tolerance changes.
 *
 * Baseline-agnostic by design: this definition carries no baseline of its own.
 * The app layer maps `drillType = "forehand_drive_general"` onto the existing
 * `forehand_drive` [com.ttcoachai.shared.models.PersonalBaseline] — general
 * practice is calibrated against the same personal reference angles as the
 * structured drill, not a separate calibration pass.
 */
object ForehandDriveGeneral {

    /** Widened locomotion gate (torso-lengths) — see class doc for rationale. */
    const val MOVEMENT_TOLERANT_HIP_TRAVEL = 0.8f

    val DEFINITION = MovementDefinition(
        id = "forehand_drive_general",
        metrics = CoreMetricSpecs.ALL,
        messages = CoreMessageTemplates.TEMPLATES,
        repValidation = RepValidationConfig(hipTravelMaxTorso = MOVEMENT_TOLERANT_HIP_TRAVEL)
    )
}
