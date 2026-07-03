package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.BaselineDeriver
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guard the design decision that legacy MediaPipe-3D metric keys are disjoint
 * from the 2D DrillMetrics keys. A legacy 3D baseline must never accidentally
 * satisfy 2D drill rules.
 */
class MetricKeyDisjointTest {

    @Test
    fun legacyThreeDAndTwoDMetricKeysAreDisjoint() {
        // Legacy 3D keys from BaselineDeriver (frozen)
        val legacyKeys = setOf(
            BaselineDeriver.METRIC_WRIST_ANGLE,
            BaselineDeriver.METRIC_BODY_ROTATION,
            BaselineDeriver.METRIC_FOLLOW_THROUGH_ANGLE,
            BaselineDeriver.METRIC_CONTACT_HEIGHT,
            BaselineDeriver.METRIC_ELBOW_BODY_DISTANCE
        )

        // 2D keys from DrillMetrics (current pivot)
        val twoDKeys = DrillMetrics.ALL_KEYS.toSet()

        // Assertion: no overlap
        val overlap = legacyKeys intersect twoDKeys
        assertTrue(
            overlap.isEmpty(),
            "Legacy 3D and 2D metric keys must be disjoint, but found overlap: $overlap"
        )
    }

    @Test
    fun bothMetricKeySetsMustBeNonEmpty() {
        val legacyKeys = setOf(
            BaselineDeriver.METRIC_WRIST_ANGLE,
            BaselineDeriver.METRIC_BODY_ROTATION,
            BaselineDeriver.METRIC_FOLLOW_THROUGH_ANGLE,
            BaselineDeriver.METRIC_CONTACT_HEIGHT,
            BaselineDeriver.METRIC_ELBOW_BODY_DISTANCE
        )

        val twoDKeys = DrillMetrics.ALL_KEYS.toSet()

        // Guard against a vacuous pass if a constant list is emptied
        assertFalse(legacyKeys.isEmpty(), "Legacy 3D metric keys must not be empty")
        assertFalse(twoDKeys.isEmpty(), "2D metric keys must not be empty")
    }
}
