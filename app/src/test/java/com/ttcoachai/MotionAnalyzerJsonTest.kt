package com.ttcoachai

import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.ExerciseParameters
import com.ttcoachai.shared.models.StrokePhase
import com.ttcoachai.shared.detection.JsonStrokeDetector
import com.ttcoachai.services.MotionAnalyzer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
    class MotionAnalyzerJsonTest {

    private lateinit var motionAnalyzer: MotionAnalyzer
    private val beginnerParameters = ExerciseParameters.forehandDriveBeginner()
    private val expertParameters = ExerciseParameters.forehandDrive()

    @Before
    fun setup() {
        motionAnalyzer = MotionAnalyzer(beginnerParameters)
    }

    private fun findJsonFile(filename: String): String {
        // Accept both legacy "_poses.json" suffix and the current shared fixture names.
        val candidateNames = listOf(filename, filename.removeSuffix("_poses.json") + ".json")
        val candidateDirs = listOf(
            "shared/src/commonTest/resources/fixtures",
            "../shared/src/commonTest/resources/fixtures",
            "app/src/main/assets/Videos",
            "src/main/assets/Videos",
            "../app/src/main/assets/Videos"
        )
        val file = candidateDirs.asSequence()
            .flatMap { dir -> candidateNames.asSequence().map { n -> File("$dir/$n") } }
            .find { it.exists() }
        assertNotNull("JSON file $filename must exist. Tried dirs: $candidateDirs names: $candidateNames", file)
        return file!!.absolutePath
    }

    @Test
    fun `test analyze strokes from forehand drive JSON with beginner parameters`() {
        val jsonPath = findJsonFile("forehand_drive_poses.json")

        // 1. Load frames
        val frames = JsonTestUtils.loadFramesFromJson(jsonPath)
        assertFalse("Frames should not be empty", frames.isEmpty())

        // 2. Detect strokes
        val detector = JsonStrokeDetector()
        val detectionResult = detector.detect(frames)
        assertFalse("Should detect at least one stroke", detectionResult.strokes.isEmpty())

        println("Detected ${detectionResult.strokes.size} strokes in correct technique file")

        // 3. Analyze each stroke at the contact frame
        for (stroke in detectionResult.strokes) {
            val contactFrame = frames.find { it.frameIndex == stroke.contactFrame }
            assertNotNull("Contact frame ${stroke.contactFrame} must exist", contactFrame)

            val analysisResult = motionAnalyzer.analyzeStroke(contactFrame!!.landmarks, StrokePhase.CONTACT)

            assertEquals("Stroke ${stroke.strokeIndex} should have 100% score with beginner params. Errors: ${analysisResult.errors}",
                100f, analysisResult.overallScore, 0.1f)
        }
    }

    @Test
    fun `test analyze wrong forehand drive technique`() {
        val jsonPath = findJsonFile("forehand_drive_wrong_poses.json")
        val expertAnalyzer = MotionAnalyzer(expertParameters)

        // 1. Load frames
        val frames = JsonTestUtils.loadFramesFromJson(jsonPath)

        // 2. Detect strokes
        val detector = JsonStrokeDetector()
        val detectionResult = detector.detect(frames)

        println("Detected ${detectionResult.strokes.size} strokes in wrong technique file")

        // 3. Analyze each stroke
        for (stroke in detectionResult.strokes) {
            val contactFrame = frames.find { it.frameIndex == stroke.contactFrame }
            assertNotNull(contactFrame)

            val result = expertAnalyzer.analyzeStroke(contactFrame!!.landmarks, StrokePhase.CONTACT)

            println("Stroke ${stroke.strokeIndex}: Score=${result.overallScore}%")
            println("  Errors: ${result.errors}")

            // Assert that elbow error is caught (error_elbow_close)
            val hasElbowError = result.feedbackItems.any { it.type == CorrectionType.ELBOW_POSITION && !it.isPositive }

            assertTrue("Stroke ${stroke.strokeIndex} should have elbow error", hasElbowError)

            // Log the elbow distance to see if we need a "too close" check
            println("  Elbow Body Distance: ${result.elbowBodyDistance}")
        }
    }
}
