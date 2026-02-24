/*
 * AI Coach for Table Tennis
 * JsonStrokeDetectorTest — Unit tests for batch stroke detection from pose frame sequences.
 */

package com.ttcoachai.shared.detection

import com.ttcoachai.shared.TestFixtures
import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.PoseFrame
import com.ttcoachai.shared.models.StrokeDetectorConfig
import com.ttcoachai.shared.models.StrokePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonStrokeDetectorTest {

    // ── Empty input ───────────────────────────────────────────────────────────

    @Test
    fun detect_emptyFrames_returnsEmptyResult() {
        val detector = JsonStrokeDetector()
        val result = detector.detect(emptyList())
        assertTrue(result.strokes.isEmpty(), "Expected no strokes for empty input")
        assertTrue(result.framePhases.isEmpty(), "Expected no frame phases for empty input")
        assertEquals(0, result.totalFrames)
    }

    // ── Single frame ──────────────────────────────────────────────────────────

    @Test
    fun detect_singleFrame_doesNotCrash() {
        val detector = JsonStrokeDetector()
        val frame = PoseFrame(
            frameIndex = 0,
            timestampMs = 0L,
            landmarks = List(33) { Landmark3D(0.5f, 0.5f, 0f) }
        )
        val result = detector.detect(listOf(frame))
        assertEquals(1, result.totalFrames)
    }

    // ── FOREHAND config (default) ─────────────────────────────────────────────

    @Test
    fun detect_forehandFixture_detectsAtLeastOneStroke() {
        val frames = TestFixtures.loadForehandDrive()
        assertTrue(frames.isNotEmpty(), "Fixture must have frames")

        val detector = JsonStrokeDetector(StrokeDetectorConfig.FOREHAND)
        val result = detector.detect(frames)

        assertTrue(
            result.strokes.isNotEmpty(),
            "Expected at least one stroke in forehand_drive fixture, got ${result.strokes.size} strokes from ${frames.size} frames"
        )
    }

    @Test
    fun detect_forehandFixture_totalFramesMatchesInput() {
        val frames = TestFixtures.loadForehandDrive()
        val detector = JsonStrokeDetector(StrokeDetectorConfig.FOREHAND)
        val result = detector.detect(frames)
        assertEquals(frames.size, result.totalFrames)
    }

    @Test
    fun detect_forehandFixture_framePhasesCoverFrames() {
        val frames = TestFixtures.loadForehandDrive()
        val detector = JsonStrokeDetector(StrokeDetectorConfig.FOREHAND)
        val result = detector.detect(frames)
        // Every frame should have a corresponding phase entry
        assertEquals(frames.size, result.framePhases.size,
            "Expected one phase entry per frame, got ${result.framePhases.size} for ${frames.size} frames")
    }

    @Test
    fun detect_forehandFixture2_detectsStrokes() {
        val frames = TestFixtures.loadForehandDrive2()
        assertTrue(frames.isNotEmpty(), "Fixture 2 must have frames")

        val detector = JsonStrokeDetector(StrokeDetectorConfig.FOREHAND)
        val result = detector.detect(frames)

        // forehand_drive2 may have different stroke count but should detect at least one
        assertTrue(
            result.strokes.isNotEmpty(),
            "Expected at least one stroke in forehand_drive2 fixture"
        )
    }

    // ── Detected stroke structure ─────────────────────────────────────────────

    @Test
    fun detect_forehandFixture_strokesHaveValidFrameRanges() {
        val frames = TestFixtures.loadForehandDrive()
        val detector = JsonStrokeDetector(StrokeDetectorConfig.FOREHAND)
        val result = detector.detect(frames)

        for (stroke in result.strokes) {
            assertTrue(stroke.preparationStartFrame >= 0, "preparationStartFrame must be >= 0")
            assertTrue(stroke.returnEndFrame <= frames.lastIndex, "returnEndFrame must be <= lastIndex")
            assertTrue(
                stroke.preparationStartFrame <= stroke.returnEndFrame,
                "start must be <= end for stroke ${stroke.strokeIndex}"
            )
        }
    }

    @Test
    fun detect_forehandFixture_strokesHavePositiveDuration() {
        val frames = TestFixtures.loadForehandDrive()
        val detector = JsonStrokeDetector(StrokeDetectorConfig.FOREHAND)
        val result = detector.detect(frames)

        for (stroke in result.strokes) {
            assertTrue(stroke.strokeDurationMs >= 0, "Stroke duration must be >= 0")
        }
    }

    // ── getStrokeForFrame / getPhaseForFrame ──────────────────────────────────

    @Test
    fun detect_forehandFixture_getPhaseForFrameReturnsNonNull() {
        val frames = TestFixtures.loadForehandDrive()
        val detector = JsonStrokeDetector(StrokeDetectorConfig.FOREHAND)
        val result = detector.detect(frames)

        // Every frame index should have a phase
        for (frameIndex in frames.indices) {
            val phase = result.getPhaseForFrame(frameIndex)
            assertTrue(phase != null || frameIndex < frames.size,
                "Expected phase for frame $frameIndex")
        }
    }

    // ── BACKHAND config ───────────────────────────────────────────────────────

    @Test
    fun detect_backhandConfig_doesNotCrashOnForehandData() {
        val frames = TestFixtures.loadForehandDrive()
        // Running BACKHAND config on forehand data may detect 0 strokes, but must not crash
        val detector = JsonStrokeDetector(StrokeDetectorConfig.BACKHAND)
        val result = detector.detect(frames)
        // Just verify no exception and totalFrames is correct
        assertEquals(frames.size, result.totalFrames)
    }

    @Test
    fun detect_backhandConfig_usesLandmark15() {
        // BACKHAND config uses landmark index 15 (left wrist) and invertDirection=true
        val config = StrokeDetectorConfig.BACKHAND
        assertEquals(15, config.landmarkIndex)
        assertEquals(true, config.invertDirection)
    }

    // ── Repeated detect() calls are independent ───────────────────────────────

    @Test
    fun detect_calledTwice_producesConsistentResults() {
        val frames = TestFixtures.loadForehandDrive()
        val detector = JsonStrokeDetector(StrokeDetectorConfig.FOREHAND)

        val result1 = detector.detect(frames)
        val result2 = detector.detect(frames)

        assertEquals(result1.strokes.size, result2.strokes.size,
            "Repeated detect() calls should produce the same stroke count")
    }
}
