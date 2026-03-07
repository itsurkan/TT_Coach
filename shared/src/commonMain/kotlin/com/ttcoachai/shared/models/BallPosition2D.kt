package com.ttcoachai.shared.models

data class BallPosition2D(
    val x: Float,
    val y: Float,
    val frameIndex: Int,
    val timestampMs: Long,
    val source: DataSource
)
