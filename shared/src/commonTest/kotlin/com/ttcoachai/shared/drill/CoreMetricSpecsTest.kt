package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreMetricSpecsTest {

    private fun kpList(vararg overrides: Pair<Int, Keypoint2D>): List<Keypoint2D> {
        val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
        overrides.forEach { (i, p) -> kp[i] = p }
        return kp
    }

    @Test
    fun allFourCoreMetricsPresentWithExpectedKeysAndPrecision() {
        val keys = CoreMetricSpecs.ALL.map { it.key }.toSet()
        assertEquals(DrillMetrics.PEAK_KEYS.toSet(), keys)
        CoreMetricSpecs.ALL.forEach {
            assertEquals(MetricPrecision.PRECISE_DEGREES, it.precision, "${it.key} must be precise per trust rule")
        }
    }

    @Test
    fun sanityBoundsMatchSpecExactly() {
        val byKey = CoreMetricSpecs.ALL.associateBy { it.key }
        assertEquals(20.0..170.0, byKey[DrillMetrics.METRIC_ELBOW_ANGLE]!!.sanityBounds)
        assertEquals(5.0..175.0, byKey[DrillMetrics.METRIC_SHOULDER_ANGLE]!!.sanityBounds)
        assertEquals(60.0..180.0, byKey[DrillMetrics.METRIC_KNEE_BEND]!!.sanityBounds)
        assertEquals(-60.0..60.0, byKey[DrillMetrics.METRIC_TORSO_LEAN]!!.sanityBounds)
    }

    @Test
    fun elbowExtractorMatchesAngleCalculations2D() {
        val kp = kpList(
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.50f, 0.30f, 1f),
            Coco17.RIGHT_ELBOW to Keypoint2D(0.55f, 0.42f, 1f),
            Coco17.RIGHT_WRIST to Keypoint2D(0.65f, 0.40f, 1f)
        )
        val spec = CoreMetricSpecs.ALL.first { it.key == DrillMetrics.METRIC_ELBOW_ANGLE }
        val expected = com.ttcoachai.shared.analysis.AngleCalculations2D.elbowAngle(kp, Handedness.RIGHT, 1f, 0.3f)
        assertEquals(expected, spec.extractor(kp, Handedness.RIGHT, 1f, 0.3f))
    }

    @Test
    fun torsoLeanIgnoresHandednessParam() {
        val kp = kpList(
            Coco17.LEFT_SHOULDER to Keypoint2D(0.48f, 0.30f, 1f),
            Coco17.RIGHT_SHOULDER to Keypoint2D(0.52f, 0.30f, 1f),
            Coco17.LEFT_HIP to Keypoint2D(0.48f, 0.55f, 1f),
            Coco17.RIGHT_HIP to Keypoint2D(0.52f, 0.55f, 1f),
            Coco17.NOSE to Keypoint2D(0.55f, 0.20f, 1f)
        )
        val leanSpec = CoreMetricSpecs.ALL.first { it.key == DrillMetrics.METRIC_TORSO_LEAN }
        val leanRight = leanSpec.extractor(kp, Handedness.RIGHT, 1f, 0.3f)
        val leanLeft = leanSpec.extractor(kp, Handedness.LEFT, 1f, 0.3f)
        assertEquals(leanRight, leanLeft)
    }

    @Test
    fun sanityBoundsIsSaneStillReflectsCoreMetricSpecs() {
        assertTrue(SanityBounds.isSane(DrillMetrics.METRIC_ELBOW_ANGLE, 90.0))
        assertFalse(SanityBounds.isSane(DrillMetrics.METRIC_ELBOW_ANGLE, 19.0))
        assertFalse(SanityBounds.isSane(DrillMetrics.METRIC_ELBOW_ANGLE, 171.0))
        assertTrue(SanityBounds.isSane("unknown_metric", 12345.0))
    }
}
