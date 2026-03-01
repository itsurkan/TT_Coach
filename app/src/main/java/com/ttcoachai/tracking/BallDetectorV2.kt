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
 * BallDetectorV2 — HSV color thresholding + frame-differencing motion mask.
 *
 * Identical pipeline to BallDetector up through morphological filtering.
 * After morphology, a motion mask is computed from the absolute difference between
 * the current and previous grayscale frames and ANDed with the HSV result.
 * This eliminates static bright objects (wall outlets, windows) that share the
 * ball's color but never move.
 *
 * First frame has no previous → motion filter is skipped, falls back to HSV-only.
 * Subsequent frames benefit from the combined filter.
 *
 * API is identical to BallDetector — drop-in replacement.
 */
class BallDetectorV2(
    private val ballColor: BallColor = BallColor.WHITE,
    private val expectedRadiusRange: IntRange = 4..25
) {

    enum class BallColor { WHITE, ORANGE }

    companion object {
        private const val TAG = "BallDetectorV2"
        private const val MIN_CIRCULARITY = 0.20f
        private const val CONFIDENCE_CIRCULARITY_WEIGHT = 0.6f
        private const val CONFIDENCE_SIZE_WEIGHT = 0.4f

        // HSV ranges
        private val ORANGE_LOWER = Scalar(5.0, 100.0, 100.0)
        private val ORANGE_UPPER = Scalar(25.0, 255.0, 255.0)
        private val WHITE_LOWER  = Scalar(0.0, 0.0, 200.0)
        private val WHITE_UPPER  = Scalar(180.0, 50.0, 255.0)

        // Motion mask parameters
        private const val MOTION_THRESHOLD = 25.0   // min pixel-change (0–255) to count as motion
        private const val MOTION_DILATE_PX = 25.0   // dilation radius — bridges gap from fast ball travel
    }

    // HSV pipeline Mats (same as BallDetector)
    private var srcMat:      Mat? = null
    private var hsvMat:      Mat? = null
    private var maskMat:     Mat? = null
    private var morphMat:    Mat? = null
    private var morphKernel: Mat? = null

    // Motion-mask Mats (new in V2)
    private var grayMat:      Mat? = null   // current frame grayscale (full frame)
    private var prevGrayMat:  Mat? = null   // previous frame grayscale (full frame)
    private var diffMat:      Mat? = null   // absdiff result (ROI-sized, reused each frame)
    private var motionKernel: Mat? = null   // large ellipse kernel for dilation

    fun detect(
        bitmap: Bitmap,
        roi: RegionOfInterest,
        frameIndex: Int,
        timestampMs: Long
    ): BallDetection {
        return try {
            detectInternal(bitmap, roi, frameIndex, timestampMs)
        } catch (e: Throwable) {
            Log.e(TAG, "Ball detection failed on frame $frameIndex", e)
            notDetected(frameIndex, timestampMs)
        }
    }

    fun release() {
        srcMat?.release();      srcMat      = null
        hsvMat?.release();      hsvMat      = null
        maskMat?.release();     maskMat     = null
        morphMat?.release();    morphMat    = null
        morphKernel?.release(); morphKernel = null
        grayMat?.release();     grayMat     = null
        prevGrayMat?.release(); prevGrayMat = null
        diffMat?.release();     diffMat     = null
        motionKernel?.release(); motionKernel = null
    }

    // --- Internal pipeline ---

    private fun detectInternal(
        bitmap: Bitmap,
        roi: RegionOfInterest,
        frameIndex: Int,
        timestampMs: Long
    ): BallDetection {
        val frameWidth  = bitmap.width
        val frameHeight = bitmap.height

        // Lazy Mat allocation
        if (srcMat      == null) srcMat      = Mat()
        if (hsvMat      == null) hsvMat      = Mat()
        if (maskMat     == null) maskMat     = Mat()
        if (morphMat    == null) morphMat    = Mat()
        if (morphKernel == null) morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        if (grayMat     == null) grayMat     = Mat()
        if (prevGrayMat == null) prevGrayMat = Mat()
        if (diffMat     == null) diffMat     = Mat()
        if (motionKernel == null) motionKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE, Size(MOTION_DILATE_PX, MOTION_DILATE_PX)
        )

        val src    = srcMat!!
        val hsv    = hsvMat!!
        val mask   = maskMat!!
        val morph  = morphMat!!
        val kernel = morphKernel!!

        // 1. Bitmap → Mat
        Utils.bitmapToMat(bitmap, src)

        // 2. Clamp ROI to frame bounds
        val roiX = roi.x.coerceIn(0, frameWidth - 1)
        val roiY = roi.y.coerceIn(0, frameHeight - 1)
        val roiW = roi.width.coerceAtMost(frameWidth - roiX)
        val roiH = roi.height.coerceAtMost(frameHeight - roiY)

        if (roiW <= 0 || roiH <= 0) return notDetected(frameIndex, timestampMs)

        // 3. Zero-copy sub-matrix crop
        val roiRect = Rect(roiX, roiY, roiW, roiH)
        val roiMat  = Mat(src, roiRect)

        // 4. Convert to HSV
        Imgproc.cvtColor(roiMat, hsv, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)

        // 5. Color threshold
        val (lower, upper) = when (ballColor) {
            BallColor.ORANGE -> ORANGE_LOWER to ORANGE_UPPER
            BallColor.WHITE  -> WHITE_LOWER  to WHITE_UPPER
        }
        Core.inRange(hsv, lower, upper, mask)

        // 6. Morphological open then close
        Imgproc.morphologyEx(mask, morph, Imgproc.MORPH_OPEN,  kernel)
        Imgproc.morphologyEx(morph, morph, Imgproc.MORPH_CLOSE, kernel)

        // 6.5 Frame-differencing motion mask
        //   Convert full frame to grayscale, crop both current and previous to ROI,
        //   compute absdiff, threshold, dilate, AND with HSV morph result.
        //   Skipped on first frame (prevGrayMat is empty) → falls back to HSV-only.
        Imgproc.cvtColor(src, grayMat!!, Imgproc.COLOR_RGBA2GRAY)
        if (!prevGrayMat!!.empty()) {
            val currRoi = Mat(grayMat!!, roiRect)
            val prevRoi = Mat(prevGrayMat!!, roiRect)

            Core.absdiff(currRoi, prevRoi, diffMat!!)
            Imgproc.threshold(diffMat!!, diffMat!!, MOTION_THRESHOLD, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.dilate(diffMat!!, diffMat!!, motionKernel!!)

            Core.bitwise_and(morph, diffMat!!, morph)

            currRoi.release()
            prevRoi.release()
        }
        grayMat!!.copyTo(prevGrayMat!!)

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

            val radiusEstimate = sqrt(area / PI).toFloat()
            val radiusMid      = (expectedRadiusRange.first + expectedRadiusRange.last) / 2f
            val sizeScore      = 1f - abs(radiusEstimate - radiusMid) / radiusMid
            val score          = CONFIDENCE_CIRCULARITY_WEIGHT * circularity +
                                 CONFIDENCE_SIZE_WEIGHT * sizeScore.coerceIn(0f, 1f)

            if (score > bestScore) {
                bestScore    = score
                bestContour  = contour
            }
        }

        roiMat.release()

        if (bestContour == null) {
            contours.forEach { it.release() }
            return notDetected(frameIndex, timestampMs)
        }

        // 9. Centroid and radius (compute before releasing contours)
        val moments  = Imgproc.moments(bestContour)
        val bestArea = Imgproc.contourArea(bestContour)
        contours.forEach { it.release() }
        if (moments.m00 == 0.0) return notDetected(frameIndex, timestampMs)

        val cxRoi    = (moments.m10 / moments.m00).toFloat()
        val cyRoi    = (moments.m01 / moments.m00).toFloat()
        val radiusPx = sqrt(bestArea / PI).toFloat()

        // 10. Translate ROI → full-frame normalised [0,1]
        val cxFrame = (roiX + cxRoi) / frameWidth.toFloat()
        val cyFrame = (roiY + cyRoi) / frameHeight.toFloat()

        return BallDetection(
            x          = cxFrame.coerceIn(0f, 1f),
            y          = cyFrame.coerceIn(0f, 1f),
            confidence = bestScore.coerceIn(0f, 1f),
            radiusPx   = radiusPx,
            frameIndex = frameIndex,
            timestampMs = timestampMs,
            status     = BallDetectionStatus.DETECTED
        )
    }

    private fun notDetected(frameIndex: Int, timestampMs: Long) = BallDetection(
        x = 0f, y = 0f, confidence = 0f, radiusPx = 0f,
        frameIndex = frameIndex, timestampMs = timestampMs,
        status = BallDetectionStatus.NOT_DETECTED
    )
}
