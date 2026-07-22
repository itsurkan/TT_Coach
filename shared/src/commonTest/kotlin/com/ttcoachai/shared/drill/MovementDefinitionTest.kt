package com.ttcoachai.shared.drill

import kotlin.test.Test
import kotlin.test.assertEquals

class MovementDefinitionTest {

    private val definition = MovementDefinition(
        id = "test_movement",
        metrics = CoreMetricSpecs.ALL,
        messages = CoreMessageTemplates.TEMPLATES
    )

    @Test
    fun precisionForKnownMetricIsPreciseDegrees() {
        assertEquals(MetricPrecision.PRECISE_DEGREES, definition.precisionFor(DrillMetrics.METRIC_ELBOW_ANGLE))
        assertEquals(MetricPrecision.PRECISE_DEGREES, definition.precisionFor(DrillMetrics.METRIC_SHOULDER_ANGLE))
        assertEquals(MetricPrecision.PRECISE_DEGREES, definition.precisionFor(DrillMetrics.METRIC_KNEE_BEND))
        assertEquals(MetricPrecision.PRECISE_DEGREES, definition.precisionFor(DrillMetrics.METRIC_TORSO_LEAN))
    }

    @Test
    fun followThroughAngle2dDerivedMetricIsPreciseDegrees() {
        // follow_through_angle_2d is NOT in metrics (which only has the 4 peak specs),
        // but is PRECISE per MetricPrecisionPolicy — proves batch-path parity.
        assertEquals(MetricPrecision.PRECISE_DEGREES, definition.precisionFor(DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D))
    }

    @Test
    fun strokeSpeedAndCoilRatioDerivedMetricsAreQualitative() {
        // stroke_speed and coil_ratio are NOT in metrics, and are QUALITATIVE per policy.
        assertEquals(MetricPrecision.QUALITATIVE, definition.precisionFor(DrillMetrics.METRIC_STROKE_SPEED))
        assertEquals(MetricPrecision.QUALITATIVE, definition.precisionFor(DrillMetrics.METRIC_COIL_RATIO))
    }

    @Test
    fun precisionForUnknownMetricIsQualitative() {
        assertEquals(MetricPrecision.QUALITATIVE, definition.precisionFor("some_future_rotational_cue"))
    }
}
