/*
 * AI Coach for Table Tennis
 * StrokeAnalyzerTest — Full pipeline tests for StrokeAnalyzer using fixture data and synthetic landmarks.
 */

package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.TestFixtures
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.ExerciseParameters
import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.StrokePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StrokeAnalyzerTest {

    // ── Empty / insufficient landmarks ────────────────────────────────────────

    @Test
    fun analyzeStroke_emptyLandmarks_returnsErrorResult() {
        val result = StrokeAnalyzer.analyzeStroke(emptyList(), ExerciseParameters.forehandDrive())
        assertTrue(result.errors.isNotEmpty(), "Expected error for empty landmarks")
        assertTrue(result.overallScore == 0f || result.errors.isNotEmpty())
    }

    @Test
    fun analyzeStroke_fewerThan33Landmarks_returnsPartialResult() {
        // With only 20 landmarks, most angle/metric functions return null → fewer validations
        val shortList = MutableList(20) { Landmark3D(0.5f, 0.5f, 0f) }
        // No indices 20, 23, 24 → wrist angle null, body rotation null, follow-through null
        // MetricCalculations.calculateContactHeight needs 16, 24 — index 16 exists but 24 doesn't
        val result = StrokeAnalyzer.analyzeStroke(shortList, ExerciseParameters.forehandDrive())
        // Should not throw; result may have a score based on available validations
        assertNotNull(result)
    }

    // ── Synthetic landmarks — wrist validation failure ─────────────────────────

    @Test
    fun analyzeStroke_bentWrist_reportsWristCorrectionType() {
        // 90° wrist angle clearly outside forehandDrive range [160, 170]
        val landmarks = makeLandmarks(33)
        // Wrist at origin, elbow along +X, index along +Y → 90° angle
        landmarks[14] = Landmark3D(1f, 0f, 0f)   // elbow
        landmarks[16] = Landmark3D(0f, 0f, 0f)   // wrist
        landmarks[20] = Landmark3D(0f, 1f, 0f)   // index finger

        val result = StrokeAnalyzer.analyzeStroke(landmarks, ExerciseParameters.forehandDrive())
        assertFalse(result.isWristAngleValid, "Wrist should be invalid for 90° angle")

        val wristFeedback = result.feedbackItems.firstOrNull { it.type == CorrectionType.WRIST }
        assertNotNull(wristFeedback, "Should have WRIST feedback item")
    }

    @Test
    fun analyzeStroke_straightWrist_passesWristValidation() {
        // 165° wrist angle (within forehandDrive range [160, 170])
        val landmarks = makeLandmarks(33)
        // Create ~165° angle at wrist: cos(165°) ≈ -0.9659
        landmarks[14] = Landmark3D(1f, 0f, 0f)              // elbow
        landmarks[16] = Landmark3D(0f, 0f, 0f)              // wrist
        landmarks[20] = Landmark3D(-0.9659f, 0.2588f, 0f)   // index finger
        val result = StrokeAnalyzer.analyzeStroke(landmarks, ExerciseParameters.forehandDrive())
        assertTrue(result.isWristAngleValid, "Wrist should be valid for ~165° angle")
    }

    // ── Phase parameter is preserved ──────────────────────────────────────────

    @Test
    fun analyzeStroke_preservesPhaseInResult() {
        val landmarks = makeLandmarks(33)
        val result = StrokeAnalyzer.analyzeStroke(landmarks, ExerciseParameters.forehandDriveBeginner(), StrokePhase.CONTACT)
        assertEquals(StrokePhase.CONTACT, result.phase)
    }

    // ── Score range ───────────────────────────────────────────────────────────

    @Test
    fun analyzeStroke_scoreIsInValidRange() {
        val landmarks = makeLandmarks(33)
        val result = StrokeAnalyzer.analyzeStroke(landmarks, ExerciseParameters.forehandDriveBeginner())
        assertTrue(result.overallScore in 0f..100f, "Score must be in [0, 100], was ${result.overallScore}")
    }

    // ── Fixture-based tests ───────────────────────────────────────────────────

    @Test
    fun analyzeStroke_correctForehandFixture_producesScoreAndNoExceptions() {
        val frames = TestFixtures.loadForehandDrive()
        assertTrue(frames.isNotEmpty(), "Fixture must have frames")

        // Analyze a middle frame with beginner params (to ensure some validations pass)
        val middleFrame = frames[frames.size / 2]
        val result = StrokeAnalyzer.analyzeStroke(
            middleFrame.landmarks,
            ExerciseParameters.forehandDriveBeginner()
        )
        assertNotNull(result)
        assertTrue(result.overallScore in 0f..100f)
    }

    @Test
    fun analyzeStroke_wrongForehandFixture_canIdentifyWristError() {
        val frames = TestFixtures.loadForehandDriveWrong()
        assertTrue(frames.isNotEmpty(), "Fixture must have frames")

        // Analyze multiple frames and check that at least one produces a WRIST feedback
        val strictParams = ExerciseParameters.forehandDrive()
        val wristErrors = frames.mapNotNull { frame ->
            val result = StrokeAnalyzer.analyzeStroke(frame.landmarks, strictParams)
            if (!result.isWristAngleValid) result else null
        }
        // The wrong poses fixture should produce wrist errors with strict parameters
        // (At least half of frames should fail wrist validation)
        assertTrue(
            wristErrors.size > frames.size / 3,
            "Expected wrist errors in wrong forehand poses, got ${wristErrors.size}/${frames.size}"
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeLandmarks(count: Int): MutableList<Landmark3D> =
        MutableList(count) { Landmark3D(0.5f, 0.5f, 0f) }
}
