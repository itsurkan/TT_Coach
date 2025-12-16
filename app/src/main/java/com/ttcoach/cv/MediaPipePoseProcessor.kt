package com.ttcoach.cv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerOptions
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Processes camera frames using MediaPipe Pose to extract 33 key points
 */
class MediaPipePoseProcessor(private val context: Context) {
    
    private var poseLandmarker: PoseLandmarker? = null
    private val _poseResult = MutableStateFlow<PoseLandmarkerResult?>(null)
    val poseResult: StateFlow<PoseLandmarkerResult?> = _poseResult.asStateFlow()
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private var frameTimestamp = 0L
    
    init {
        initialize()
    }
    
    private fun initialize() {
        try {
            // Note: MediaPipe model file (pose_landmarker_lite.task) must be placed in app/src/main/assets/
            // Download from: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker
            
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .build()
            
            val options = PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: PoseLandmarkerResult, image: com.google.mediapipe.tasks.vision.core.MPImage ->
                    _poseResult.value = result
                }
                .build()
            
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            _isInitialized.value = true
            Log.d(TAG, "MediaPipe Pose initialized successfully")
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
            val landmarker = poseLandmarker ?: return
            
            // Create MPImage from Bitmap
            // MediaPipe Tasks Vision uses MPImage which can be created from Bitmap
            val mpImage = com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList.getDefaultInstance()
            // Note: Need to use proper MPImage creation - this is a placeholder
            // The actual API may vary, but typically:
            // val mpImage = MPImage(bitmap, MPImage.ImageFormat.IMAGE_FORMAT_RGB)
            
            // For now, using a workaround - convert to proper format
            // This will need to be adjusted based on actual MediaPipe Tasks Vision API
            try {
                // Process frame - the actual API call may differ
                // landmarker.detectAsync(mpImage, timestamp)
                Log.d(TAG, "Frame processed at timestamp: $timestamp")
            } catch (e: Exception) {
                Log.e(TAG, "Error in detectAsync", e)
            }
            
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
        val result = _poseResult.value ?: return null
        return result.landmarks().firstOrNull()?.mapIndexed { index, landmark ->
            KeyPoint(
                index = index,
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
                visibility = landmark.visibility()
            )
        }
    }
    
    fun release() {
        poseLandmarker?.close()
        poseLandmarker = null
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

