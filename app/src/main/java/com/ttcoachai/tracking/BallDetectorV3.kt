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
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * BallDetectorV3 — motion-first detection.
 *
 * Pipeline:
 *  1. Convert the current frame to grayscale and diff against the previous frame.
 *  2. Threshold + dilate the diff to get blobs of significant motion.
 *  3. Find bounding boxes of those motion blobs (intersected with the caller ROI).
 *  4. Run the HSV color-threshold + contour pipeline only inside each motion box.
 *  5. Return the highest-confidence candidate across all boxes.
 *
 * Compared to V2 (which ANDs a motion mask with the full-ROI HSV result), V3 skips
 * static regions of the frame entirely, making it faster on large frames.
 *
 * First frame (no previous): falls back to full-ROI HSV (same as V1 behaviour).
 *
 * API is identical to BallDetector — drop-in replacement.
 */
class BallDetectorV3(
    private val ballColor: BallColor = BallColor.WHITE,
    private val expectedRadiusRange: IntRange = 4..25
) {

    enum class BallColor { WHITE, ORANGE }

    companion object {
        private const val TAG = "BallDetectorV3"
        private const val MIN_CIRCULARITY = 0.20f
        private const val CONFIDENCE_CIRCULARITY_WEIGHT = 0.6f
        private const val CONFIDENCE_SIZE_WEIGHT = 0.4f

        private val ORANGE_LOWER = Scalar(5.0, 100.0, 100.0)
        private val ORANGE_UPPER = Scalar(25.0, 255.0, 255.0)
        private val WHITE_LOWER  = Scalar(0.0, 0.0, 200.0)
        private val WHITE_UPPER  = Scalar(180.0, 50.0, 255.0)

        // Pixel-intensity change required to count as motion (0–255).
        private const val MOTION_THRESHOLD = 25.0
        // Dilation radius applied to motion blobs — merges nearby patches so a
        // fast-moving, motion-blurred ball is covered by a single bounding box.
        private const val MOTION_DILATE_PX = 30.0
    }

    // HSV pipeline Mats (reused across calls)
    private var srcMat:      Mat? = null
    private var hsvMat:      Mat? = null
    private var maskMat:     Mat? = null
    private var morphMat:    Mat? = null
    private var morphKernel: Mat? = null

    // Motion detection Mats
    private var grayMat:      Mat? = null
    private var prevGrayMat:  Mat? = null
    private var diffMat:      Mat? = null
    private var motionMask:   Mat? = null
    private var motionKernel: Mat? = null

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

    /** Clears the stored previous frame. Call between unrelated video clips or camera restarts. */
    fun reset() {
        prevGrayMat?.release()
        prevGrayMat = Mat()
    }

    fun release() {
        srcMat?.release();       srcMat       = null
        hsvMat?.release();       hsvMat       = null
        maskMat?.release();      maskMat      = null
        morphMat?.release();     morphMat     = null
        morphKernel?.release();  morphKernel  = null
        grayMat?.release();      grayMat      = null
        prevGrayMat?.release();  prevGrayMat  = null
        diffMat?.release();      diffMat      = null
        motionMask?.release();   motionMask   = null
        motionKernel?.release(); motionKernel = null
    }

    // -------------------------------------------------------------------------
    // Internal pipeline
    // -------------------------------------------------------------------------

    private fun detectInternal(
        bitmap: Bitmap,
        roi: RegionOfInterest,
        frameIndex: Int,
        timestampMs: Long
    ): BallDetection {
        val frameWidth  = bitmap.width
        val frameHeight = bitmap.height

        // Lazy Mat allocation — keeps constructor OpenCV-free for unit-test environments
        if (srcMat      == null) srcMat      = Mat()
        if (hsvMat      == null) hsvMat      = Mat()
        if (maskMat     == null) maskMat     = Mat()
        if (morphMat    == null) morphMat    = Mat()
        if (morphKernel == null) morphKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        if (grayMat     == null) grayMat     = Mat()
        if (prevGrayMat == null) prevGrayMat = Mat()
        if (diffMat     == null) diffMat     = Mat()
        if (motionMask  == null) motionMask  = Mat()
        if (motionKernel == null) motionKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE, Size(MOTION_DILATE_PX, MOTION_DILATE_PX))

        // 1. Bitmap → RGBA Mat
        Utils.bitmapToMat(bitmap, srcMat!!)

        // 2. Clamp caller ROI to frame bounds
        val roiX = roi.x.coerceIn(0, frameWidth - 1)
        val roiY = roi.y.coerceIn(0, frameHeight - 1)
        val roiW = roi.width.coerceAtMost(frameWidth - roiX)
        val roiH = roi.height.coerceAtMost(frameHeight - roiY)
        if (roiW <= 0 || roiH <= 0) return notDetected(frameIndex, timestampMs)
        val callerRoi = Rect(roiX, roiY, roiW, roiH)

        // 3. Convert full frame to grayscale for motion detection
        Imgproc.cvtColor(srcMat!!, grayMat!!, Imgproc.COLOR_RGBA2GRAY)

        // 4. Determine search rects — motion boxes if we have a previous frame,
        //    otherwise the full caller ROI (first-frame fallback)
        val searchRects: List<Rect> = if (prevGrayMat!!.empty()) {
            listOf(callerRoi)
        } else {
            motionBoundingRects(callerRoi).ifEmpty { return saveGrayAndReturn(notDetected(frameIndex, timestampMs)) }
        }

        // 5. Save current grayscale for next frame's diff
        grayMat!!.copyTo(prevGrayMat!!)

        // 6. Run HSV detection inside each motion box, pick the best result
        return searchRects
            .mapNotNull { rect -> detectInRect(rect, frameWidth, frameHeight, frameIndex, timestampMs) }
            .maxByOrNull { it.confidence }
            ?: notDetected(frameIndex, timestampMs)
    }

    // -------------------------------------------------------------------------
    // Step 4 — motion bounding boxes
    // -------------------------------------------------------------------------

    /**
     * Computes bounding rectangles (in full-frame coords) of moving regions
     * within [roiRect]. Returns an empty list when no significant motion is found.
     */
    private fun motionBoundingRects(roiRect: Rect): List<Rect> {
        val prevRoi = Mat(prevGrayMat!!, roiRect)
        val currRoi = Mat(grayMat!!,     roiRect)

        // Absolute per-pixel difference, then threshold to binary motion mask
        Core.absdiff(currRoi, prevRoi, diffMat!!)
        Imgproc.threshold(diffMat!!, motionMask!!, MOTION_THRESHOLD, 255.0, Imgproc.THRESH_BINARY)
        // Dilate to merge nearby blobs (fast-moving ball leaves multiple small patches)
        Imgproc.dilate(motionMask!!, motionMask!!, motionKernel!!)

        currRoi.release()
        prevRoi.release()

        val contours  = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            motionMask!!,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        hierarchy.release()

        // Accept only blobs consistent with a ball-sized moving object.
        //
        // A moving ball leaves an elongated streak: narrow in one dimension (≈ ball diameter)
        // and long in the other (the trail). We therefore constrain the SHORT side of the
        // bounding rect, not the total area — which would wrongly reject valid streaks.
        //
        // min short side: smallest expected ball diameter
        // max short side: largest expected ball diameter + dilation on each edge
        // → rejects person-sized motion blobs (wide in both dimensions)
        val minShortSide = expectedRadiusRange.first * 2
        val maxShortSide = ((expectedRadiusRange.last + MOTION_DILATE_PX) * 2).toInt()
        // Still require minimum area so sub-pixel noise blobs are skipped
        val minBlobArea  = PI * expectedRadiusRange.first * expectedRadiusRange.first

        val rects = mutableListOf<Rect>()
        for (c in contours) {
            val area = Imgproc.contourArea(c)
            if (area >= minBlobArea) {
                val br        = Imgproc.boundingRect(c)
                val shortSide = minOf(br.width, br.height)
                if (shortSide in minShortSide..maxShortSide) {
                    // boundingRect coords are relative to roiRect — translate to full-frame
                    rects.add(Rect(roiRect.x + br.x, roiRect.y + br.y, br.width, br.height))
                }
            }
            c.release()
        }

        return rects
    }

    // -------------------------------------------------------------------------
    // Step 6 — HSV detection inside one rect
    // -------------------------------------------------------------------------

    /**
     * Runs the HSV color-threshold + morphology + contour pipeline on [searchRect]
     * (full-frame pixel coordinates). Returns a DETECTED [BallDetection] or null.
     */
    private fun detectInRect(
        searchRect: Rect,
        frameWidth: Int,
        frameHeight: Int,
        frameIndex: Int,
        timestampMs: Long
    ): BallDetection? {
        val subMat = Mat(srcMat!!, searchRect)

        val hsv    = hsvMat!!
        val mask   = maskMat!!
        val morph  = morphMat!!
        val kernel = morphKernel!!

        // Convert sub-region to HSV
        Imgproc.cvtColor(subMat, hsv, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)
        subMat.release()

        // Color threshold
        val (lower, upper) = when (ballColor) {
            BallColor.ORANGE -> ORANGE_LOWER to ORANGE_UPPER
            BallColor.WHITE  -> WHITE_LOWER  to WHITE_UPPER
        }
        Core.inRange(hsv, lower, upper, mask)

        // Morphological open (remove noise) then close (fill small holes)
        Imgproc.morphologyEx(mask, morph, Imgproc.MORPH_OPEN,  kernel)
        Imgproc.morphologyEx(morph, morph, Imgproc.MORPH_CLOSE, kernel)

        // Find contours (coords are relative to searchRect)
        val contours  = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            morph,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        hierarchy.release()

        val minArea = PI * expectedRadiusRange.first * expectedRadiusRange.first
        val maxArea = PI * expectedRadiusRange.last * expectedRadiusRange.last

        var bestContour: MatOfPoint? = null
        var bestScore   = -1f

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < minArea || area > maxArea) continue

            val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            if (perimeter <= 0) continue

            val circularity = (4.0 * PI * area / (perimeter * perimeter)).toFloat()
            if (circularity < MIN_CIRCULARITY) continue

            val radiusEstimate = sqrt(area / PI).toFloat()
            val radiusMid      = (expectedRadiusRange.first + expectedRadiusRange.last) / 2f
            val sizeScore      = 1f - abs(radiusEstimate - radiusMid) / radiusMid
            val score          = CONFIDENCE_CIRCULARITY_WEIGHT * circularity +
                                 CONFIDENCE_SIZE_WEIGHT * sizeScore.coerceIn(0f, 1f)

            if (score > bestScore) {
                bestScore   = score
                bestContour = contour
            }
        }

        if (bestContour == null) {
            contours.forEach { it.release() }
            return null
        }

        // Compute centroid and radius before releasing contours
        val moments  = Imgproc.moments(bestContour)
        val bestArea = Imgproc.contourArea(bestContour)
        contours.forEach { it.release() }
        if (moments.m00 == 0.0) return null

        // Translate from sub-rect coords → full-frame normalised [0,1]
        val cxSub    = (moments.m10 / moments.m00).toFloat()
        val cySub    = (moments.m01 / moments.m00).toFloat()
        val cxFrame  = (searchRect.x + cxSub) / frameWidth.toFloat()
        val cyFrame  = (searchRect.y + cySub) / frameHeight.toFloat()
        val radiusPx = sqrt(bestArea / PI).toFloat()

        return BallDetection(
            x           = cxFrame.coerceIn(0f, 1f),
            y           = cyFrame.coerceIn(0f, 1f),
            confidence  = bestScore.coerceIn(0f, 1f),
            radiusPx    = radiusPx,
            frameIndex  = frameIndex,
            timestampMs = timestampMs,
            status      = BallDetectionStatus.DETECTED
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Saves current gray to prevGray and returns [result] — used in early-exit paths. */
    private fun saveGrayAndReturn(result: BallDetection): BallDetection {
        grayMat!!.copyTo(prevGrayMat!!)
        return result
    }

    private fun notDetected(frameIndex: Int, timestampMs: Long) = BallDetection(
        x = 0f, y = 0f, confidence = 0f, radiusPx = 0f,
        frameIndex = frameIndex, timestampMs = timestampMs,
        status = BallDetectionStatus.NOT_DETECTED
    )
}
