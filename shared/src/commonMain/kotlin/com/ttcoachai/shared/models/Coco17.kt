package com.ttcoachai.shared.models

/** COCO-17 keypoint indices (docs/pose_json_schema_v2.md). Valid for Halpe26 indices 0–16 too. */
object Coco17 {
    const val NOSE = 0
    const val LEFT_EYE = 1
    const val RIGHT_EYE = 2
    const val LEFT_EAR = 3
    const val RIGHT_EAR = 4
    const val LEFT_SHOULDER = 5
    const val RIGHT_SHOULDER = 6
    const val LEFT_ELBOW = 7
    const val RIGHT_ELBOW = 8
    const val LEFT_WRIST = 9
    const val RIGHT_WRIST = 10
    const val LEFT_HIP = 11
    const val RIGHT_HIP = 12
    const val LEFT_KNEE = 13
    const val RIGHT_KNEE = 14
    const val LEFT_ANKLE = 15
    const val RIGHT_ANKLE = 16

    fun shoulder(h: Handedness) = if (h == Handedness.RIGHT) RIGHT_SHOULDER else LEFT_SHOULDER
    fun elbow(h: Handedness) = if (h == Handedness.RIGHT) RIGHT_ELBOW else LEFT_ELBOW
    fun wrist(h: Handedness) = if (h == Handedness.RIGHT) RIGHT_WRIST else LEFT_WRIST
    fun hip(h: Handedness) = if (h == Handedness.RIGHT) RIGHT_HIP else LEFT_HIP
    fun knee(h: Handedness) = if (h == Handedness.RIGHT) RIGHT_KNEE else LEFT_KNEE
    fun ankle(h: Handedness) = if (h == Handedness.RIGHT) RIGHT_ANKLE else LEFT_ANKLE
}
