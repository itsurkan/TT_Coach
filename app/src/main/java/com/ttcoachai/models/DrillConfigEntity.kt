package com.ttcoachai.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted per-drill shape overrides authored in the dev parameter editor
 * (Phase 7). Stored as a single row per `drillType`; columns are the slider
 * values in natural units (degrees / normalized-coord offset).
 *
 * Semantics today: read by [BaselinePreviewActivity] to restore last-edited
 * values on reopen, and dumped to the export JSON.
 *
 * Semantics later: once the Stage 1 Phase 2 rule evaluator lands, these
 * deltas apply as overrides on top of the derived [PersonalBaseline] so
 * drill feedback compares against the coach-tuned pose rather than the
 * player's raw calibration. That path is wired off the same columns — no
 * migration needed.
 */
@Entity(tableName = "drill_configs")
data class DrillConfigEntity(
    @PrimaryKey val drillType: String,
    val bodyRotationDeltaDeg: Float = 0f,
    val torsoTiltDeltaDeg: Float = 0f,
    val rightShoulderAngleDeltaDeg: Float = 0f,
    val rightElbowAngleDeltaDeg: Float = 0f,
    val rightElbowXOffset: Float = 0f,
    val rightWristAngleDeltaDeg: Float = 0f,
    val kneeBendDeltaDeg: Float = 0f,
    val updatedAtMs: Long = 0L
)
