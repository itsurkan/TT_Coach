package com.ttcoachai.shared.models

/**
 * Result of stroke detection analysis
 */
data class StrokeDetectionResult(
    val strokes: List<DetectedStroke>,
    val framePhases: List<FramePhaseInfo>,
    val totalFrames: Int
) {
    /** Get stroke that contains a specific frame */
    fun getStrokeForFrame(frameIndex: Int): DetectedStroke? {
        return strokes.find { it.containsFrame(frameIndex) }
    }

    /** Get phase for a specific frame */
    fun getPhaseForFrame(frameIndex: Int): StrokePhase {
        return framePhases.getOrNull(frameIndex)?.phase ?: StrokePhase.READY
    }

    /** Get stroke info for a specific frame */
    fun getFrameInfo(frameIndex: Int): FramePhaseInfo? {
        return framePhases.getOrNull(frameIndex)
    }
}
