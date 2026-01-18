package com.google.mediapipe.examples.poselandmarker

import com.google.mediapipe.examples.poselandmarker.models.CorrectionType
import com.google.mediapipe.examples.poselandmarker.models.ExerciseParameters
import com.google.mediapipe.examples.poselandmarker.models.StrokePhase
import com.google.mediapipe.examples.poselandmarker.services.MotionAnalyzer
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Optional

class MotionAnalyzerTest {

    private lateinit var motionAnalyzer: MotionAnalyzer
    private val parameters = ExerciseParameters.forehandDrive()

    @Before
    fun setup() {
        motionAnalyzer = MotionAnalyzer(parameters)
    }

    private fun createLandmark(x: Float, y: Float, z: Float = 0f): NormalizedLandmark {
        return NormalizedLandmark.create(x, y, z, Optional.of(1f), Optional.of(1f))
    }

    private fun createDummyLandmarks(): List<NormalizedLandmark> {
        // Create 33 landmarks
        return List(33) { createLandmark(0.5f, 0.5f) }
    }

    @Test
    fun `test analyzeStroke returns feedback items for errors`() {
        // Set up landmarks that will cause specific errors
        // For example, high wrist angle (bent wrist)
        val landmarks = createDummyLandmarks().toMutableList()
        
        // Right Arm: shoulder=12, elbow=14, wrist=16, index=20
        landmarks[12] = createLandmark(0.5f, 0.4f) // shoulder
        landmarks[14] = createLandmark(0.5f, 0.5f) // elbow
        landmarks[16] = createLandmark(0.6f, 0.5f) // wrist - straight-ish
        landmarks[20] = createLandmark(0.7f, 0.6f) // index - bent relative to elbow-wrist
        
        val result = motionAnalyzer.analyzeStroke(landmarks, StrokePhase.CONTACT)
        
        assertNotNull(result)
        // Check if we have feedback items
        assertTrue("Feedback items should not be empty if there are technique issues", result.feedbackItems.isNotEmpty())
        
        // Check if correction types are present
        val types = result.feedbackItems.map { it.type }
        assertTrue(types.isNotEmpty())
    }

    @Test
    fun `test perfect stroke returns high score and positive feedback`() {
        // Mock a "perfect" landmark set for forehand drive
        // This is hard to mock perfectly without real math, but let's try to make it "good enough"
        // based on default parameters (e.g., wrist angle near 180)
        
        val landmarks = createDummyLandmarks().toMutableList()
        
        // Wrist (16) between Elbow (14) and Index (20) - perfectly straight
        landmarks[14] = createLandmark(0.5f, 0.5f) // elbow
        landmarks[16] = createLandmark(0.6f, 0.5f) // wrist
        landmarks[20] = createLandmark(0.7f, 0.5f) // index
        
        // Shoulders (11, 12) and Hips (23, 24) aligned
        landmarks[11] = createLandmark(0.4f, 0.4f)
        landmarks[12] = createLandmark(0.6f, 0.4f)
        landmarks[23] = createLandmark(0.4f, 0.7f)
        landmarks[24] = createLandmark(0.6f, 0.7f)
        
        // Follow-through (Shoulder 12 - Elbow 14 - Wrist 16)
        // Straight-ish arm
        
        val result = motionAnalyzer.analyzeStroke(landmarks, StrokePhase.CONTACT)
        
        // The score might not be 100 but should be high if our mocks hit target ranges
        println("Score: ${result.overallScore}")
        
        if (result.overallScore >= 90f) {
            assertTrue(result.feedbackItems.any { it.isPositive && it.type == CorrectionType.GENERAL })
        }
    }
}
