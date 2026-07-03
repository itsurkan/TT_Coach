package com.ttcoachai.shared.drill.movements

import com.ttcoachai.shared.drill.CoreMessageTemplates
import com.ttcoachai.shared.drill.CoreMetricSpecs
import com.ttcoachai.shared.drill.MovementDefinition

/**
 * Backhand-drive movement definition — proves a new movement is pure data
 * (docs/superpowers/specs/2026-07-02-generic-movement-pipeline-design.md): same
 * detection tuning, rep-validation flags, core in-plane metrics and message set as
 * [ForehandDrive] for now, differing only in [MovementDefinition.id]. Detection
 * tuning, sanity bounds and phrasing are expected to diverge once real backhand
 * footage is available to tune against (today's constants are all forehand-tuned).
 */
object BackhandDrive {
    val DEFINITION = MovementDefinition(
        id = "backhand_drive",
        metrics = CoreMetricSpecs.ALL,
        messages = CoreMessageTemplates.TEMPLATES
    )
}
