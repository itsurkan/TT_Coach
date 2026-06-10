package com.ttcoachai.shared.models

/**
 * One detected stroke from the wrist-speed signal. All values are frame indices
 * into the source frame list; durations are derived by the caller via intervalMs.
 */
data class Stroke2D(
    val strokeIndex: Int,
    val startFrame: Int,
    val peakFrame: Int,
    val endFrame: Int,
    /** Smoothed wrist speed at the peak, in torso-lengths per second (L-01). */
    val peakSpeed: Float
)
