package com.ttcoachai.tracking

import android.graphics.Bitmap
import android.util.Log
import com.ttcoachai.shared.models.BallDetection
import com.ttcoachai.shared.models.BallDetectionStatus
import com.ttcoachai.shared.models.RegionOfInterest
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detects a table tennis ball in a bitmap frame using OpenCV HSV color thresholding,
 * morphological filtering, and contour analysis.
 *
 * Pre-allocates Mat objects to minimise GC pressure (research R5).
 * Thread-safe: all state is confined to pre-allocated Mats accessed from the background executor.
 *
 * Usage:
 *   val detector = BallDetector(BallDetector.BallColor.WHITE)
 *   val result = detector.detect(bitmap, roi, frameIndex, timestampMs)
 *   // ... when done:
 *   detector.release()
 */
class BallDetector(
    private val ballColor: BallColor = BallColor.WHITE,
    private val expectedRadiusRange: IntRange = 4..25
) {

    enum class BallColor { WHITE, ORANGE }

    companion object {
        private const val TAG = "BallDetector"
        private const val MIN_CIRCULARITY = 0.20f   // Allow heavily motion-blurred streaks
        private const val CONFIDENCE_CIRCULARITY_WEIGHT = 0.6f
        private const val CONFIDENCE_SIZE_WEIGHT = 0.4f

        // HSV ranges per research R2
        // Orange: H 5-25, S 100-255, V 100-255
        private val ORANGE_LOWER = Scalar(5.0, 100.0, 100.0)
        private val ORANGE_UPPER = Scalar(25.0, 255.0, 255.0)

        // White: H 0-180, S 0-50, V 200-255
        private val WHITE_LOWER = Scalar(0.0, 0.0, 200.0)
        private val WHITE_UPPER = Scalar(180.0, 50.0, 255.0)
    }

    // Pre-allocated Mats (R5). Nullable so that OpenCV JNI is not called during construction,
    // allowing the class to be instantiated in unit-test environments where native libs
    // are unavailable. They are allocated on first use inside detectInternal() which is
    // already guarded by the try/catch in detect().
    private var srcMat: Mat? = null
    private var hsvMat: Mat? = null
    private var maskMat: Mat? = null
    private var morphMat: Mat? = null
    private var morphKernel: Mat? = null

    /**
     * Detect the ball in [bitmap] within [roi].
     *
     * @param bitmap Full frame bitmap (ARGB_8888)
     * @param roi    Region of interest (pixel coordinates in the full frame)
     * @param frameIndex Sequential frame number
     * @param timestampMs Frame capture timestamp in milliseconds
     * @return BallDetection with normalised (0-1) coordinates relative to the full frame
     */
    fun detect(
        bitmap: Bitmap,
        roi: RegionOfInterest,
        frameIndex: Int,
        timestampMs: Long
    ): BallDetection {
        return try {
            detectInternal(bitmap, roi, frameIndex, timestampMs)
        } catch (e: Throwable) {
            // Catches both Exception and Error (e.g. UnsatisfiedLinkError when native libs absent)
            Log.e(TAG, "Ball detection failed on frame $frameIndex", e)
            notDetected(frameIndex, timestampMs)
        }
    }

    /** Release pre-allocated OpenCV Mat objects. Call when detector is no longer needed. */
    fun release() {
        srcMat?.release()
        hsvMat?.release()
        maskMat?.release()
        morphMat?.release()
        morphKernel?.release()
        srcMat = null
        hsvMat = null
        maskMat = null
        morphMat = null
        morphKernel = null
    }

    // --- Internal pipeline ---

    private fun detectInternal(
        bitmap: Bitmap,
        roi: RegionOfInterest,
        frameIndex: Int,
        timestampMs: Long
    ): BallDetection {
        val frameWidth = bitmap.width
        val frameHeight = bitmap.height

        // Allocate Mats on first use (deferred to keep constructor OpenCV-free)
        if (srcMat == null)      srcMat      = Mat()
        if (hsvMat == null)      hsvMat      = Mat()
        if (maskMat == null)     maskMat     = Mat()
        if (morphMat == null)    morphMat    = Mat()
        if (morphKernel == null) morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))

        val src     = srcMat!!
        val hsv     = hsvMat!!
        val mask    = maskMat!!
        val morph   = morphMat!!
        val kernel  = morphKernel!!

        // 1. Bitmap → Mat
        Utils.bitmapToMat(bitmap, src)

        // 2. Clamp ROI to frame bounds
        val roiX = roi.x.coerceIn(0, frameWidth - 1)
        val roiY = roi.y.coerceIn(0, frameHeight - 1)
        val roiW = roi.width.coerceAtMost(frameWidth - roiX)
        val roiH = roi.height.coerceAtMost(frameHeight - roiY)

        if (roiW <= 0 || roiH <= 0) return notDetected(frameIndex, timestampMs)

        // 3. Zero-copy sub-matrix crop (R4)
        val roiRect = Rect(roiX, roiY, roiW, roiH)
        val roiMat = Mat(src, roiRect)

        // 4. Convert to HSV
        Imgproc.cvtColor(roiMat, hsv, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)

        // 5. Color threshold
        val (lower, upper) = when (ballColor) {
            BallColor.ORANGE -> ORANGE_LOWER to ORANGE_UPPER
            BallColor.WHITE  -> WHITE_LOWER  to WHITE_UPPER
        }
        Core.inRange(hsv, lower, upper, mask)

        // 6. Morphological open (remove noise) then close (fill small holes)
        Imgproc.morphologyEx(mask, morph, Imgproc.MORPH_OPEN, kernel)
        Imgproc.morphologyEx(morph, morph, Imgproc.MORPH_CLOSE, kernel)

        // 7. Find contours
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(
            morph,
            contours,
            Mat(),
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        // 8. Filter by area and circularity, pick best candidate
        val minArea = PI * expectedRadiusRange.first * expectedRadiusRange.first
        val maxArea = PI * expectedRadiusRange.last * expectedRadiusRange.last

        var bestContour: MatOfPoint? = null
        var bestScore = -1f

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < minArea || area > maxArea) continue

            val perimeter = Imgproc.arcLength(org.opencv.core.MatOfPoint2f(*contour.toArray()), true)
            if (perimeter <= 0) continue

            val circularity = (4.0 * PI * area / (perimeter * perimeter)).toFloat()
            if (circularity < MIN_CIRCULARITY) continue

            // Score = weighted combination of circularity and area-within-range-centrality
            val radiusEstimate = sqrt(area / PI).toFloat()
            val radiusMid = (expectedRadiusRange.first + expectedRadiusRange.last) / 2f
            val sizeScore = 1f - abs(radiusEstimate - radiusMid) / radiusMid
            val score = CONFIDENCE_CIRCULARITY_WEIGHT * circularity + CONFIDENCE_SIZE_WEIGHT * sizeScore.coerceIn(0f, 1f)

            if (score > bestScore) {
                bestScore = score
                bestContour = contour
            }
        }

        roiMat.release()

        if (bestContour == null) {
            contours.forEach { it.release() }
            return notDetected(frameIndex, timestampMs)
        }

        // 9. Compute centroid and radius in ROI coordinates (before releasing contours)
        val moments = Imgproc.moments(bestContour)
        val bestArea = Imgproc.contourArea(bestContour)
        contours.forEach { it.release() }
        if (moments.m00 == 0.0) return notDetected(frameIndex, timestampMs)

        val cxRoi = (moments.m10 / moments.m00).toFloat()
        val cyRoi = (moments.m01 / moments.m00).toFloat()
        val radiusPx = sqrt(bestArea / PI).toFloat()

        // 10. Translate from ROI coords to full-frame normalised [0,1]
        val cxFrame = (roiX + cxRoi) / frameWidth.toFloat()
        val cyFrame = (roiY + cyRoi) / frameHeight.toFloat()

        return BallDetection(
            x = cxFrame.coerceIn(0f, 1f),
            y = cyFrame.coerceIn(0f, 1f),
            confidence = bestScore.coerceIn(0f, 1f),
            radiusPx = radiusPx,
            frameIndex = frameIndex,
            timestampMs = timestampMs,
            status = BallDetectionStatus.DETECTED
        )
    }

    private fun notDetected(frameIndex: Int, timestampMs: Long) = BallDetection(
        x = 0f, y = 0f, confidence = 0f, radiusPx = 0f,
        frameIndex = frameIndex, timestampMs = timestampMs,
        status = BallDetectionStatus.NOT_DETECTED
    )
}
