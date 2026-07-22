package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.MetricStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [BaselineHintBand.compute] — turns a baseline [MetricStats] into a rounded
 * `mean ± k·σ` band (used as the custom-drill editor's per-phase-target
 * placeholder when a real baseline exists), or null when the stat can't
 * honestly back a placeholder.
 */
class BaselineHintBandTest {

    @Test
    fun computesRoundedMeanPlusMinusKSigmaBand() {
        val stats = MetricStats(mean = 120.0, std = 5.0, min = 100.0, max = 140.0, sampleCount = 10)

        val band = BaselineHintBand.compute(stats, kSigma = 2.0)

        assertEquals(110, band?.first)
        assertEquals(130, band?.second)
    }

    @Test
    fun defaultsToBaselineRuleFactoryKSigma() {
        val stats = MetricStats(mean = 120.0, std = 5.0, min = 100.0, max = 140.0, sampleCount = 10)

        val band = BaselineHintBand.compute(stats)

        assertEquals(BaselineRuleFactory.DEFAULT_CONSISTENCY_K_SIGMA, 2.0)
        assertEquals(110, band?.first)
        assertEquals(130, band?.second)
    }

    @Test
    fun roundsToNearestInt() {
        val stats = MetricStats(mean = 121.4, std = 4.6, min = 100.0, max = 140.0, sampleCount = 10)

        val band = BaselineHintBand.compute(stats, kSigma = 2.0)

        // low = 121.4 - 9.2 = 112.2 -> 112 ; high = 121.4 + 9.2 = 130.6 -> 131
        assertEquals(112, band?.first)
        assertEquals(131, band?.second)
    }

    @Test
    fun nullStatsYieldsNull() {
        assertNull(BaselineHintBand.compute(null, kSigma = 2.0))
    }

    @Test
    fun zeroStdYieldsNull() {
        val stats = MetricStats(mean = 120.0, std = 0.0, min = 100.0, max = 140.0, sampleCount = 10)
        assertNull(BaselineHintBand.compute(stats, kSigma = 2.0))
    }

    @Test
    fun negativeStdYieldsNull() {
        val stats = MetricStats(mean = 120.0, std = -1.0, min = 100.0, max = 140.0, sampleCount = 10)
        assertNull(BaselineHintBand.compute(stats, kSigma = 2.0))
    }
}
