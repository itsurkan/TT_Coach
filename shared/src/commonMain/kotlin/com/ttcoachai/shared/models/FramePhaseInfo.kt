package com.ttcoachai.shared.models

/**
 * Phase information for a single frame
 */
data class FramePhaseInfo(
    val frameIndex: Int,
    val phase: StrokePhase,
    val strokeIndex: Int?,
    val phaseProgress: Float  // 0.0-1.0 progress within phase
)
