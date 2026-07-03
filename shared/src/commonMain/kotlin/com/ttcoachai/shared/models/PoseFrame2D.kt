package com.ttcoachai.shared.models

/** One video frame of 2D keypoints. Empty [keypoints] = no person detected. */
data class PoseFrame2D(
    val frameIndex: Int,
    val timestampMs: Long,
    val keypoints: List<Keypoint2D>
)
