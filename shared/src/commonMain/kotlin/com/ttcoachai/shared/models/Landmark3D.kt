package com.ttcoachai.shared.models

data class Landmark3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float = 0f,
    val presence: Float = 0f
)
