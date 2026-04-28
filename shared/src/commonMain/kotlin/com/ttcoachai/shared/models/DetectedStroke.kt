package com.ttcoachai.shared.models

/**
 * Detected stroke with phase boundaries and metrics
 */
data class DetectedStroke(
    val strokeIndex: Int,

    // Phase boundaries (frame indices)
    val preparationStartFrame: Int,
    val preparationEndFrame: Int,
    val forwardStartFrame: Int,
    val contactFrame: Int,
    val forwardEndFrame: Int,
    val returnStartFrame: Int,
    val returnEndFrame: Int,

    // Metrics
    val backswingMinValue: Float,
    val forwardPeakValue: Float,
    val peakVelocity: Float,
    val strokeDurationMs: Long,
    val forwardSwingDurationMs: Long,

    // Quality indicators
    val isComplete: Boolean
) {
    /** Check if a frame index is within this stroke */
    fun containsFrame(frameIndex: Int): Boolean {
        return frameIndex in preparationStartFrame..returnEndFrame
    }

    /** Get the phase for a specific frame */
    fun getPhaseForFrame(frameIndex: Int): StrokePhase {
        return when {
            frameIndex < preparationStartFrame -> StrokePhase.READY
            frameIndex <= preparationEndFrame -> StrokePhase.BACKSWING
            frameIndex <= forwardEndFrame -> {
                if (frameIndex == contactFrame) StrokePhase.CONTACT
                else StrokePhase.FORWARD_SWING
            }
            frameIndex <= returnEndFrame -> StrokePhase.FOLLOW_THROUGH
            else -> StrokePhase.RECOVERY
        }
    }
}
