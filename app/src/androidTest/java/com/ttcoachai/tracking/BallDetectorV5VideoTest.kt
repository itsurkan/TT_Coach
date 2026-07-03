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
 * Instrumented test for BallDetectorV5 (TFLite + motion-first).
 *
 * Runs on-device where both OpenCV native libs and the TFLite model are available.
 * Processes every video in [EXPORT_VIDEOS] and writes `<name>_ball_v5.json` to
 * the app's external files directory.
 *
 * Pull results with:
 *   adb pull /sdcard/Android/data/com.ttcoachai/files/ .
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BallDetectorV5VideoTest {

    private lateinit var detector: BallDetectorV5

    private val FRAME_STEP_MS = 100L

    @Before
    fun setUp() {
        OpenCVLoader.initLocal()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        detector = BallDetectorV5(
            context = context,
            confidenceThreshold = 0.5f,
            expectedRadiusRange = 3..35
        )
    }

    @After
    fun tearDown() {
        detector.release()
    }

    // -------------------------------------------------------------------------
    // JSON export — main test
    // -------------------------------------------------------------------------

    @Test
    fun exportsBallDetectionsToJson() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outDir = context.getExternalFilesDir(null)
            ?: error("External storage not available")

        val singleVideo = InstrumentationRegistry.getArguments().getString("videoName")
        val videos = if (singleVideo != null) listOf(singleVideo) else EXPORT_VIDEOS

        for (videoName in videos) {
            detector.reset()
            val assetPath = videoAssetPath(videoName)
            val baseName = videoName.substringBeforeLast('.')
            val outFile = File(outDir, "${baseName}_ball_v5.json")

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
            sb.appendLine("  \"detector\": \"V5-TFLite\",")
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
                    println("BallDetectorV5VideoTest: $videoName frame $frameIndex / ~$totalFrames")
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

            val json = sb.toString().let { raw ->
                raw.replace(
                    "  \"exportTimestamp\":",
                    "  \"totalFrames\": $frameIndex,\n  \"exportTimestamp\":"
                )
            }

            val fullJson = "$json\n  ]\n}"
            outFile.writeText(fullJson)

            println("BallDetectorV5VideoTest: wrote ${outFile.absolutePath} ($frameIndex frames)")
        }
    }

    // -------------------------------------------------------------------------
    // Basic detection sanity check
    // -------------------------------------------------------------------------

    @Test
    fun detectsBallInAtLeastOneFrameOfForehandDriveVideo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val frames = extractFrames(videoAssetPath("forehand_drive.mp4"), FRAME_STEP_MS)
        assertTrue("Video should have at least 5 frames", frames.size >= 5)

        var detectedCount = 0
        frames.forEachIndexed { i, (bitmap, timestampMs) ->
            val roi = RegionOfInterest(x = 0, y = 0, width = bitmap.width, height = bitmap.height)
            val result = detector.detect(bitmap, roi, frameIndex = i, timestampMs = timestampMs)
            bitmap.recycle()
            if (result.status == BallDetectionStatus.DETECTED) detectedCount++
        }

        assertTrue(
            "Expected at least 1 DETECTED frame, got $detectedCount out of ${frames.size}",
            detectedCount >= 1
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun extractFrames(assetPath: String, stepMs: Long): List<Pair<Bitmap, Long>> {
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
                posMs * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            )
            if (bitmap != null) {
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
        fun videoAssetPath(videoName: String): String {
            val base = videoName.substringBeforeLast('.')
            return "Videos/$base/$videoName"
        }

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
