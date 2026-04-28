package com.ttcoachai.shared.models

/**
 * Summary statistics for a single metric across the calibration sample.
 *
 * `std` is the sample standard deviation (Bessel's correction, divide by n-1).
 * For `sampleCount <= 1`, `std` is defined as 0.0.
 */
data class MetricStats(
    val mean: Double,
    val std: Double,
    val min: Double,
    val max: Double,
    val sampleCount: Int
)
