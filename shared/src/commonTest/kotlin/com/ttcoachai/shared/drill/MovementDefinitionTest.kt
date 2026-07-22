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
    fun precisionForUnknownMetricIsQualitative() {
        assertEquals(MetricPrecision.QUALITATIVE, definition.precisionFor("some_future_rotational_cue"))
    }
}
