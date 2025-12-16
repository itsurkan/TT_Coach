package com.ttcoach.cv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.util.Log
// MediaPipe imports - TODO: Fix API imports when MediaPipe model is added
// import com.google.mediapipe.tasks.core.BaseOptions
// import com.google.mediapipe.tasks.vision.core.RunningMode
// import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
// import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerOptions
// import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Processes camera frames using MediaPipe Pose to extract 33 key points
 */
class MediaPipePoseProcessor(private val context: Context) {
    
    // TODO: Uncomment when MediaPipe API is fixed
    // private var poseLandmarker: PoseLandmarker? = null
    private val _poseResult = MutableStateFlow<Any?>(null) // PoseLandmarkerResult? when fixed
    val poseResult: StateFlow<Any?> = _poseResult.asStateFlow() // PoseLandmarkerResult? when fixed
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private var frameTimestamp = 0L
    
    init {
        initialize()
    }
    
    private fun initialize() {
        try {
            // TODO: Initialize MediaPipe when model file is added and API is fixed
            // Note: MediaPipe model file (pose_landmarker_lite.task) must be placed in app/src/main/assets/
            // Download from: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker
            
            // val baseOptions = BaseOptions.builder()
            //     .setModelAssetPath("pose_landmarker_lite.task")
            //     .build()
            // 
            // val options = PoseLandmarkerOptions.builder()
            //     .setBaseOptions(baseOptions)
            //     .setRunningMode(RunningMode.LIVE_STREAM)
            //     .setResultListener { result: PoseLandmarkerResult, image: MPImage ->
            //         _poseResult.value = result
            //     }
            //     .build()
            // 
            // poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            
            // For now, mark as not initialized until MediaPipe is properly integrated
            _isInitialized.value = false
            Log.w(TAG, "MediaPipe Pose initialization skipped - API needs fixing and model file required")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaPipe Pose", e)
            _isInitialized.value = false
        }
    }
    
    /**
     * Process a frame from camera using Bitmap
     * @param bitmap Camera frame as Bitmap
     * @param timestamp Frame timestamp in milliseconds
     */
    fun processFrame(bitmap: Bitmap, timestamp: Long) {
        try {
            // TODO: Implement MediaPipe frame processing when API is fixed
            // val landmarker = poseLandmarker ?: return
            // val mpImage = MPImage(bitmap, MPImage.ImageFormat.IMAGE_FORMAT_RGB)
            // landmarker.detectAsync(mpImage, timestamp)
            Log.d(TAG, "Frame processed at timestamp: $timestamp (MediaPipe processing disabled)")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame with Bitmap", e)
        }
    }
    
    /**
     * Process a frame from camera using NV21 data
     * @param nv21Data YUV420_888 image data converted to NV21 format
     * @param width Image width
     * @param height Image height
     * @param timestamp Frame timestamp in milliseconds
     */
    fun processFrame(nv21Data: ByteArray, width: Int, height: Int, timestamp: Long) {
        try {
            // Convert NV21 to Bitmap
            val bitmap = nv21ToBitmap(nv21Data, width, height)
                ?: run {
                    Log.w(TAG, "Failed to convert NV21 to Bitmap")
                    return
                }
            
            processFrame(bitmap, timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame with NV21", e)
        }
    }
    
    private fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap? {
        return try {
            val yuvImage = android.graphics.YuvImage(
                nv21,
                ImageFormat.NV21,
                width,
                height,
                null
            )
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                android.graphics.Rect(0, 0, width, height),
                90,
                out
            )
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting NV21 to Bitmap", e)
            null
        }
    }
    
    /**
     * Get 33 key points from the latest pose result
     */
    fun getKeyPoints(): List<KeyPoint>? {
        // TODO: Implement when MediaPipe is properly integrated
        // val result = _poseResult.value as? PoseLandmarkerResult ?: return null
        // return result.landmarks().firstOrNull()?.mapIndexed { index, landmark ->
        //     KeyPoint(
        //         index = index,
        //         x = landmark.x(),
        //         y = landmark.y(),
        //         z = landmark.z(),
        //         visibility = landmark.visibility().orElse(0f)
        //     )
        // }
        return null
    }
    
    fun release() {
        // TODO: Release MediaPipe resources when implemented
        // poseLandmarker?.close()
        // poseLandmarker = null
        _isInitialized.value = false
        Log.d(TAG, "MediaPipe Pose released")
    }
    
    companion object {
        private const val TAG = "MediaPipePoseProcessor"
    }
}

/**
 * Data class representing a pose key point
 */
data class KeyPoint(
    val index: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float
)

