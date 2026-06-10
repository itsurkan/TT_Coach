package com.ttcoachai.shared.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BaselineDeriverFromMetricsTest {

    private val elbow = "elbow_angle"
    private val total = "stroke_total_ms"

    private fun rep(elbowDeg: Double) = mapOf(elbow to elbowDeg)
    private fun phases(ms: Double) = mapOf(total to ms)

    @Test
    fun derivesStatsFromGenericMetricMaps() {
        val baseline = BaselineDeriver.deriveFromMetrics(
            repMetrics = listOf(rep(100.0), rep(110.0), rep(120.0)),
            repPhaseDurations = listOf(phases(800.0), phases(820.0), phases(840.0)),
            drillType = "forehand_drive",
            createdAtMs = 1_000L,
            drillerHandedness = "right",
            minRepCount = 3
        )
        assertEquals(3, baseline.repCount)
        assertEquals(110.0, baseline.metricStats[elbow]!!.mean, 1e-9)
        assertEquals(10.0, baseline.metricStats[elbow]!!.std, 1e-9)
        assertEquals(820.0, baseline.phaseDurationsMs[total]!!.mean, 1e-9)
        assertEquals("forehand_drive", baseline.drillType)
        assertEquals("right", baseline.drillerHandedness)
    }

    @Test
    fun excludesOutlierRep() {
        // 5 tight reps + 1 far outlier; 2σ one-pass exclusion must drop the outlier
        val metrics = listOf(rep(100.0), rep(101.0), rep(99.0), rep(100.5), rep(99.5), rep(160.0))
        val durations = List(6) { phases(800.0) }
        val baseline = BaselineDeriver.deriveFromMetrics(
            metrics, durations, "forehand_drive", 0L, null, minRepCount = 3
        )
        assertEquals(listOf(5), baseline.excludedRepIndices)
        assertEquals(5, baseline.repCount)
        assertTrue(baseline.metricStats[elbow]!!.mean < 102.0)
    }

    @Test
    fun throwsBelowMinRepCount() {
        assertFailsWith<IllegalArgumentException> {
            BaselineDeriver.deriveFromMetrics(
                listOf(rep(100.0), rep(110.0)),
                listOf(phases(800.0), phases(810.0)),
                "forehand_drive", 0L, null, minRepCount = 3
            )
        }
    }

    @Test
    fun throwsOnEmptyOrMismatchedInput() {
        assertFailsWith<IllegalArgumentException> {
            BaselineDeriver.deriveFromMetrics(emptyList(), emptyList(), "x", 0L, null)
        }
        assertFailsWith<IllegalArgumentException> {
            BaselineDeriver.deriveFromMetrics(listOf(rep(1.0)), emptyList(), "x", 0L, null)
        }
    }
}
