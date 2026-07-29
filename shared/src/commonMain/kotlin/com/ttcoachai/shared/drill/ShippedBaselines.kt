package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.BaselineRuleFactory
import com.ttcoachai.shared.models.MetricStats
import com.ttcoachai.shared.models.PersonalBaseline

/**
 * Curated, one-time-derived shipped baseline (docs/superpowers/specs/
 * 2026-07-27-no-calibration-shipped-baseline-design.md §1; derivation record:
 * docs/shipped-baseline-derivation.md). Two roles:
 *
 * (a) [defaultBands] seeds a new drill's editable per-metric reference bands
 *     (`CustomDrillEntity.perPhaseTargetsJson`) so a player never sees an empty editor or a
 *     hardcoded textbook figure.
 * (b) [FOREHAND_ANDRII] itself is the σ-carrier baseline `LiveDrillSession` uses in "standard"
 *     reference mode — so a qualitative-metric cue (`coil_ratio`, `stroke_speed`) ranks by
 *     Andrii's own variability instead of the crude
 *     [com.ttcoachai.shared.drill.DrillFeedbackEngine.DEFAULT_RANGE_SEVERITY_SCALE_DEGREES] fallback.
 *
 * LIMITATION (see docs/DESIGN_LIMITATIONS.md L-37, L-38): derived with the camera-yaw gate
 * consciously relaxed (`cameraYawDeg = 0f`) for this one-time editorial step — these bands
 * encode "match this recorded stroke as filmed," not a camera-agnostic universal norm; standard-
 * mode severity ranking likewise reflects Andrii's own consistency, not a player-agnostic scale.
 */
object ShippedBaselines {

    val FOREHAND_ANDRII: PersonalBaseline = PersonalBaseline(
        drillType = "forehand_drive",
        metricStats = mapOf(
            DrillMetrics.METRIC_ELBOW_ANGLE to MetricStats(mean = 64.53417587280273, std = 29.928821717007263, min = 33.66743850708008, max = 119.95394134521484, sampleCount = 12),
            DrillMetrics.METRIC_SHOULDER_ANGLE to MetricStats(mean = 37.94060389200846, std = 9.023364910517047, min = 23.295503616333008, max = 52.16059112548828, sampleCount = 12),
            DrillMetrics.METRIC_KNEE_BEND to MetricStats(mean = 174.72368621826172, std = 2.431677913758513, min = 169.59434509277344, max = 176.87957763671875, sampleCount = 12),
            DrillMetrics.METRIC_TORSO_LEAN to MetricStats(mean = 3.8143043319384256, std = 1.3922928139415498, min = 2.423185110092163, max = 6.360828638076782, sampleCount = 12),
            DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D to MetricStats(mean = 132.2988166809082, std = 44.34245701413729, min = 59.16209411621094, max = 169.4313201904297, sampleCount = 12),
            DrillMetrics.METRIC_STROKE_SPEED to MetricStats(mean = 9.967092275619507, std = 0.33454233928057225, min = 9.307697296142578, max = 10.531898498535156, sampleCount = 12),
            DrillMetrics.METRIC_COIL_RATIO to MetricStats(mean = 1.322065035502116, std = 0.4782544112683285, min = 0.7653630375862122, max = 2.3219175338745117, sampleCount = 12),
        ),
        phaseDurationsMs = mapOf(
            "forward_swing_ms" to MetricStats(mean = 320.1666666666667, std = 80.01117346213513, min = 221.0, max = 442.0, sampleCount = 12),
            "stroke_total_ms" to MetricStats(mean = 828.75, std = 230.27143074680762, min = 442.0, max = 1173.0, sampleCount = 12),
        ),
        repCount = 12,
        excludedRepIndices = listOf(0, 11, 12),
        qualityScore = 0.7412837894387136,
        createdAtMs = 1785249184303L,
        drillerHandedness = "right"
    )

    /**
     * `mean ± 2σ` per metric (rounded to 2 decimals — enough resolution for `coil_ratio`/
     * `stroke_speed`, whose values are typically < 10, while degree metrics still read cleanly).
     * Seeded verbatim into a new drill's `perPhaseTargetsJson` at creation time (Task F) — this
     * is NOT a runtime fallback re-applied to every empty editor field; a band a player
     * deliberately leaves blank stays silent (see `BaselineRuleFactory.applyRangeOverrides`).
     */
    fun defaultBands(): Map<String, ClosedRange<Double>> =
        DrillMetrics.ALL_KEYS.mapNotNull { key ->
            val stats = FOREHAND_ANDRII.metricStats[key] ?: return@mapNotNull null
            val spread = BaselineRuleFactory.DEFAULT_CONSISTENCY_K_SIGMA * stats.std
            key to round2(stats.mean - spread)..round2(stats.mean + spread)
        }.toMap()

    private fun round2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
}
