package com.ttcoachai.tracking

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.ttcoachai.shared.models.BallDetectionStatus
import com.ttcoachai.shared.models.RegionOfInterest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File

/**
 * Instrumented integration test for BallDetector using a real pre-recorded video.
 *
 * Runs on-device where OpenCV native libs are available — exercises the real
 * HSV-threshold → morph → contour pipeline, not the JVM crash-fallback path.
 *
 * Asset: app/src/main/assets/Videos/forehand_drive.mp4
 *
 * Strategy:
 *  - Decode every Nth frame with MediaMetadataRetriever (no MediaCodec/GL needed            "IMG_6370.MP4",
        ).
 *  - Run BallDetector on each frame with a full-frame ROI.
 *  - Assert that at least one frame produces DETECTED (proves real detection works).
 *  - Assert that DETECTED results carry plausible confidence and radius values.
 *  - Assert that metadata (frameIndex, timestampMs) is always propagated correctly.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BallDetectorVideoTest {

    private lateinit var detector: BallDetectorV3

    // Sample every FRAME_STEP_MS milliseconds — coarse enough to be fast,
    // fine enough to catch a fast-moving ball.
    private val FRAME_STEP_MS = 100L

    // At least this many DETECTED frames expected from a forehand_drive clip.
    private val MIN_DETECTED_FRAMES = 1

    @Before
    fun setUp() {
        // OpenCV native library must be loaded before any Mat/Imgproc calls.
        // Without this the detector silently falls back to NOT_DETECTED on every frame.
        OpenCVLoader.initLocal()

        // forehand_drive.mp4: white ball, heavily motion-blurred into elongated streaks.
        // On-device diagnostics show blob radius ~30px at 720x1280 during fast strokes.
        // MIN_CIRCULARITY is 0.20 to accept motion-blur streaks.
        detector = BallDetectorV3(BallDetectorV3.BallColor.WHITE, expectedRadiusRange = 3..35)
    }

    @After
    fun tearDown() {
        detector.release()
    }

    // -------------------------------------------------------------------------
    // Main detection test
    // -------------------------------------------------------------------------

    @Test
    fun detectsBallInAtLeastOneFrameOfForehandDriveVideo() {
        val frames = extractFrames(videoAssetPath("forehand_drive.mp4"), FRAME_STEP_MS)
        assertTrue("Video should have at least 5 frames", frames.size >= 5)

        var detectedCount = 0
        val results = frames.mapIndexed { i, (bitmap, timestampMs) ->
            // Full-frame ROI: the ball in forehand_drive.mp4 sits near x=53, which is
            // just outside the default ROI (starts at x=72 for 720px-wide frames).
            val roi = RegionOfInterest(x = 0, y = 0, width = bitmap.width, height = bitmap.height)
            val result = detector.detect(bitmap, roi, frameIndex = i, timestampMs = timestampMs)
            bitmap.recycle()
            if (result.status == BallDetectionStatus.DETECTED) detectedCount++
            result
        }

        // At least one frame must contain the ball
        assertTrue(
            "Expected at least $MIN_DETECTED_FRAMES DETECTED frame(s), got $detectedCount " +
                    "out of ${frames.size} sampled frames",
            detectedCount >= MIN_DETECTED_FRAMES
        )

        // Every result must have correct frameIndex and timestampMs
        results.forEachIndexed { i, result ->
            assertEquals("frameIndex mismatch at position $i", i, result.frameIndex)
        }
    }

    // -------------------------------------------------------------------------
    // Quality assertions on detected frames
    // -------------------------------------------------------------------------

    @Test
    fun detectedFramesHavePlausibleConfidenceAndRadius() {
        val frames = extractFrames(videoAssetPath("forehand_drive.mp4"), FRAME_STEP_MS)

        val detectedResults = frames.mapIndexed { i, (bitmap, timestampMs) ->
            val roi = RegionOfInterest(x = 0, y = 0, width = bitmap.width, height = bitmap.height)
            val result = detector.detect(bitmap, roi, frameIndex = i, timestampMs = timestampMs)
            bitmap.recycle()
            result
        }.filter { it.status == BallDetectionStatus.DETECTED }

        // Only assert quality when we actually found something
        if (detectedResults.isEmpty()) return  // detection test above will fail first

        for (result in detectedResults) {
            assertTrue(
                "Confidence must be in (0, 1], got ${result.confidence}",
                result.confidence > 0f && result.confidence <= 1f
            )
            assertTrue(
                "radiusPx must be positive, got ${result.radiusPx}",
                result.radiusPx > 0f
            )
            assertTrue(
                "x must be normalised [0,1], got ${result.x}",
                result.x in 0f..1f
            )
            assertTrue(
                "y must be normalised [0,1], got ${result.y}",
                result.y in 0f..1f
            )
        }
    }

    // -------------------------------------------------------------------------
    // Metadata propagation
    // -------------------------------------------------------------------------

    @Test
    fun timestampMsPropagatedCorrectlyForAllFrames() {
        val frames = extractFrames(videoAssetPath("forehand_drive.mp4"), FRAME_STEP_MS)

        frames.forEachIndexed { i, (bitmap, timestampMs) ->
            val roi = RegionOfInterest(x = 0, y = 0, width = bitmap.width, height = bitmap.height)
            val result = detector.detect(bitmap, roi, frameIndex = i, timestampMs = timestampMs)
            bitmap.recycle()

            assertEquals(
                "timestampMs mismatch at frame $i: expected $timestampMs, got ${result.timestampMs}",
                timestampMs, result.timestampMs
            )
        }
    }

    // -------------------------------------------------------------------------
    // Release safety
    // -------------------------------------------------------------------------

    @Test
    fun releaseAfterVideoProcessingDoesNotThrow() {
        val frames = extractFrames(videoAssetPath("forehand_drive.mp4"), FRAME_STEP_MS)
        val (bitmap, ts) = frames.first()
        val roi = RegionOfInterest(x = 0, y = 0, width = bitmap.width, height = bitmap.height)
        detector.detect(bitmap, roi, frameIndex = 0, timestampMs = ts)
        bitmap.recycle()
        frames.drop(1).forEach { (b, _) -> b.recycle() }
        detector.release()  // must not throw
    }

    // -------------------------------------------------------------------------
    // JSON export
    // -------------------------------------------------------------------------

    /**
     * Runs ball detection on every video in [EXPORT_VIDEOS] and writes a
     * `<name>_ball.json` file to the app's external files directory.
     *
     * The output schema mirrors the poses JSON:
     * ```json
     * {
     *   "videoName": "forehand_drive.mp4",
     *   "intervalMs": 100,
     *   "totalFrames": 73,
     *   "videoDurationMs": 7254,
     *   "videoWidth": 720,
     *   "videoHeight": 1280,
     *   "exportTimestamp": 1234567890,
     *   "frames": [
     *     {
     *       "frameIndex": 0,
     *       "timestampMs": 0,
     *       "ball": { "x": 0.12, "y": 0.45, "radiusPx": 22.5, "confidence": 0.87, "status": "DETECTED" }
     *       // or "ball": null when NOT_DETECTED / OUT_OF_FRAME
     *     }
     *   ]
     * }
     * ```
     *
     * After the test runs, pull the files with:
     *   adb pull /sdcard/Android/data/com.ttcoachai/files/ .
     */
    @Test
    fun exportsBallDetectionsToJson() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outDir = context.getExternalFilesDir(null)
            ?: error("External storage not available")

        // If -e videoName <name> was passed via adb instrument, process only that video.
        // Otherwise fall back to the full EXPORT_VIDEOS list.
        val singleVideo = InstrumentationRegistry.getArguments().getString("videoName")
        val videos = if (singleVideo != null) listOf(singleVideo) else EXPORT_VIDEOS

        for (videoName in videos) {
            detector.reset()  // clear prevGray so each video starts fresh
            val assetPath = videoAssetPath(videoName)
            val baseName = videoName.substringBeforeLast('.')
            val outFile = File(outDir, "${baseName}_ball.json")

            val retriever = MediaMetadataRetriever()
            val afd = context.assets.openFd(assetPath)
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0

            val sb = StringBuilder()
            sb.appendLine("{")
            sb.appendLine("  \"videoName\": \"$videoName\",")
            sb.appendLine("  \"intervalMs\": $FRAME_STEP_MS,")
            sb.appendLine("  \"videoDurationMs\": $durationMs,")
            sb.appendLine("  \"videoWidth\": $width,")
            sb.appendLine("  \"videoHeight\": $height,")
            sb.appendLine("  \"exportTimestamp\": ${System.currentTimeMillis()},")
            sb.appendLine("  \"frames\": [")

            val totalFrames = ((durationMs / FRAME_STEP_MS) + 1).toInt()
            var posMs = 0L
            var frameIndex = 0
            var firstFrame = true
            while (posMs <= durationMs) {
                if (frameIndex % 10 == 0) {
                    println("BallDetectorVideoTest: $videoName frame $frameIndex / ~$totalFrames")
                }
                val bitmap = retriever.getFrameAtTime(
                    posMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                if (bitmap != null) {
                    val argb = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    if (argb !== bitmap) bitmap.recycle()

                    val roi = RegionOfInterest(x = 0, y = 0, width = argb.width, height = argb.height)
                    val result = detector.detect(argb, roi, frameIndex = frameIndex, timestampMs = posMs)
                    argb.recycle()

                    if (!firstFrame) sb.appendLine("    ,")
                    firstFrame = false

                    sb.appendLine("    {")
                    sb.appendLine("      \"frameIndex\": $frameIndex,")
                    sb.appendLine("      \"timestampMs\": $posMs,")
                    if (result.status == BallDetectionStatus.DETECTED) {
                        sb.appendLine("      \"ball\": {")
                        sb.appendLine("        \"x\": ${result.x},")
                        sb.appendLine("        \"y\": ${result.y},")
                        sb.appendLine("        \"radiusPx\": ${result.radiusPx},")
                        sb.appendLine("        \"confidence\": ${result.confidence},")
                        sb.appendLine("        \"status\": \"DETECTED\"")
                        sb.append("      }")
                    } else {
                        sb.append("      \"ball\": null")
                    }
                    sb.appendLine()
                    sb.append("    }")

                    frameIndex++
                }
                posMs += FRAME_STEP_MS
            }

            retriever.release()

            // Write totalFrames now that we know the count
            val json = sb.toString()
                .let { raw ->
                    // Inject totalFrames after exportTimestamp line
                    raw.replace(
                        "  \"exportTimestamp\":",
                        "  \"totalFrames\": $frameIndex,\n  \"exportTimestamp\":"
                    )
                }

            val fullJson = "$json\n  ]\n}"
            outFile.writeText(fullJson)

            println("BallDetectorVideoTest: wrote ${outFile.absolutePath} ($frameIndex frames)")
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Opens [assetPath] from the app's assets and extracts frames every [stepMs] milliseconds
     * using [MediaMetadataRetriever]. Returns a list of (Bitmap, timestampMs) pairs.
     *
     * Uses a file descriptor because MediaMetadataRetriever can work directly with
     * AssetFileDescriptor — no temp file needed.
     */
    private fun extractFrames(assetPath: String, stepMs: Long): List<Pair<Bitmap, Long>> {
        // targetContext = the app under test, which carries the main assets (Videos/).
        // context = the instrumentation test APK, which does NOT have the app's assets.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val afd = context.assets.openFd(assetPath)

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()

        val durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L

        val frames = mutableListOf<Pair<Bitmap, Long>>()
        var posMs = 0L
        while (posMs <= durationMs) {
            val bitmap = retriever.getFrameAtTime(
                posMs * 1_000L,  // microseconds
                MediaMetadataRetriever.OPTION_CLOSEST
            )
            if (bitmap != null) {
                // Ensure ARGB_8888 — BallDetector requires it
                val argb = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                if (argb !== bitmap) bitmap.recycle()
                frames.add(argb to posMs)
            }
            posMs += stepMs
        }

        retriever.release()
        return frames
    }

    companion object {
        /** Returns the asset path for a video in its per-video subfolder. */
        fun videoAssetPath(videoName: String): String {
            val base = videoName.substringBeforeLast('.')
            return "Videos/$base/$videoName"
        }

        /** Videos to process in [exportsBallDetectionsToJson]. Add more names here as needed. */
        private val EXPORT_VIDEOS = listOf(
            "forehand_drive.mp4",
            "forehand_drive_wrong.mp4",
            "forehand_drive2.mp4",
            "ivan.mp4",
            "video_2026-03-01_13-59-49.mp4",
                "IMG_6370.MP4",
                "project_9.mp4",
        )
    }
}
