package com.ttcoachai.pose

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Keypoint2D

/** Pure JVM overlay projection: normalized COCO-17 keypoints to pixel coords. */
object Coco17OverlayScaling {
    data class OverlayPoint(val x: Float, val y: Float)

    const val SCORE_THRESHOLD = 0.3f

    /**
     * Project a normalized keypoint to view pixel coordinates.
     * Keypoints are normalized [0,1] per-axis (x / videoWidth, y / videoHeight).
     * Returns null if score < SCORE_THRESHOLD (not drawn).
     *
     * Assumption: overlay covers the same region as the analysis frame
     * (letterbox/crop correction for mismatched PreviewView aspect is deferred).
     */
    fun project(kp: Keypoint2D, viewWidth: Int, viewHeight: Int): OverlayPoint? {
        if (kp.score < SCORE_THRESHOLD) return null
        return OverlayPoint(kp.x * viewWidth, kp.y * viewHeight)
    }

    /** Standard COCO-17 skeleton edges (limbs + torso + head). */
    val BONES: List<Pair<Int, Int>> = listOf(
        // Head
        Coco17.NOSE to Coco17.LEFT_EYE,
        Coco17.NOSE to Coco17.RIGHT_EYE,
        Coco17.LEFT_EYE to Coco17.LEFT_EAR,
        Coco17.RIGHT_EYE to Coco17.RIGHT_EAR,
        // Left arm
        Coco17.LEFT_SHOULDER to Coco17.LEFT_ELBOW,
        Coco17.LEFT_ELBOW to Coco17.LEFT_WRIST,
        // Right arm
        Coco17.RIGHT_SHOULDER to Coco17.RIGHT_ELBOW,
        Coco17.RIGHT_ELBOW to Coco17.RIGHT_WRIST,
        // Torso
        Coco17.LEFT_SHOULDER to Coco17.RIGHT_SHOULDER,
        Coco17.LEFT_SHOULDER to Coco17.LEFT_HIP,
        Coco17.RIGHT_SHOULDER to Coco17.RIGHT_HIP,
        Coco17.LEFT_HIP to Coco17.RIGHT_HIP,
        // Left leg
        Coco17.LEFT_HIP to Coco17.LEFT_KNEE,
        Coco17.LEFT_KNEE to Coco17.LEFT_ANKLE,
        // Right leg
        Coco17.RIGHT_HIP to Coco17.RIGHT_KNEE,
        Coco17.RIGHT_KNEE to Coco17.RIGHT_ANKLE
    )
}
