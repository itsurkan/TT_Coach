package com.ttcoachai.tracking

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ttcoachai.shared.models.BallDetection
import com.ttcoachai.shared.models.BallDetectionStatus
import com.ttcoachai.shared.models.RegionOfInterest
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * BallDetectorV6 — YOLOv11-nano ball detection via TFLite.
 *
 * Pipeline:
 *  1. Crop the ROI from the input bitmap.
 *  2. Resize to 320×320 and normalize to [0,1].
 *  3. Run TFLite inference → [1, 5, N] raw predictions.
 *  4. Transpose to [N, 5], filter by confidence, apply NMS.
 *  5. Return the highest-confidence detection in full-frame normalized coords.
 *
 * No motion detection needed — YOLO handles presence/absence natively.
 */
class BallDetectorV6(
    context: Context,
    private val confidenceThreshold: Float = 0.25f,
    private val nmsIouThreshold: Float = 0.45f,
    modelFileName: String = "ball_yolo.tflite"
) {
    companion object {
        private const val TAG = "BallDetectorV6"
        private const val MODEL_INPUT_SIZE = 320
    }

    private val interpreter: Interpreter
    private var gpuDelegate: GpuDelegate? = null

    // Input: [1, 320, 320, 3] float32
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(1 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())

    // Reusable bitmap for resizing
    private var resizedBitmap: Bitmap =
        Bitmap.createBitmap(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, Bitmap.Config.ARGB_8888)

    // Output shape determined at init
    private val numPredictions: Int
    private val outputBuffer: ByteBuffer

    init {
        val modelBuffer = loadModelFile(context, modelFileName)
        val options = Interpreter.Options()

        // Try GPU delegate, fall back to CPU
        try {
            gpuDelegate = GpuDelegate()
            options.addDelegate(gpuDelegate!!)
            Log.d(TAG, "Using GPU delegate")
        } catch (e: Exception) {
            Log.w(TAG, "GPU delegate unavailable, falling back to CPU", e)
            gpuDelegate = null
            options.setNumThreads(2)
        }

        interpreter = Interpreter(modelBuffer, options)

        // Query output shape: expected [1, 5, N] for single-class YOLO
        val outputShape = interpreter.getOutputTensor(0).shape()
        // outputShape = [1, 5, N] where N = number of anchors
        numPredictions = outputShape[2]
        outputBuffer = ByteBuffer.allocateDirect(1 * 5 * numPredictions * 4)
            .order(ByteOrder.nativeOrder())

        Log.d(TAG, "Model loaded: output shape ${outputShape.contentToString()}, $numPredictions predictions")
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

    fun reset() {
        // No state to reset (no motion detection)
    }

    fun release() {
        interpreter.close()
        gpuDelegate?.close()
        resizedBitmap.recycle()
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

        // Clamp ROI
        val roiX = roi.x.coerceIn(0, frameWidth - 1)
        val roiY = roi.y.coerceIn(0, frameHeight - 1)
        val roiW = roi.width.coerceAtMost(frameWidth - roiX)
        val roiH = roi.height.coerceAtMost(frameHeight - roiY)
        if (roiW <= 0 || roiH <= 0) return notDetected(frameIndex, timestampMs)

        // Crop ROI from bitmap
        val cropBitmap = Bitmap.createBitmap(bitmap, roiX, roiY, roiW, roiH)

        // Resize to 320×320
        val canvas = android.graphics.Canvas(resizedBitmap)
        val srcRect = android.graphics.Rect(0, 0, cropBitmap.width, cropBitmap.height)
        val dstRect = android.graphics.Rect(0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        canvas.drawBitmap(cropBitmap, srcRect, dstRect, null)
        cropBitmap.recycle()

        // Fill input buffer: normalize to [0, 1] (YOLO expects this, not ImageNet normalization)
        inputBuffer.rewind()
        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        resizedBitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        // Run inference
        outputBuffer.rewind()
        interpreter.run(inputBuffer, outputBuffer)

        // Parse output [1, 5, N] → find best detection
        outputBuffer.rewind()
        val raw = FloatArray(5 * numPredictions)
        outputBuffer.asFloatBuffer().get(raw)

        // raw is stored as [5][N]: row-major with 5 rows of N values
        // row 0 = cx, row 1 = cy, row 2 = w, row 3 = h, row 4 = conf
        var bestConf = confidenceThreshold
        var bestCx = 0f
        var bestCy = 0f
        var bestW = 0f
        var bestH = 0f

        for (i in 0 until numPredictions) {
            val conf = raw[4 * numPredictions + i]
            if (conf > bestConf) {
                bestConf = conf
                bestCx = raw[0 * numPredictions + i]
                bestCy = raw[1 * numPredictions + i]
                bestW = raw[2 * numPredictions + i]
                bestH = raw[3 * numPredictions + i]
            }
        }

        if (bestConf <= confidenceThreshold) {
            return notDetected(frameIndex, timestampMs)
        }

        // Map from model pixel space (0-320) → ROI-normalized → full-frame normalized
        val roiNormX = bestCx / MODEL_INPUT_SIZE  // [0,1] within ROI
        val roiNormY = bestCy / MODEL_INPUT_SIZE
        val fullX = (roiX + roiNormX * roiW) / frameWidth.toFloat()
        val fullY = (roiY + roiNormY * roiH) / frameHeight.toFloat()

        // Radius in full-frame pixels (use the larger of w/h)
        val radiusPx = maxOf(bestW, bestH) / 2f * maxOf(
            roiW.toFloat() / MODEL_INPUT_SIZE,
            roiH.toFloat() / MODEL_INPUT_SIZE
        )

        return BallDetection(
            x = fullX.coerceIn(0f, 1f),
            y = fullY.coerceIn(0f, 1f),
            confidence = bestConf.coerceIn(0f, 1f),
            radiusPx = radiusPx,
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
