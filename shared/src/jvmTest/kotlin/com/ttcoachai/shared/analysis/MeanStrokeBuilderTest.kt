package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.DetectedStroke
import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.PoseFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeanStrokeBuilderTest {

    @Test
    fun buildsEmptyWhenNoStrokes() {
        val out = MeanStrokeBuilder.build(
            strokes = emptyList(),
            allFrames = listOf(frame(0, 0f), frame(1, 1f))
        )
        assertEquals(0, out.size)
    }

    @Test
    fun buildsEmptyWhenStrokesTooShort() {
        val frames = listOf(frame(0, 0f), frame(1, 1f))
        val out = MeanStrokeBuilder.build(
            strokes = listOf(stroke(0, 0)),
            allFrames = frames
        )
        assertEquals(0, out.size)
    }

    @Test
    fun singleStrokeGetsTimeNormalizedToTargetLength() {
        val frames = (0..9).map { i -> frame(i, i.toFloat()) }
        val out = MeanStrokeBuilder.build(
            strokes = listOf(stroke(0, 9)),
            allFrames = frames,
            targetLength = 5
        )
        assertEquals(5, out.size)
        // First and last frames preserved
        assertEquals(0f, out.first().landmarks.first().x, 1e-5f)
        assertEquals(9f, out.last().landmarks.first().x, 1e-5f)
        // Middle is halfway through source
        assertEquals(4.5f, out[2].landmarks.first().x, 1e-5f)
    }

    @Test
    fun twoStrokesGetAveraged() {
        val a = (0..9).map { i -> frame(i, i.toFloat()) }
        val b = (0..9).map { i -> frame(i, (i + 10).toFloat()) } // offset by 10
        val combined = a + b
        val out = MeanStrokeBuilder.build(
            strokes = listOf(stroke(0, 9), stroke(10, 19)),
            allFrames = combined,
            targetLength = 3
        )
        assertEquals(3, out.size)
        // At t=0: stroke A first frame (x=0) + stroke B first frame (x=10) → mean = 5
        assertEquals(5f, out[0].landmarks.first().x, 1e-5f)
        // At t=2: stroke A last (9) + stroke B last (19) → mean = 14
        assertEquals(14f, out[2].landmarks.first().x, 1e-5f)
    }

    @Test
    fun frameIndexAndTimestampAreDeterministic() {
        val frames = (0..9).map { i -> frame(i, i.toFloat()) }
        val out = MeanStrokeBuilder.build(
            strokes = listOf(stroke(0, 9)),
            allFrames = frames,
            targetLength = 4,
            intervalMs = 50L
        )
        assertTrue(out.isNotEmpty())
        for ((idx, f) in out.withIndex()) {
            assertEquals(idx, f.frameIndex)
            assertEquals(idx * 50L, f.timestampMs)
        }
    }

    private fun frame(idx: Int, xSeed: Float): PoseFrame = PoseFrame(
        frameIndex = idx,
        timestampMs = idx * 33L,
        landmarks = List(33) { Landmark3D(x = xSeed, y = 0f, z = 0f, visibility = 1f, presence = 1f) }
    )

    private fun stroke(start: Int, end: Int): DetectedStroke = DetectedStroke(
        strokeIndex = 0,
        preparationStartFrame = start,
        preparationEndFrame = start,
        forwardStartFrame = start,
        contactFrame = (start + end) / 2,
        forwardEndFrame = end,
        returnStartFrame = end,
        returnEndFrame = end,
        backswingMinValue = 0f,
        forwardPeakValue = 0f,
        peakVelocity = 0f,
        strokeDurationMs = 0L,
        forwardSwingDurationMs = 0L,
        isComplete = true
    )
}
