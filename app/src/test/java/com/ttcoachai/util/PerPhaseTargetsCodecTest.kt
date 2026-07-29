package com.ttcoachai.util

import com.ttcoachai.shared.drill.DrillMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JSON parsing tests for [PerPhaseTargetsCodec] — no Android dependency, plain JUnit.
 */
class PerPhaseTargetsCodecTest {

    @Test
    fun blankJsonYieldsEmptyMap() {
        assertTrue(PerPhaseTargetsCodec.parse("").isEmpty())
        assertTrue(PerPhaseTargetsCodec.parse("   ").isEmpty())
    }

    @Test
    fun malformedJsonYieldsEmptyMap() {
        assertTrue(PerPhaseTargetsCodec.parse("{not json").isEmpty())
    }

    @Test
    fun legacyKneesStrikeKeyMapsToKneeBendMetricKey() {
        val parsed = PerPhaseTargetsCodec.parse("""{"knees · strike":[110,130]}""")
        assertEquals(110f to 130f, parsed[DrillMetrics.METRIC_KNEE_BEND])
        assertTrue(PerPhaseTargetsCodec.KEY_KNEES_STRIKE !in parsed)
    }

    @Test
    fun legacyTorsoStrikeKeyMapsToTorsoLeanMetricKey() {
        val parsed = PerPhaseTargetsCodec.parse("""{"torso tilt · strike":[25,45]}""")
        assertEquals(25f to 45f, parsed[DrillMetrics.METRIC_TORSO_LEAN])
    }

    @Test
    fun newDirectMetricKeysDecodeUnchanged() {
        val parsed = PerPhaseTargetsCodec.parse(
            """{"elbow_angle":[35,70],"coil_ratio":[0.9,1.3]}"""
        )
        assertEquals(35f to 70f, parsed[DrillMetrics.METRIC_ELBOW_ANGLE])
        assertEquals(0.9f to 1.3f, parsed[DrillMetrics.METRIC_COIL_RATIO])
    }

    @Test
    fun decimalValuesSurviveRoundTrip() {
        val encoded = PerPhaseTargetsCodec.encode(mapOf(DrillMetrics.METRIC_STROKE_SPEED to (3.25f to 6.8f)))
        val parsed = PerPhaseTargetsCodec.parse(encoded)
        assertEquals(3.25f to 6.8f, parsed[DrillMetrics.METRIC_STROKE_SPEED])
    }

    @Test
    fun ignoresArraysShorterThanTwoElements() {
        val parsed = PerPhaseTargetsCodec.parse("""{"knees · strike":[110]}""")
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun unrelatedKeysPassThroughGenerically() {
        val parsed = PerPhaseTargetsCodec.parse("""{"elbow · backswing":[80,100]}""")
        assertEquals(80f to 100f, parsed["elbow · backswing"])
    }

    @Test
    fun encodeEmptyMapYieldsEmptyString() {
        assertEquals("", PerPhaseTargetsCodec.encode(emptyMap()))
    }

    @Test
    fun encodeThenParseRoundTripsMultipleKeys() {
        val bands = mapOf(
            DrillMetrics.METRIC_KNEE_BEND to (106f to 134f),
            DrillMetrics.METRIC_COIL_RATIO to (0.95f to 1.28f)
        )
        val parsed = PerPhaseTargetsCodec.parse(PerPhaseTargetsCodec.encode(bands))
        assertEquals(bands, parsed)
    }
}
