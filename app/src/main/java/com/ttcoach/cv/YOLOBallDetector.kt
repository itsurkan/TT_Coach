package com.ttcoach.cv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * YOLO Nano ball detector using TensorFlow Lite
 * Detects table tennis ball in camera frames
 */
class YOLOBallDetector(private val context: Context) {
    
    private var interpreter: Interpreter? = null
    private val inputBuffer: ByteBuffer
    private val outputBuffer: Array<FloatArray>
    
    // YOLO input/output dimensions (adjust based on actual model)
    private val inputWidth = 416
    private val inputHeight = 416
    private val inputChannels = 3
    private val numDetections = 10 // Max detections
    private val numClasses = 1 // Just ball class
    
    // Detection thresholds
    private val confidenceThreshold = 0.5f
    private val nmsThreshold = 0.4f
    
    init {
        // Initialize input buffer
        inputBuffer = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * inputChannels)
            .order(ByteOrder.nativeOrder())
        
        // Initialize output buffer (YOLO format: [x, y, w, h, confidence, class])
        outputBuffer = Array(numDetections) { FloatArray(6) } // [x, y, w, h, conf, class]
        
        loadModel()
    }
    
    private fun loadModel() {
        try {
            // TODO: Load YOLO Nano model from assets
            // val modelFile = "yolo_nano.tflite"
            // val modelBuffer = loadModelFile(context, modelFile)
            // interpreter = Interpreter(modelBuffer)
            
            Log.w(TAG, "YOLO model not loaded - model file required in app/src/main/assets/yolo_nano.tflite")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading YOLO model", e)
        }
    }
    
    /**
     * Detect ball in bitmap image
     * @param bitmap Input image
     * @return List of detected balls with bounding boxes and confidence scores
     */
    fun detect(bitmap: Bitmap): List<BallDetection> {
        val interpreter = this.interpreter ?: return emptyList()
        
        try {
            // Preprocess image
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
            convertBitmapToByteBuffer(resizedBitmap)
            
            // Run inference
            interpreter.run(inputBuffer, outputBuffer)
            
            // Post-process detections
            return parseDetections(outputBuffer, bitmap.width, bitmap.height)
        } catch (e: Exception) {
            Log.e(TAG, "Error during detection", e)
            return emptyList()
        }
    }
    
    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
        inputBuffer.rewind()
        val pixels = IntArray(inputWidth * inputHeight)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var pixelIndex = 0
        for (i in 0 until inputHeight) {
            for (j in 0 until inputWidth) {
                val pixel = pixels[pixelIndex++]
                
                // Extract RGB values and normalize to [0, 1]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                
                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }
        }
    }
    
    private fun parseDetections(
        output: Array<FloatArray>,
        imageWidth: Int,
        imageHeight: Int
    ): List<BallDetection> {
        val detections = mutableListOf<BallDetection>()
        
        for (i in output.indices) {
            val detection = output[i]
            val confidence = detection[4]
            
            if (confidence > confidenceThreshold) {
                // YOLO format: [center_x, center_y, width, height, confidence, class]
                val centerX = detection[0] * imageWidth
                val centerY = detection[1] * imageHeight
                val width = detection[2] * imageWidth
                val height = detection[3] * imageHeight
                
                val left = centerX - width / 2
                val top = centerY - height / 2
                val right = centerX + width / 2
                val bottom = centerY + height / 2
                
                detections.add(
                    BallDetection(
                        boundingBox = RectF(left, top, right, bottom),
                        confidence = confidence,
                        center = android.graphics.PointF(centerX, centerY)
                    )
                )
            }
        }
        
        // Apply Non-Maximum Suppression
        return applyNMS(detections)
    }
    
    private fun applyNMS(detections: List<BallDetection>): List<BallDetection> {
        if (detections.isEmpty()) return emptyList()
        
        // Sort by confidence (descending)
        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<BallDetection>()
        val suppressed = BooleanArray(detections.size)
        
        for (i in sorted.indices) {
            if (suppressed[i]) continue
            
            selected.add(sorted[i])
            
            // Suppress overlapping detections
            for (j in (i + 1) until sorted.size) {
                if (suppressed[j]) continue
                
                val iou = calculateIoU(sorted[i].boundingBox, sorted[j].boundingBox)
                if (iou > nmsThreshold) {
                    suppressed[j] = true
                }
            }
        }
        
        return selected
    }
    
    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = maxOf(box1.left, box2.left)
        val intersectionTop = maxOf(box1.top, box2.top)
        val intersectionRight = minOf(box1.right, box2.right)
        val intersectionBottom = minOf(box1.bottom, box2.bottom)
        
        if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
            return 0f
        }
        
        val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        val unionArea = box1Area + box2Area - intersectionArea
        
        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }
    
    fun release() {
        interpreter?.close()
        interpreter = null
        Log.d(TAG, "YOLO detector released")
    }
    
    companion object {
        private const val TAG = "YOLOBallDetector"
        
        // TODO: Implement model loading when model file is available
        // private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        //     val fileDescriptor = context.assets.openFd(modelPath)
        //     val inputStream = FileInputStream(fileDescriptor.createInputStream())
        //     val fileChannel = inputStream.channel
        //     val startOffset = fileDescriptor.startOffset
        //     val declaredLength = fileDescriptor.declaredLength
        //     return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        // }
    }
}

/**
 * Data class representing a detected ball
 */
data class BallDetection(
    val boundingBox: RectF,
    val confidence: Float,
    val center: android.graphics.PointF
)

