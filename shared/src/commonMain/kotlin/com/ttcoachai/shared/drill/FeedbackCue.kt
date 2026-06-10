package com.ttcoachai.shared.drill

enum class CueDirection { TOO_HIGH, TOO_LOW }

/** One actionable deviation of a rep metric from the personal baseline. */
data class FeedbackCue(
    val metricKey: String,
    val direction: CueDirection,
    /** Signed degrees (or metric units) vs the baseline mean. */
    val deltaFromMean: Double,
    /** |delta| / σ — used to pick the single most important cue per cadence window. */
    val severity: Double,
    val precision: MetricPrecision
)
