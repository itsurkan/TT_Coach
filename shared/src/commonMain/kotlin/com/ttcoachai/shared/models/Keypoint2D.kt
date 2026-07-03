package com.ttcoachai.shared.models

/** Single COCO/Halpe keypoint in normalized image coords (x / videoWidth, y / videoHeight). */
data class Keypoint2D(
    val x: Float,
    val y: Float,
    val score: Float
)
