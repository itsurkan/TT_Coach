package com.ttcoachai.shared.models

/** Full schema-v2 pose export: header metadata + frames. */
data class PoseSequence2D(
    val topology: Topology,
    val model: String,
    val videoName: String,
    val intervalMs: Long,
    val totalFrames: Int,
    val videoDurationMs: Long,
    val videoWidth: Int,
    val videoHeight: Int,
    val frames: List<PoseFrame2D>
) {
    /** x-deltas must be multiplied by this before any angle/distance math. */
    val aspectRatio: Float get() = videoWidth.toFloat() / videoHeight.toFloat()
}
