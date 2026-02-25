package com.ttcoachai.shared.models

// T019 — Parabolic trajectory model coefficients
// x(t) = ax + bx*t          (linear: constant horizontal velocity)
// y(t) = ay + by*t + cy*t²  (quadratic: gravity-influenced vertical)
data class ParabolicFit(
    val ax: Double,  // X-intercept
    val bx: Double,  // X-slope (horizontal velocity, px/frame)
    val ay: Double,  // Y-intercept
    val by: Double,  // Y-slope (initial vertical velocity)
    val cy: Double   // Y-curvature (gravity effect in screen coords)
)

// T020 — Contact event classification
enum class ContactType {
    BOUNCE,           // Vertical velocity reversal near table surface
    PADDLE_CONTACT,   // Speed ratio > 1.8x before/after
    NET_CLIP,         // Direction angle > 30° without speed spike
    UNKNOWN_CONTACT   // Direction angle > 30°, unclassified
}

data class ContactEvent(
    val type: ContactType,
    val frameIndex: Int,
    val timestampMs: Long,
    val position: Pair<Float, Float>,  // Normalised (x, y) at contact point
    val velocityBefore: Float = 0f,    // Ball speed before contact (px/frame)
    val velocityAfter: Float = 0f,     // Ball speed after contact (px/frame)
    val confidence: Float = 0f         // Detection confidence 0-1
)

// T021 — Continuous arc of ball flight between two direction-change events
data class TrajectorySegment(
    val segmentIndex: Int,
    val startFrameIndex: Int,
    val endFrameIndex: Int,
    val detections: List<BallDetection>,
    val fittedPositions: List<BallPosition2D> = emptyList(),
    val contactBefore: ContactEvent? = null,
    val contactAfter: ContactEvent? = null,
    val fitCoefficients: ParabolicFit,
    val fitRmsError: Double = 0.0,
    val segmentDurationMs: Long = 0L
)
