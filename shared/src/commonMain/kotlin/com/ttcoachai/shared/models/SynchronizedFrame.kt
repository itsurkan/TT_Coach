package com.ttcoachai.shared.models

/**
 * Unified data point combining skeleton pose and ball position for a single frame.
 *
 * Both [pose] and [ball] are nullable — null means no data was captured for that stream
 * at this timestamp. Consumers should inspect [poseSource] and [ballSource] to understand
 * the provenance of each field:
 *  - [DataSource.DETECTED]     → directly measured in this frame
 *  - [DataSource.INTERPOLATED] → estimated from neighbouring frames
 *  - [DataSource.ABSENT]       → no data available (field is null)
 *
 * Source: Spec FR-007, FR-008, SC-004; data-model.md SynchronizedFrame entity.
 */
data class SynchronizedFrame(
    val frameIndex: Int,
    val timestampMs: Long,
    val pose: PoseFrame? = null,
    val ball: BallDetection? = null,
    val poseSource: DataSource = DataSource.ABSENT,
    val ballSource: DataSource = DataSource.ABSENT
)
