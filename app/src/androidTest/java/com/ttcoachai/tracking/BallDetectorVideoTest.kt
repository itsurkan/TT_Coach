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

/**
 * Instrumented integration test for BallDetector using a real pre-recorded video.
 *
 * Runs on-device where OpenCV native libs are available — exercises the real
 * HSV-threshold → morph → contour pipeline, not the JVM crash-fallback path.
 *
 * Asset: app/src/main/assets/Videos/forehand_drive.mp4
 *
 * Strategy:
 *  - Decode every Nth frame with MediaMetadataRetriever (no MediaCodec/GL needed).
 *  - Run BallDetector on each frame with a full-frame ROI.
 *  - Assert that at least one frame produces DETECTED (proves real detection works).
 *  - Assert that DETECTED results carry plausible confidence and radius values.
 *  - Assert that metadata (frameIndex, timestampMs) is always propagated correctly.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BallDetectorVideoTest {

    private lateinit var detector: BallDetector

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
        detector = BallDetector(BallDetector.BallColor.WHITE, expectedRadiusRange = 3..50)
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
        val frames = extractFrames("Videos/forehand_drive.mp4", FRAME_STEP_MS)
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
        val frames = extractFrames("Videos/forehand_drive.mp4", FRAME_STEP_MS)

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
        val frames = extractFrames("Videos/forehand_drive.mp4", FRAME_STEP_MS)

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
        val frames = extractFrames("Videos/forehand_drive.mp4", FRAME_STEP_MS)
        val (bitmap, ts) = frames.first()
        val roi = RegionOfInterest(x = 0, y = 0, width = bitmap.width, height = bitmap.height)
        detector.detect(bitmap, roi, frameIndex = 0, timestampMs = ts)
        bitmap.recycle()
        frames.drop(1).forEach { (b, _) -> b.recycle() }
        detector.release()  // must not throw
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
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
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
}
