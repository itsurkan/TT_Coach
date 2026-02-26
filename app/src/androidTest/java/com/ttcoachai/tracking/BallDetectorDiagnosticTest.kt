package com.ttcoachai.tracking

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Diagnostic test: dumps HSV stats from forehand_drive.mp4 on-device.
 * Run once, read logcat output to tune BallDetector HSV thresholds.
 *
 * adb logcat -s BallDiag
 */
@RunWith(AndroidJUnit4::class)
class BallDetectorDiagnosticTest {

    @Test
    fun dumpHsvStatsForVideoFrames() {
        val loaded = OpenCVLoader.initLocal()
        Log.i("BallDiag", "OpenCV loaded: $loaded")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val afd = context.assets.openFd("Videos/forehand_drive.mp4")
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()

        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        Log.i("BallDiag", "Duration: ${durationMs}ms")

        var posMs = 0L
        var frameIdx = 0
        while (posMs <= durationMs) {
            val rawBmp = retriever.getFrameAtTime(posMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (rawBmp == null) { posMs += 500L; continue }
            val bmp = rawBmp.copy(Bitmap.Config.ARGB_8888, false)
            if (rawBmp !== bmp) rawBmp.recycle()

            val src = Mat()
            val hsv = Mat()
            Utils.bitmapToMat(bmp, src)
            Imgproc.cvtColor(src, hsv, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)
            bmp.recycle()

            // White: use EXACT BallDetector thresholds
            val mask1 = Mat(); Core.inRange(hsv, Scalar(0.0, 0.0, 200.0), Scalar(180.0, 50.0, 255.0), mask1)   // exact BallDetector WHITE
            val mask2 = Mat(); Core.inRange(hsv, Scalar(0.0, 0.0, 120.0), Scalar(180.0, 100.0, 255.0), mask2)  // widened reference
            val mask3 = Mat(); Core.inRange(hsv, Scalar(0.0, 0.0, 200.0), Scalar(180.0, 50.0, 255.0), mask3)   // same as mask1 for consistency
            val n1 = Core.countNonZero(mask1)
            val n2 = Core.countNonZero(mask2)
            val n3 = Core.countNonZero(mask3)

            // Sample HSV at known ball location: x~53, y~750 in 720x1280
            val bx = 53; val by = 750
            val hsvAtBall = if (bx < src.cols() && by < src.rows()) hsv.get(by, bx) else null

            // Find contours in the EXACT BallDetector mask (mask1) to mirror production pipeline
            val contours = mutableListOf<org.opencv.core.MatOfPoint>()
            val kern = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(5.0, 5.0))
            val morphed = Mat()
            Imgproc.morphologyEx(mask1, morphed, Imgproc.MORPH_OPEN, kern)
            Imgproc.morphologyEx(morphed, morphed, Imgproc.MORPH_CLOSE, kern)
            Imgproc.findContours(morphed, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val hsvStr = if (hsvAtBall != null) "(${hsvAtBall[0].toInt()},${hsvAtBall[1].toInt()},${hsvAtBall[2].toInt()})" else "n/a"
            Log.i("BallDiag", "f$frameIdx t=${posMs}ms HSV@ball=$hsvStr w_orig=$n1 w_wide=$n2 contours=${contours.size}")

            // Log top 5 contours sorted by area
            contours.sortedByDescending { Imgproc.contourArea(it) }.take(5).forEach { cnt ->
                val area = Imgproc.contourArea(cnt)
                val perim = Imgproc.arcLength(org.opencv.core.MatOfPoint2f(*cnt.toArray()), true)
                val circ = if (perim > 0) 4.0 * Math.PI * area / (perim * perim) else 0.0
                val r = Math.sqrt(area / Math.PI)
                val M = Imgproc.moments(cnt)
                val cx = if (M.m00 != 0.0) (M.m10 / M.m00).toInt() else 0
                val cy = if (M.m00 != 0.0) (M.m01 / M.m00).toInt() else 0
                Log.i("BallDiag", "  cnt: area=${area.toInt()} r=${String.format("%.1f",r)} circ=${String.format("%.2f",circ)} center=($cx,$cy)")
            }

            src.release(); hsv.release(); mask1.release(); mask2.release(); mask3.release()
            morphed.release(); kern.release()
            contours.forEach { it.release() }
            posMs += 500L
            frameIdx++
        }

        retriever.release()
    }

    @Test
    fun runActualDetectorAndLogResult() {
        OpenCVLoader.initLocal()
        val detector = BallDetector(BallDetector.BallColor.WHITE, expectedRadiusRange = 3..50)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val afd = context.assets.openFd("Videos/forehand_drive.mp4")
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()

        // Sample a few frames
        var frameIdx = 0
        for (posMs in listOf(0L, 500L, 1000L, 2000L, 5000L)) {
            val raw = retriever.getFrameAtTime(posMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
            val bmp = raw.copy(Bitmap.Config.ARGB_8888, false)
            if (raw !== bmp) raw.recycle()

            val roi = com.ttcoachai.shared.models.RegionOfInterest(0, 0, bmp.width, bmp.height)
            val result = detector.detect(bmp, roi, frameIdx, posMs)
            bmp.recycle()

            Log.i("BallDiag", "detect t=${posMs}ms → status=${result.status} conf=${result.confidence} r=${result.radiusPx} x=${result.x} y=${result.y}")
            frameIdx++
        }

        retriever.release()
        detector.release()
    }
}
