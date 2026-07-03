package com.ttcoachai.shared.drill

/**
 * Which candidate filters a movement's rep pipeline applies (docs/superpowers/specs/
 * 2026-07-02-generic-movement-pipeline-design.md). Flags, not strategies — add a
 * strategy hook only when a movement actually needs custom validation logic beyond
 * toggling these stages (deferred until that need is real).
 */
data class RepValidationConfig(
    /** ForwardStrokeFilter: drops recovery swings (same speed/duration class as strokes). */
    val directionGate: Boolean = true,
    /** RepFilter: drops peaks whose speed/duration fall outside the session's dominant cluster. */
    val banding: Boolean = true,
    /** LocomotionFilter: drop reps whose hip-mid travels more than this many
     *  torso-lengths (walking). ≤ 0 disables the locomotion gate. */
    val hipTravelMaxTorso: Float = LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO
)
