package com.ttcoachai.shared.models

data class FeedbackItem(
    val message: String,
    val type: CorrectionType,
    val isPositive: Boolean = false,
    val strokeLandmarks: List<List<Landmark3D>> = emptyList()
)
