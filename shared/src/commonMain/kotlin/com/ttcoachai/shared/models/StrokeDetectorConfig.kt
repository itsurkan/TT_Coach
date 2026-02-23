package com.ttcoachai.shared.models

/**
 * Configuration for stroke detection thresholds
 */
data class StrokeDetectorConfig(
    val landmarkIndex: Int = 16,              // Right wrist (16) or Left wrist (15)
    val trackingAxis: TrackingAxis = TrackingAxis.X,
    val backswingThreshold: Float = 0.45f,    // Below = backswing detected
    val forwardPeakThreshold: Float = 0.60f,  // Above = peak reached
    val readyPositionThreshold: Float = 0.52f, // Neutral position
    val forwardVelocityThreshold: Float = 0.08f, // Per-frame velocity to detect forward
    val returnVelocityThreshold: Float = -0.05f, // Negative velocity for return
    val minBackswingDepth: Float = 0.08f,     // Minimum movement back
    val minForwardExtension: Float = 0.15f,   // Minimum forward movement
    val minStrokeFrames: Int = 3,             // 300ms minimum at 100ms interval
    val maxStrokeFrames: Int = 20,            // 2s timeout
    val smoothingWindow: Int = 3,             // Moving average frames
    val invertDirection: Boolean = false       // For backhand (left-to-right)
) {
    companion object {
        /** Default config for forehand drive */
        val FOREHAND = StrokeDetectorConfig(
            forwardVelocityThreshold = 0.075f
        )

        /** Config for backhand - inverted direction, left wrist */
        val BACKHAND = StrokeDetectorConfig(
            landmarkIndex = 15,  // Left wrist
            invertDirection = true
        )
    }
}
