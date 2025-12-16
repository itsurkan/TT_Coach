package com.ttcoach.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager as SystemCameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * Manages CameraX for capturing frames at 60 FPS
 */
class CameraManager(private val context: Context) {
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    
    private val executor = Executors.newSingleThreadExecutor()
    
    private val _frameFlow = MutableStateFlow<androidx.camera.core.ImageProxy?>(null)
    val frameFlow: StateFlow<androidx.camera.core.ImageProxy?> = _frameFlow.asStateFlow()
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps.asStateFlow()
    
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()
    
    /**
     * Initialize camera with CameraX
     */
    fun initialize(previewView: PreviewView, lifecycleOwner: LifecycleOwner, onError: (String) -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                // Preview
                preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                
                // Image Analysis for frame processing
                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also {
                        it.setAnalyzer(executor) { imageProxy ->
                            processImage(imageProxy)
                        }
                    }
                
                // Select camera (back camera)
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                // Bind use cases
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                
                this.cameraProvider = cameraProvider
                _isInitialized.value = true
                Log.d(TAG, "Camera initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing camera", e)
                onError(e.message ?: "Unknown error")
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    private fun processImage(imageProxy: androidx.camera.core.ImageProxy) {
        try {
            _frameFlow.value = imageProxy
            
            // Calculate FPS
            frameCount++
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastFpsTime >= 1000) {
                _fps.value = frameCount.toFloat()
                frameCount = 0
                lastFpsTime = currentTime
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
        }
    }
    
    fun release() {
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        imageAnalysis = null
        preview = null
        cameraProvider = null
        _isInitialized.value = false
        Log.d(TAG, "Camera released")
    }
    
    companion object {
        private const val TAG = "CameraManager"
    }
}

