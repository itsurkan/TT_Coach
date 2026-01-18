package com.google.mediapipe.examples.poselandmarker

import com.google.mediapipe.examples.poselandmarker.models.ExerciseParameters
import com.google.mediapipe.examples.poselandmarker.models.StrokePhase
import com.google.mediapipe.examples.poselandmarker.services.JsonStrokeDetector
import com.google.mediapipe.examples.poselandmarker.services.MotionAnalyzer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MotionAnalyzerJsonTest {

    private lateinit var motionAnalyzer: MotionAnalyzer
    private val parameters = ExerciseParameters.forehandDrive()

    @Before
    fun setup() {
        motionAnalyzer = MotionAnalyzer(parameters)
    }

    @Test
    fun `test analyze strokes from forehand drive JSON`() {
        // Try multiple paths to find the JSON file (handles different working directories)
        val possiblePaths = listOf(
            "app/src/main/assets/Videos/forehand_drive_poses.json",
            "src/main/assets/Videos/forehand_drive_poses.json",
            "../app/src/main/assets/Videos/forehand_drive_poses.json"
        )
        
        val jsonFile = possiblePaths.map { File(it) }.find { it.exists() }
        assertNotNull("JSON file must exist. Tried: $possiblePaths", jsonFile)
        val jsonPath = jsonFile!!.absolutePath

        // 1. Load frames
        val frames = JsonTestUtils.loadFramesFromJson(jsonPath)
        assertFalse("Frames should not be empty", frames.isEmpty())

        // 2. Detect strokes
        val detector = JsonStrokeDetector()
        val detectionResult = detector.detectStrokes(frames)
        assertFalse("Should detect at least one stroke", detectionResult.strokes.isEmpty())

        println("Detected ${detectionResult.strokes.size} strokes")

        // 3. Analyze each stroke at the contact frame
        for (stroke in detectionResult.strokes) {
            val contactFrameIndex = stroke.contactFrame
            val contactFrame = frames.find { it.frameIndex == contactFrameIndex }
            
            assertNotNull("Contact frame $contactFrameIndex must exist", contactFrame)
            
            val landmarks = JsonTestUtils.toNormalizedLandmarks(contactFrame!!.landmarks)
            val analysisResult = motionAnalyzer.analyzeStroke(landmarks, StrokePhase.CONTACT)
            
            println("Stroke ${stroke.strokeIndex}: Score=${analysisResult.overallScore}%")
            println("  Feedback Items: ${analysisResult.feedbackItems.size}")
            for (item in analysisResult.feedbackItems) {
                println("    - [${item.type}] ${item.message} (Positive: ${item.isPositive})")
            }

            // High level assertions
            assertNotNull(analysisResult.feedbackItems)
            // Even if score is 100, we should have the "Positive" feedback item if score >= 90
            if (analysisResult.overallScore >= 90f) {
                assertTrue("High score should have positive feedback", 
                    analysisResult.feedbackItems.any { it.isPositive })
            }
        }
    }
}
