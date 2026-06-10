package com.ttcoachai.shared.detection

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.models.Handedness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrokeDetector2DFixtureTest {

    @Test
    fun detectsStrokesOnAndriiRtmFixture() {
        val seq = TestFixturesV2.loadAndriiRtm()
        val strokes = StrokeDetector2D().detect(seq.frames, Handedness.RIGHT, seq.aspectRatio, seq.intervalMs)

        // Diagnostic for founder QA against poses_viewer (footage predates the
        // camera-placement protocol; counts are pipeline checks, not tuning).
        println("andrii_1_rtm: ${strokes.size} strokes at peaks ${strokes.map { it.peakFrame }}")

        assertTrue(strokes.isNotEmpty(), "expected at least one stroke in andrii_1_rtm")
        strokes.forEach { s ->
            assertTrue(s.startFrame <= s.peakFrame && s.peakFrame <= s.endFrame, "ordered boundaries: $s")
            assertTrue(s.endFrame < seq.frames.size, "endFrame in range: $s")
            assertTrue(s.peakSpeed > 0f)
        }
        strokes.zipWithNext().forEach { (a, b) ->
            assertTrue(a.peakFrame < b.peakFrame, "strokes ordered by time")
        }
    }

    @Test
    fun fixtureDetectionIsDeterministic() {
        val seq = TestFixturesV2.loadAndriiRtm()
        val a = StrokeDetector2D().detect(seq.frames, Handedness.RIGHT, seq.aspectRatio, seq.intervalMs)
        val b = StrokeDetector2D().detect(seq.frames, Handedness.RIGHT, seq.aspectRatio, seq.intervalMs)
        assertEquals(a, b)
    }
}
