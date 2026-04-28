/*
 * AI Coach for Table Tennis
 * StrokePhaseDetectorTest — Unit tests for real-time stroke phase detection.
 */

package com.ttcoachai.shared.detection

import com.ttcoachai.shared.TestFixtures
import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.StrokePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrokePhaseDetectorTest {

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun initialPhase_isREADY() {
        val detector = StrokePhaseDetector()
        assertEquals(StrokePhase.READY, detector.getCurrentPhase())
    }

    // ── Empty / insufficient landmarks ────────────────────────────────────────

    @Test
    fun detect_emptyLandmarks_returnsCurrentPhase() {
        val detector = StrokePhaseDetector()
        val phase = detector.detect(emptyList(), 0L)
        assertEquals(StrokePhase.READY, phase)
    }

    @Test
    fun detect_tooFewLandmarks_returnsCurrentPhase() {
        val detector = StrokePhaseDetector()
        // Needs index 16 (size > 16), so 16 elements is too few
        val phase = detector.detect(List(16) { Landmark3D(0f, 0f, 0f) }, 100L)
        assertEquals(StrokePhase.READY, phase)
    }

    // ── reset() ───────────────────────────────────────────────────────────────

    @Test
    fun reset_clearsPhaseToREADY() {
        val detector = StrokePhaseDetector()
        // Feed frames to move away from READY: strong negative Z velocity triggers BACKSWING
        val landmarksWithBackswing = makeLandmarks(33, wristZ = -0.1f)
        repeat(10) { i ->
            detector.detect(makeLandmarks(33, wristZ = -0.1f * (i + 1)), (i * 100L))
        }
        // Reset should bring us back
        detector.reset()
        assertEquals(StrokePhase.READY, detector.getCurrentPhase())
    }

    @Test
    fun reset_clearsVelocityHistory() {
        val detector = StrokePhaseDetector()
        // Feed some frames
        repeat(5) { i ->
            detector.detect(makeLandmarks(33, wristZ = -0.05f * i), (i * 100L))
        }
        detector.reset()

        // After reset, a single frame with small velocity should stay READY
        val phase = detector.detect(makeLandmarks(33, wristZ = 0.001f), 0L)
        assertEquals(StrokePhase.READY, phase)
    }

    // ── Phase transitions using synthetic data ────────────────────────────────

    @Test
    fun detect_negativeWristVelocity_canTransitionToBackswing() {
        val detector = StrokePhaseDetector()
        // MIN_PHASE_FRAMES = 3, BACKSWING threshold = -0.015
        // Feed 5 frames with strong negative Z delta
        var prevZ = 0f
        var finalPhase = StrokePhase.READY
        for (i in 0 until 6) {
            val z = prevZ - 0.05f  // delta = -0.05, well below -0.015
            finalPhase = detector.detect(makeLandmarks(33, wristZ = z), (i * 100L))
            prevZ = z
        }
        // After sufficient frames with strong negative velocity, should be BACKSWING
        assertEquals(StrokePhase.BACKSWING, finalPhase)
    }

    @Test
    fun detect_stationaryLandmarks_staysREADY() {
        val detector = StrokePhaseDetector()
        // All frames same Z — velocity is 0 — should stay READY
        var phase = StrokePhase.READY
        repeat(10) { i ->
            phase = detector.detect(makeLandmarks(33, wristZ = 0f), (i * 100L))
        }
        assertEquals(StrokePhase.READY, phase)
    }

    // ── Fixture-based smoke test ───────────────────────────────────────────────

    @Test
    fun detect_forehandFixture_doesNotThrowAndProducesPhases() {
        val frames = TestFixtures.loadForehandDrive()
        assertTrue(frames.isNotEmpty(), "Fixture must have frames")

        val detector = StrokePhaseDetector()
        val phases = frames.map { frame ->
            detector.detect(frame.landmarks, frame.timestampMs)
        }

        // Should produce some phases (not all null/crash)
        assertTrue(phases.isNotEmpty())
        // Should produce at least one non-READY phase in a real stroke sequence
        val nonReadyPhases = phases.filter { it != StrokePhase.READY }
        assertTrue(nonReadyPhases.isNotEmpty(), "Expected at least one non-READY phase in forehand fixture")
    }

    @Test
    fun detect_emptyInput_returnsEmptyResult() {
        val frames = emptyList<com.ttcoachai.shared.models.PoseFrame>()
        val detector = StrokePhaseDetector()
        // Just verify no exception
        val phase = detector.detect(emptyList(), 0L)
        assertEquals(StrokePhase.READY, phase)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeLandmarks(count: Int, wristZ: Float = 0f): List<Landmark3D> =
        MutableList(count) { i ->
            if (i == 16) Landmark3D(0.5f, 0.5f, wristZ)
            else Landmark3D(0.5f, 0.5f, 0f)
        }
}
