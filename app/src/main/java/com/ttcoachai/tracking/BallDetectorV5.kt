package com.ttcoachai.tracking

import android.content.Context
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
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.PI

/**
 * BallDetectorV5 — motion-first detection + TFLite MobileNetV3-Small regressor.
 *
 * Pipeline:
 *  1. Convert current frame to grayscale and diff against the previous frame.
 *  2. Threshold + dilate the diff to get motion blobs (same as V3).
 *  3. For each motion bounding box (intersected with caller ROI):
 *     a. Crop the RGBA region from the source frame.
 *     b. Resize to 320×320, normalize with ImageNet mean/std.
 *     c. Run TFLite inference → (x, y, confidence) in [0,1].
 *     d. If confidence > threshold, map (x, y) back to full-frame coordinates.
 *  4. Return the highest-confidence detection across all motion boxes.
 *
 * First frame (no previous): runs TFLite on the full caller ROI.
 *
 * API is identical to BallDetector/V2/V3 — drop-in replacement (plus Context for model loading).
 */
class BallDetectorV5(
    context: Context,
    private val confidenceThreshold: Float = 0.5f,
    private val expectedRadiusRange: IntRange = 4..25,
    modelFileName: String = "ball_regressor.tflite"
) {
    companion object {
        private const val TAG = "BallDetectorV5"
        private const val MODEL_INPUT_SIZE = 320

        // ImageNet normalization constants (model was trained with these)
        private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        // Motion detection parameters (same as V3)
        private const val MOTION_THRESHOLD = 25.0
        private const val MOTION_DILATE_PX = 30.0
    }

    // TFLite interpreter
    private val interpreter: Interpreter

    // Pre-allocated TFLite I/O buffers
    // Input: [1, 320, 320, 3] float32
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(1 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())

    // Output: [1, 3] float32 → (x, y, confidence)
    private val outputArray = Array(1) { FloatArray(3) }

    // Reusable bitmap for resizing crops to 320×320
    private var resizedBitmap: Bitmap =
        Bitmap.createBitmap(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, Bitmap.Config.ARGB_8888)

    // Motion detection Mats (lazy-allocated, same as V3)
    private var srcMat: Mat? = null
    private var grayMat: Mat? = null
    private var prevGrayMat: Mat? = null
    private var diffMat: Mat? = null
    private var motionMask: Mat? = null
    private var motionKernel: Mat? = null

    init {
        val modelBuffer = loadModelFile(context, modelFileName)
        val options = Interpreter.Options().apply {
            setNumThreads(2)
        }
        interpreter = Interpreter(modelBuffer, options)
    }

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

    /** Clears the stored previous frame. Call between unrelated video clips. */
    fun reset() {
        prevGrayMat?.release()
        prevGrayMat = Mat()
    }

    fun release() {
        interpreter.close()
        resizedBitmap.recycle()
        srcMat?.release(); srcMat = null
        grayMat?.release(); grayMat = null
        prevGrayMat?.release(); prevGrayMat = null
        diffMat?.release(); diffMat = null
        motionMask?.release(); motionMask = null
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
        val frameWidth = bitmap.width
        val frameHeight = bitmap.height

        // Lazy Mat allocation
        if (srcMat == null) srcMat = Mat()
        if (grayMat == null) grayMat = Mat()
        if (prevGrayMat == null) prevGrayMat = Mat()
        if (diffMat == null) diffMat = Mat()
        if (motionMask == null) motionMask = Mat()
        if (motionKernel == null) motionKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE, Size(MOTION_DILATE_PX, MOTION_DILATE_PX)
        )

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

        // 4. Determine search rects — motion boxes if we have a previous frame
        val searchRects: List<Rect> = if (prevGrayMat!!.empty()) {
            listOf(callerRoi)
        } else {
            motionBoundingRects(callerRoi).ifEmpty {
                grayMat!!.copyTo(prevGrayMat!!)
                return notDetected(frameIndex, timestampMs)
            }
        }

        // 5. Save current grayscale for next frame's diff
        grayMat!!.copyTo(prevGrayMat!!)

        // 6. Run TFLite on each motion box, pick the best result
        return searchRects
            .mapNotNull { rect -> inferInRect(rect, frameWidth, frameHeight, frameIndex, timestampMs) }
            .maxByOrNull { it.confidence }
            ?: notDetected(frameIndex, timestampMs)
    }

    // -------------------------------------------------------------------------
    // Motion bounding boxes (same logic as V3)
    // -------------------------------------------------------------------------

    private fun motionBoundingRects(roiRect: Rect): List<Rect> {
        val prevRoi = Mat(prevGrayMat!!, roiRect)
        val currRoi = Mat(grayMat!!, roiRect)

        Core.absdiff(currRoi, prevRoi, diffMat!!)
        Imgproc.threshold(diffMat!!, motionMask!!, MOTION_THRESHOLD, 255.0, Imgproc.THRESH_BINARY)
        Imgproc.dilate(motionMask!!, motionMask!!, motionKernel!!)

        currRoi.release()
        prevRoi.release()

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            motionMask!!,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        hierarchy.release()

        val minShortSide = expectedRadiusRange.first * 2
        val maxShortSide = ((expectedRadiusRange.last + MOTION_DILATE_PX) * 2).toInt()
        val minBlobArea = PI * expectedRadiusRange.first * expectedRadiusRange.first

        val rects = mutableListOf<Rect>()
        for (c in contours) {
            val area = Imgproc.contourArea(c)
            if (area >= minBlobArea) {
                val br = Imgproc.boundingRect(c)
                val shortSide = minOf(br.width, br.height)
                if (shortSide in minShortSide..maxShortSide) {
                    rects.add(Rect(roiRect.x + br.x, roiRect.y + br.y, br.width, br.height))
                }
            }
            c.release()
        }

        return rects
    }

    // -------------------------------------------------------------------------
    // TFLite inference on a single rect
    // -------------------------------------------------------------------------

    /**
     * Crops [searchRect] from the source frame, resizes to 320×320,
     * runs TFLite inference, and maps the predicted (x, y) back to
     * full-frame normalized coordinates.
     *
     * Returns a DETECTED [BallDetection] or null if confidence < threshold.
     */
    private fun inferInRect(
        searchRect: Rect,
        frameWidth: Int,
        frameHeight: Int,
        frameIndex: Int,
        timestampMs: Long
    ): BallDetection? {
        // Crop the RGBA sub-region and convert to Bitmap
        val subMat = Mat(srcMat!!, searchRect)
        val cropBitmap = Bitmap.createBitmap(searchRect.width, searchRect.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(subMat, cropBitmap)
        subMat.release()

        // Resize to model input size (320×320)
        val canvas = android.graphics.Canvas(resizedBitmap)
        val srcRect = android.graphics.Rect(0, 0, cropBitmap.width, cropBitmap.height)
        val dstRect = android.graphics.Rect(0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        canvas.drawBitmap(cropBitmap, srcRect, dstRect, null)
        cropBitmap.recycle()

        // Fill input buffer: normalize with ImageNet mean/std
        inputBuffer.rewind()
        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        resizedBitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        for (pixel in pixels) {
            val r = ((pixel shr 16 and 0xFF) / 255.0f - IMAGENET_MEAN[0]) / IMAGENET_STD[0]
            val g = ((pixel shr 8 and 0xFF) / 255.0f - IMAGENET_MEAN[1]) / IMAGENET_STD[1]
            val b = ((pixel and 0xFF) / 255.0f - IMAGENET_MEAN[2]) / IMAGENET_STD[2]
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        // Run inference
        interpreter.run(inputBuffer, outputArray)

        val predX = outputArray[0][0]    // normalized x within the crop
        val predY = outputArray[0][1]    // normalized y within the crop
        val predConf = outputArray[0][2] // confidence

        if (predConf < confidenceThreshold) return null

        // Map from crop-local normalized coords → full-frame normalized coords
        val fullX = (searchRect.x + predX * searchRect.width) / frameWidth.toFloat()
        val fullY = (searchRect.y + predY * searchRect.height) / frameHeight.toFloat()

        return BallDetection(
            x = fullX.coerceIn(0f, 1f),
            y = fullY.coerceIn(0f, 1f),
            confidence = predConf.coerceIn(0f, 1f),
            radiusPx = 0f, // TFLite model does not predict radius
            frameIndex = frameIndex,
            timestampMs = timestampMs,
            status = BallDetectionStatus.DETECTED
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun notDetected(frameIndex: Int, timestampMs: Long) = BallDetection(
        x = 0f, y = 0f, confidence = 0f, radiusPx = 0f,
        frameIndex = frameIndex, timestampMs = timestampMs,
        status = BallDetectionStatus.NOT_DETECTED
    )

    private fun loadModelFile(context: Context, fileName: String): MappedByteBuffer {
        val afd = context.assets.openFd(fileName)
        val inputStream = FileInputStream(afd.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            afd.startOffset,
            afd.declaredLength
        )
    }
}
