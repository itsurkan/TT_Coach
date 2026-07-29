package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.BaselineRuleFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShippedBaselinesTest {

    @Test
    fun baselineHasAllSevenMetricKeys() {
        for (key in DrillMetrics.ALL_KEYS) {
            assertTrue(
                key in ShippedBaselines.FOREHAND_ANDRII.metricStats,
                "FOREHAND_ANDRII.metricStats must contain $key"
            )
        }
    }

    @Test
    fun baselineHasPlausibleHandednessAndRepCount() {
        assertEquals("right", ShippedBaselines.FOREHAND_ANDRII.drillerHandedness)
        assertTrue(ShippedBaselines.FOREHAND_ANDRII.repCount > 0)
        assertTrue(ShippedBaselines.FOREHAND_ANDRII.qualityScore in 0.0..1.0)
    }

    @Test
    fun defaultBandsCoversAllSevenKeysWithMinLessThanMax() {
        val bands = ShippedBaselines.defaultBands()
        assertEquals(DrillMetrics.ALL_KEYS.toSet(), bands.keys)
        for ((key, band) in bands) {
            assertTrue(band.start < band.endInclusive, "$key band must have min < max")
        }
    }

    @Test
    fun defaultBandsMatchMeanPlusMinusTwoSigmaIndependently() {
        val bands = ShippedBaselines.defaultBands()
        for (key in DrillMetrics.ALL_KEYS) {
            val stats = ShippedBaselines.FOREHAND_ANDRII.metricStats.getValue(key)
            val spread = BaselineRuleFactory.DEFAULT_CONSISTENCY_K_SIGMA * stats.std
            val expectedLow = stats.mean - spread
            val expectedHigh = stats.mean + spread
            val band = bands.getValue(key)
            assertTrue(
                kotlin.math.abs(band.start - expectedLow) < 0.02,
                "$key band.start=${band.start} must match mean-2sigma=$expectedLow (within rounding)"
            )
            assertTrue(
                kotlin.math.abs(band.endInclusive - expectedHigh) < 0.02,
                "$key band.endInclusive=${band.endInclusive} must match mean+2sigma=$expectedHigh (within rounding)"
            )
        }
    }
}
