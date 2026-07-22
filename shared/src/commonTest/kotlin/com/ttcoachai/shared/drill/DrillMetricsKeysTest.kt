package com.ttcoachai.shared.drill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Guards the PEAK/DERIVED/ALL key-grouping introduced alongside the three new
 * derived metric keys (follow_through_angle_2d, stroke_speed, coil_ratio).
 * shoulder_tilt is dropped from the peak/ALL key set (still a defined constant,
 * still used by AngleCalculations2D/VoicePresetCatalog/CoreMessageTemplates —
 * just no longer part of the peak-extracted spec set).
 */
class DrillMetricsKeysTest {

    @Test
    fun allKeysIsPeakKeysPlusDerivedKeys() {
        assertEquals(DrillMetrics.PEAK_KEYS + DrillMetrics.DERIVED_KEYS, DrillMetrics.ALL_KEYS)
    }

    @Test
    fun peakKeysHasFourEntries() {
        assertEquals(4, DrillMetrics.PEAK_KEYS.size)
    }

    @Test
    fun derivedKeysAreTheThreeNewMetrics() {
        assertEquals(
            listOf(
                DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D,
                DrillMetrics.METRIC_STROKE_SPEED,
                DrillMetrics.METRIC_COIL_RATIO
            ),
            DrillMetrics.DERIVED_KEYS
        )
    }

    @Test
    fun shoulderTiltIsNotInAllKeys() {
        assertFalse(DrillMetrics.ALL_KEYS.contains(DrillMetrics.METRIC_SHOULDER_TILT))
    }
}
