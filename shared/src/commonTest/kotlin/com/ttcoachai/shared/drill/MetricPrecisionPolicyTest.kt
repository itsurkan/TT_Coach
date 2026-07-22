package com.ttcoachai.shared.drill

import kotlin.test.Test
import kotlin.test.assertEquals

class MetricPrecisionPolicyTest {

    @Test
    fun preciseKeysAreExactlyTheFive() {
        // Elbow
        assertEquals(
            MetricPrecision.PRECISE_DEGREES,
            MetricPrecisionPolicy.precisionFor(DrillMetrics.METRIC_ELBOW_ANGLE),
            "Elbow angle must be PRECISE_DEGREES"
        )
        // Shoulder
        assertEquals(
            MetricPrecision.PRECISE_DEGREES,
            MetricPrecisionPolicy.precisionFor(DrillMetrics.METRIC_SHOULDER_ANGLE),
            "Shoulder angle must be PRECISE_DEGREES"
        )
        // Knee bend
        assertEquals(
            MetricPrecision.PRECISE_DEGREES,
            MetricPrecisionPolicy.precisionFor(DrillMetrics.METRIC_KNEE_BEND),
            "Knee bend must be PRECISE_DEGREES"
        )
        // Torso lean
        assertEquals(
            MetricPrecision.PRECISE_DEGREES,
            MetricPrecisionPolicy.precisionFor(DrillMetrics.METRIC_TORSO_LEAN),
            "Torso lean must be PRECISE_DEGREES"
        )
        // Follow-through (the only derived precision metric)
        assertEquals(
            MetricPrecision.PRECISE_DEGREES,
            MetricPrecisionPolicy.precisionFor(DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D),
            "Follow-through angle must be PRECISE_DEGREES"
        )
    }

    @Test
    fun derivedQualitativeKeysAreNotPrecise() {
        // Stroke speed — qualitative proxy, not a degree measurement
        assertEquals(
            MetricPrecision.QUALITATIVE,
            MetricPrecisionPolicy.precisionFor(DrillMetrics.METRIC_STROKE_SPEED),
            "Stroke speed must be QUALITATIVE"
        )
        // Coil ratio — qualitative proxy, not a degree measurement
        assertEquals(
            MetricPrecision.QUALITATIVE,
            MetricPrecisionPolicy.precisionFor(DrillMetrics.METRIC_COIL_RATIO),
            "Coil ratio must be QUALITATIVE"
        )
    }

    @Test
    fun shoulderTiltIsNotPrecise() {
        // Shoulder tilt was dropped from the allowlist and is now qualitative
        assertEquals(
            MetricPrecision.QUALITATIVE,
            MetricPrecisionPolicy.precisionFor(DrillMetrics.METRIC_SHOULDER_TILT),
            "Shoulder tilt must be QUALITATIVE"
        )
    }

    @Test
    fun unknownKeysDefaultToQualitative() {
        assertEquals(
            MetricPrecision.QUALITATIVE,
            MetricPrecisionPolicy.precisionFor("nonsense_metric"),
            "Unknown metrics must default to QUALITATIVE"
        )
    }
}
