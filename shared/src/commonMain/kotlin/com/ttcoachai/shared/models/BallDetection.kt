package com.ttcoachai.shared.models

enum class BallDetectionStatus {
    DETECTED,
    NOT_DETECTED,
    OUT_OF_FRAME
}

data class BallDetection(
    val x: Float,
    val y: Float,
    val confidence: Float = 0f,
    val radiusPx: Float = 0f,
    val frameIndex: Int,
    val timestampMs: Long,
    val status: BallDetectionStatus = BallDetectionStatus.DETECTED
)
