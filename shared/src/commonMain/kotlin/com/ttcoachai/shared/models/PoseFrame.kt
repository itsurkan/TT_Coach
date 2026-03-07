package com.ttcoachai.shared.models

data class PoseFrame(
    val frameIndex: Int,
    val timestampMs: Long,
    val landmarks: List<Landmark3D>
)
