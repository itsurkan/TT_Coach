package com.ttcoachai.util

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
    fun parsesKneesStrikeBand() {
        val parsed = PerPhaseTargetsCodec.parse("""{"knees · strike":[110,130]}""")
        assertEquals(110f to 130f, parsed[PerPhaseTargetsCodec.KEY_KNEES_STRIKE])
    }

    @Test
    fun parsesBothKneeKeysIndependently() {
        val parsed = PerPhaseTargetsCodec.parse(
            """{"knees · backswing":[100,120],"knees · strike":[110,130]}"""
        )
        assertEquals(100f to 120f, parsed[PerPhaseTargetsCodec.KEY_KNEES_BACKSWING])
        assertEquals(110f to 130f, parsed[PerPhaseTargetsCodec.KEY_KNEES_STRIKE])
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
}
