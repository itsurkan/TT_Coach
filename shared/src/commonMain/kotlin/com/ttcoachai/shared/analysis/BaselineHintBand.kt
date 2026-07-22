package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.MetricStats
import kotlin.math.roundToInt

/**
 * Turns a baseline [MetricStats] into a rounded `mean ± k·σ` integer band —
 * the same consistency window [BaselineRuleFactory.defaultRules] derives a
 * [BaselineRule.ConsistencyRule] from. Used by the custom-drill editor to show
 * an honest placeholder ("what actually applies if you leave this empty") on
 * per-phase-target rows that have a matching baseline metric, instead of a
 * hardcoded static hint.
 *
 * Pure/KMP — no Android deps, so it's testable in `commonTest` and reusable
 * from any platform's editor UI (Android today, iOS later).
 */
object BaselineHintBand {

    /**
     * Returns `low..high` (both rounded to the nearest int) for `mean ± kSigma·std`,
     * or null when [stats] is null or `std <= 0` — a flat/degenerate baseline can't
     * honestly back a placeholder band, so callers should fall back to the static
     * XML hint in that case.
     */
    fun compute(
        stats: MetricStats?,
        kSigma: Double = BaselineRuleFactory.DEFAULT_CONSISTENCY_K_SIGMA
    ): Pair<Int, Int>? {
        if (stats == null || stats.std <= 0.0) return null
        val spread = kSigma * stats.std
        val low = (stats.mean - spread).roundToInt()
        val high = (stats.mean + spread).roundToInt()
        return low to high
    }
}
