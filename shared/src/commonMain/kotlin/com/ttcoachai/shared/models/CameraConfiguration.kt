package com.ttcoachai.shared.models

data class CameraConfiguration(
    val exposureTimeNs: Long = 2_000_000L,
    val isoSensitivity: Int = 800,
    val targetFps: Int = 30,
    val isAutoExposure: Boolean = false,
    val luminanceEma: Float = 120f
)
