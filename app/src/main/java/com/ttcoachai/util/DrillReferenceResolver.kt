package com.ttcoachai.util

/**
 * Resolves the per-metric reference bands the RTM live path should use, given what the
 * launching intent carried (docs/superpowers/specs/2026-07-27-no-calibration-shipped-baseline-design.md
 * §5). [referenceTypeExtraPresent] is false only when the launching screen predates/doesn't
 * know about this feature (e.g. SessionReviewFragment's "Train Again", or an exercise id with
 * no backing CustomDrillEntity) — in that case [shippedDefaultBands] is used so the drill
 * trains immediately instead of silently producing zero cues. When the extra IS present,
 * [parsedBands] is used as-is: a band a player left blank in the editor legitimately means
 * "silent for this metric," not "fall back to the shipped default."
 */
object DrillReferenceResolver {
    fun resolveMetricBands(
        referenceTypeExtraPresent: Boolean,
        parsedBands: Map<String, ClosedRange<Double>>,
        shippedDefaultBands: Map<String, ClosedRange<Double>>
    ): Map<String, ClosedRange<Double>> =
        if (referenceTypeExtraPresent) parsedBands else shippedDefaultBands
}
