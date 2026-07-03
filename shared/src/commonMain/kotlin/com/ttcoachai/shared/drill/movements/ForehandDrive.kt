package com.ttcoachai.shared.drill.movements

import com.ttcoachai.shared.drill.CoreMessageTemplates
import com.ttcoachai.shared.drill.CoreMetricSpecs
import com.ttcoachai.shared.drill.MovementDefinition

/**
 * Phase 2 forehand-drive movement: reproduces the pre-generic pipeline bit-for-bit —
 * [MovementDefinition]'s defaults (wrist-signal detection tuning, all rep-validation
 * stages on, [MovementDefinition.repValidation]'s default hip-travel gate) plus the
 * five core in-plane metrics and the original UA+EN phrase set
 * (docs/superpowers/specs/2026-07-02-generic-movement-pipeline-design.md). This is
 * the object [MovementAnalyzer]/[MovementCalibrator] now drive, and what
 * [com.ttcoachai.shared.drill.ForehandDriveDrillAnalyzer] and
 * [com.ttcoachai.shared.drill.DrillCalibrator] delegate to for their forehand-drive
 * behavior.
 */
object ForehandDrive {
    val DEFINITION = MovementDefinition(
        id = "forehand_drive",
        metrics = CoreMetricSpecs.ALL,
        messages = CoreMessageTemplates.TEMPLATES
    )
}
