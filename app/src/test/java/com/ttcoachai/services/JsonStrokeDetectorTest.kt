package com.ttcoachai.services

import com.ttcoachai.models.StrokePhase
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for JsonStrokeDetector
 * Uses Robolectric to mock Android classes (Log, etc.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class JsonStrokeDetectorTest {

    private lateinit var detector: JsonStrokeDetector

    @Before
    fun setup() {
        detector = JsonStrokeDetector(StrokeDetectorConfig.FOREHAND)
    }

    @Test
    fun `detect strokes from forehand_drive_poses json - expects 5 strokes`() {
        // Load the JSON file from resources (path relative to app module root)
        val jsonFile = findJsonFile()
        assertNotNull("JSON file should exist", jsonFile)
        assertTrue("JSON file should exist at ${jsonFile!!.absolutePath}", jsonFile.exists())

        val jsonString = jsonFile.readText()
        val frames = parseJsonToFrames(jsonString)

        // Get interval from JSON
        val jsonRoot = JSONObject(jsonString)
        val intervalMs = jsonRoot.optLong("intervalMs", 100L)
        val totalFrames = jsonRoot.optInt("totalFrames", frames.size)

        println("JSON file: ${jsonFile.name}")
        println("Total frames: $totalFrames, interval: ${intervalMs}ms")

        // Run stroke detection
        val result = detector.detectStrokes(frames, intervalMs)

        // Log stroke details for debugging
        println("Detected ${result.strokes.size} strokes:")
        result.strokes.forEachIndexed { index, stroke ->
            println("  Stroke ${index + 1}: frames ${stroke.preparationStartFrame}-${stroke.returnEndFrame}, " +
                    "duration=${stroke.strokeDurationMs}ms, " +
                    "backswing=${String.format("%.3f", stroke.backswingMinValue)}, " +
                    "peak=${String.format("%.3f", stroke.forwardPeakValue)}")
        }

        // Verify results
        assertNotNull("Detection result should not be null", result)
        assertEquals("Should detect exactly 5 strokes", 5, result.strokes.size)
    }

    @Test
    fun `all frames should have phase info`() {
        val jsonFile = findJsonFile()
        if (jsonFile == null) {
            println("Skipping test - JSON file not found")
            return
        }

        val jsonString = jsonFile.readText()
        val frames = parseJsonToFrames(jsonString)
        val jsonRoot = JSONObject(jsonString)
        val intervalMs = jsonRoot.optLong("intervalMs", 100L)

        val result = detector.detectStrokes(frames, intervalMs)

        // Every frame should have phase info
        assertEquals("All frames should have phase info", frames.size, result.framePhases.size)

        // Count phases
        val phaseCounts = result.framePhases.groupBy { it.phase }.mapValues { it.value.size }
        println("Phase distribution:")
        phaseCounts.forEach { (phase, count) ->
            println("  ${phase.name}: $count frames")
        }

        // Verify we have active stroke phases (not just READY)
        assertTrue("Should have BACKSWING frames", phaseCounts.containsKey(StrokePhase.BACKSWING))
        assertTrue("Should have FORWARD_SWING frames", phaseCounts.containsKey(StrokePhase.FORWARD_SWING))
    }

    @Test
    fun `stroke phases should be in correct order`() {
        val jsonFile = findJsonFile()
        if (jsonFile == null) {
            println("Skipping test - JSON file not found")
            return
        }

        val jsonString = jsonFile.readText()
        val frames = parseJsonToFrames(jsonString)
        val result = detector.detectStrokes(frames, 100L)

        // For each stroke, verify phase order
        result.strokes.forEach { stroke ->
            assertTrue("Preparation should start before forward",
                stroke.preparationStartFrame <= stroke.forwardStartFrame)
            assertTrue("Forward should start before peak",
                stroke.forwardStartFrame <= stroke.forwardEndFrame)
            assertTrue("Forward end should be before return end",
                stroke.forwardEndFrame <= stroke.returnEndFrame)
        }
    }

    @Test
    fun `stroke metrics should be valid`() {
        val jsonFile = findJsonFile()
        if (jsonFile == null) {
            println("Skipping test - JSON file not found")
            return
        }

        val jsonString = jsonFile.readText()
        val frames = parseJsonToFrames(jsonString)
        val result = detector.detectStrokes(frames, 100L)

        result.strokes.forEach { stroke ->
            // Backswing should be less than peak
            assertTrue("Backswing (${stroke.backswingMinValue}) should be less than peak (${stroke.forwardPeakValue})",
                stroke.backswingMinValue < stroke.forwardPeakValue)

            // Duration should be positive
            assertTrue("Duration should be positive", stroke.strokeDurationMs > 0)

            // Peak velocity should be positive
            assertTrue("Peak velocity should be positive", stroke.peakVelocity > 0)
        }
    }

    @Test
    fun `getPhaseForFrame returns correct phase`() {
        val jsonFile = findJsonFile()
        if (jsonFile == null) {
            println("Skipping test - JSON file not found")
            return
        }

        val jsonString = jsonFile.readText()
        val frames = parseJsonToFrames(jsonString)
        val result = detector.detectStrokes(frames, 100L)

        // Frame 0 should typically be READY or early phase
        val phase0 = result.getPhaseForFrame(0)
        println("Frame 0 phase: ${phase0.name}")

        // Frame in the middle of first stroke should be active
        if (result.strokes.isNotEmpty()) {
            val firstStroke = result.strokes[0]
            val midFrame = (firstStroke.forwardStartFrame + firstStroke.forwardEndFrame) / 2
            val midPhase = result.getPhaseForFrame(midFrame)
            println("Frame $midFrame (mid-stroke) phase: ${midPhase.name}")
            assertTrue("Mid-stroke frame should be FORWARD_SWING or CONTACT",
                midPhase == StrokePhase.FORWARD_SWING || midPhase == StrokePhase.CONTACT)
        }
    }

    @Test
    fun `empty frames list returns empty result`() {
        val result = detector.detectStrokes(emptyList(), 100L)

        assertEquals("Should have 0 strokes", 0, result.strokes.size)
        assertEquals("Should have 0 frame phases", 0, result.framePhases.size)
        assertEquals("Total frames should be 0", 0, result.totalFrames)
    }

    @Test
    fun `detect strokes from forehand_drive2 json - expects 2 strokes`() {
        val jsonFile = findJsonFile("forehand_drive2_poses.json")
        assertNotNull("forehand_drive2_poses.json should exist", jsonFile)
        assertTrue("JSON file should exist at ${jsonFile!!.absolutePath}", jsonFile.exists())

        val jsonString = jsonFile.readText()
        val frames = parseJsonToFrames(jsonString)

        val jsonRoot = JSONObject(jsonString)
        val intervalMs = jsonRoot.optLong("intervalMs", 100L)
        val totalFrames = jsonRoot.optInt("totalFrames", frames.size)

        println("JSON file: ${jsonFile.name}")
        println("Total frames: $totalFrames, interval: ${intervalMs}ms")

        // Use default FOREHAND config - this video has good motion range
        val result = detector.detectStrokes(frames, intervalMs)

        println("Detected ${result.strokes.size} strokes:")
        result.strokes.forEachIndexed { index, stroke ->
            println("  Stroke ${index + 1}: frames ${stroke.preparationStartFrame}-${stroke.returnEndFrame}, " +
                    "duration=${stroke.strokeDurationMs}ms, " +
                    "backswing=${String.format("%.3f", stroke.backswingMinValue)}, " +
                    "peak=${String.format("%.3f", stroke.forwardPeakValue)}")
        }

        assertNotNull("Detection result should not be null", result)
        assertEquals("Should detect 2 strokes", 2, result.strokes.size)
        println("=== forehand_drive2_poses.json contains ${result.strokes.size} strokes ===")
    }

    @Test
    fun `detect strokes from ivan_poses json`() {
        val filename = "ivan_poses.json"
        val jsonFile = findJsonFile(filename)
        assertNotNull("$filename should exist", jsonFile)

        val jsonString = jsonFile!!.readText()
        val frames = parseJsonToFrames(jsonString)

        val jsonRoot = JSONObject(jsonString)
        val intervalMs = jsonRoot.optLong("intervalMs", 100L)
        
        println("JSON file: ${jsonFile.name}")
        println("Total frames: ${frames.size}, interval: ${intervalMs}ms")

        // Use custom strict config for Ivan to filter shallow strokes
        val strictConfig = StrokeDetectorConfig.FOREHAND.copy(backswingThreshold = 0.32f)
        val strictDetector = JsonStrokeDetector(strictConfig)
        val result = strictDetector.detectStrokes(frames, intervalMs)

        println("Detected ${result.strokes.size} strokes in Ivan's poses:")
        result.strokes.forEachIndexed { index, stroke ->
            println("  Stroke ${index + 1}: frames ${stroke.preparationStartFrame}-${stroke.returnEndFrame}, " +
                    "duration=${stroke.strokeDurationMs}ms, " +
                    "backswing=${String.format("%.3f", stroke.backswingMinValue)}, " +
                    "peak=${String.format("%.3f", stroke.forwardPeakValue)}")
        }
        
        // Based on the stricter detection logic, we expect 4 full strokes
        assertEquals("Should detect exactly 4 strokes in ivan_poses.json", 4, result.strokes.size)
        println("=== ivan_poses.json contains ${result.strokes.size} strokes ===")
    }

    @Test
    fun `detect strokes from forehand_drive_wrong_poses json - expects 4 strokes`() {
        val filename = "forehand_drive_wrong_poses.json"
        val jsonFile = findJsonFile(filename)
        assertNotNull("$filename should exist", jsonFile)

        val jsonString = jsonFile!!.readText()
        val frames = parseJsonToFrames(jsonString)

        val jsonRoot = JSONObject(jsonString)
        val intervalMs = jsonRoot.optLong("intervalMs", 100L)
        
        println("JSON file: ${jsonFile.name}")
        println("Total frames: ${frames.size}, interval: ${intervalMs}ms")

        val result = detector.detectStrokes(frames, intervalMs)

        println("Detected ${result.strokes.size} strokes:")
        result.strokes.forEachIndexed { index, stroke ->
            println("  Stroke ${index + 1}: frames ${stroke.preparationStartFrame}-${stroke.returnEndFrame}, " +
                    "duration=${stroke.strokeDurationMs}ms, " +
                    "backswing=${String.format("%.3f", stroke.backswingMinValue)}, " +
                    "peak=${String.format("%.3f", stroke.forwardPeakValue)}")
        }

        assertEquals("Should detect 4 strokes in wrong technique file (1 filtered as too slow)", 4, result.strokes.size)
    }

    @Test
    fun `analyze stroke values for ivan_poses`() {
        val filename = "ivan_poses.json"
        val jsonFile = findJsonFile(filename)
        if (jsonFile == null) return

        val jsonString = jsonFile.readText()
        val frames = parseJsonToFrames(jsonString)
        val result = detector.detectStrokes(frames, 100L)

        val sb = StringBuilder()
        sb.append("=== Stroke Analysis (Values) ===\n")
        sb.append("Config: Axis=${StrokeDetectorConfig.FOREHAND.trackingAxis}, Inv=${StrokeDetectorConfig.FOREHAND.invertDirection}\n")
        
        result.strokes.forEachIndexed { index, stroke ->
            sb.append("\nStroke ${index + 1}:\n")
            sb.append("  Frames: ${stroke.preparationStartFrame} -> ${stroke.returnEndFrame}\n")
            sb.append("  Total Duration: ${stroke.strokeDurationMs}ms\n")
            sb.append("  Backswing Min Value: ${stroke.backswingMinValue}\n")
            sb.append("  Forward Peak Value: ${stroke.forwardPeakValue}\n")
            sb.append("  Amplitude: ${stroke.forwardPeakValue - stroke.backswingMinValue}\n")
            sb.append("  Peak Velocity: ${stroke.peakVelocity}\n")
            
            // Calculate average Z (depth) variation
            var minZ = Float.MAX_VALUE
            var maxZ = Float.MIN_VALUE
            for (i in stroke.preparationStartFrame..stroke.returnEndFrame) {
                val z = frames[i].landmarks[16].z
                minZ = minOf(minZ, z)
                maxZ = maxOf(maxZ, z)
            }
            sb.append("  Z-Range (Depth): ${maxZ - minZ}\n")
            
            // Calculate Y variation (vertical)
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE
            for (i in stroke.preparationStartFrame..stroke.returnEndFrame) {
                val y = frames[i].landmarks[16].y
                minY = minOf(minY, y)
                maxY = maxOf(maxY, y)
            }
            sb.append("  Y-Range (Height): ${maxY - minY}\n")
        }
        
        val outFile = File("stroke_values.txt")
        outFile.writeText(sb.toString())
        println("Wrote values to ${outFile.absolutePath}")
    }

    /**
     * Find the JSON file - tries multiple paths since test working directory varies
     */
    private fun findJsonFile(filename: String = "forehand_drive_poses.json"): File? {
        val possiblePaths = listOf(
            "src/main/assets/Videos/$filename",
            "app/src/main/assets/Videos/$filename",
            "../app/src/main/assets/Videos/$filename",
            "debug_data/$filename",
            "../debug_data/$filename"
        )

        for (path in possiblePaths) {
            val file = File(path)
            if (file.exists()) {
                println("Found JSON file at: ${file.absolutePath}")
                return file
            }
        }

        println("JSON file not found. Tried paths:")
        possiblePaths.forEach { println("  - $it") }
        println("Current directory: ${File(".").absolutePath}")
        return null
    }

    /**
     * Parse JSON string to list of JsonPoseFrame
     */
    private fun parseJsonToFrames(jsonString: String): List<JsonPoseFrame> {
        val jsonRoot = JSONObject(jsonString)
        val framesArray = jsonRoot.getJSONArray("frames")
        val frames = mutableListOf<JsonPoseFrame>()

        for (i in 0 until framesArray.length()) {
            val frameObj = framesArray.getJSONObject(i)
            val frameIndex = frameObj.getInt("frameIndex")
            val timestampMs = frameObj.getLong("timestampMs")
            val landmarksArray = frameObj.getJSONArray("landmarks")

            val landmarks = mutableListOf<JsonLandmark>()
            for (j in 0 until landmarksArray.length()) {
                val lmObj = landmarksArray.getJSONObject(j)
                landmarks.add(
                    JsonLandmark(
                        index = lmObj.optInt("index", j),  // Use array index if not present
                        x = lmObj.getDouble("x").toFloat(),
                        y = lmObj.getDouble("y").toFloat(),
                        z = lmObj.getDouble("z").toFloat(),
                        visibility = lmObj.optDouble("visibility", 0.0).toFloat(),
                        presence = lmObj.optDouble("presence", 0.0).toFloat()
                    )
                )
            }

            frames.add(JsonPoseFrame(frameIndex, timestampMs, landmarks))
        }

        return frames
    }
}
