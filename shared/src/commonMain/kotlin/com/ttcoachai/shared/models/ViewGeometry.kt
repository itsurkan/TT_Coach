package com.ttcoachai.shared.models

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * Geometry of the recording viewpoint. [xScale] is THE single factor every
 * geometric function applies to x-deltas: it combines per-axis normalization
 * (schema-v2 normalizes x by width, y by height → aspect ratio) with first-order
 * camera-yaw foreshortening correction (1/cos) for side-view drills.
 *
 * [cameraYawDeg] is the camera's deviation from a perfect side view (sign
 * irrelevant — cos is even). Correction quality degrades with yaw: drills that
 * require the side view gate feedback above ~30° (drill policy, see
 * DrillCalibrator.DEFAULT_MAX_CAMERA_YAW_DEG); MAX_YAW_DEG is the hard math limit.
 */
data class ViewGeometry(
    val aspectRatio: Float,
    val cameraYawDeg: Float = 0f
) {
    init {
        require(abs(cameraYawDeg) <= MAX_YAW_DEG) {
            "cameraYawDeg must be within ±$MAX_YAW_DEG°, got $cameraYawDeg"
        }
    }

    val xScale: Float = aspectRatio / cos(cameraYawDeg * DEG_TO_RAD)

    companion object {
        const val MAX_YAW_DEG = 60f
        private const val DEG_TO_RAD = (PI / 180.0).toFloat()
    }
}
