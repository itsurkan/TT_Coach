/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ttcoachai.managers

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.ttcoachai.PoseLandmarkerHelper
import com.ttcoachai.tracking.CameraOptimizer
import java.util.concurrent.ExecutorService

/**
 * Manages CameraX setup and lifecycle for CameraFragment.
 * Integrates CameraOptimizer for ball-tracking exposure control.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val backgroundExecutor: ExecutorService,
    private val onImageAnalysis: (ImageProxy) -> Unit
) {

    companion object {
        private const val TAG = "CameraManager"
    }

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_BACK

    /** Null until camera is bound; initialized after bindToLifecycle succeeds. */
    private var cameraOptimizer: CameraOptimizer? = null

    /**
     * Initialize CameraX and prepare to bind camera use cases
     */
    fun setUpCamera(
        surfaceProvider: Preview.SurfaceProvider,
        displayRotation: Int
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(surfaceProvider, displayRotation)
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    /**
     * Declare and bind preview, capture and analysis use cases
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases(
        surfaceProvider: Preview.SurfaceProvider,
        displayRotation: Int
    ) {
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cameraFacing)
            .build()

        // Preview - using 4:3 ratio to match our models
        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(displayRotation)
            .build()

        // ImageAnalysis - using RGBA 8888 to match our models
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(displayRotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(backgroundExecutor) { image ->
                    onImageAnalysis(image)
                }
            }

        // Unbind use-cases before rebinding
        cameraProvider.unbindAll()

        try {
            // Bind to lifecycle
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            // Attach surface provider to preview
            preview?.setSurfaceProvider(surfaceProvider)

            // Initialize CameraOptimizer now that camera is bound
            initializeCameraOptimizer()
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    /**
     * Create CameraOptimizer using Camera2 interop after camera is bound.
     * Called internally after bindToLifecycle succeeds.
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun initializeCameraOptimizer() {
        val boundCamera = camera ?: return
        try {
            val camera2Control = Camera2CameraControl.from(boundCamera.cameraControl)
            val camera2Info = Camera2CameraInfo.from(boundCamera.cameraInfo)
            val characteristics = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
            )
            // Retrieve the full CameraCharacteristics via the camera2 info wrapper
            val cameraId = camera2Info.cameraId
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE)
                    as android.hardware.camera2.CameraManager
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

            cameraOptimizer = CameraOptimizer(camera2Control, cameraCharacteristics)
            Log.d(TAG, "CameraOptimizer initialized for camera $cameraId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CameraOptimizer", e)
        }
    }

    /**
     * Enable ball-tracking camera mode: low exposure, fixed FPS.
     * Call when starting a ball-tracking session.
     */
    fun enableBallTrackingMode() {
        cameraOptimizer?.applyBallTrackingMode()
            ?: Log.w(TAG, "CameraOptimizer not ready — ball tracking mode not applied")
    }

    /**
     * Disable ball-tracking camera mode and restore auto-exposure.
     * Call when ending a ball-tracking session.
     */
    fun disableBallTrackingMode() {
        cameraOptimizer?.restoreDefaultMode()
    }

    /**
     * Forward frame brightness to CameraOptimizer for periodic exposure adaptation.
     * Call approximately once per second with the average luminance of the frame center.
     *
     * @param averageLuminance Frame center brightness in range 0-255
     */
    fun onBrightnessUpdate(averageLuminance: Float) {
        cameraOptimizer?.onBrightnessUpdate(averageLuminance)
    }

    /**
     * Update target rotation when configuration changes
     */
    fun updateRotation(displayRotation: Int) {
        imageAnalyzer?.targetRotation = displayRotation
    }

    /**
     * Check if camera is front facing
     */
    fun isFrontCamera(): Boolean {
        return cameraFacing == CameraSelector.LENS_FACING_FRONT
    }

    /**
     * Stop and unbind all camera use cases
     */
    fun stop() {
        try {
            cameraOptimizer?.restoreDefaultMode()
            preview?.setSurfaceProvider(null)
            imageAnalyzer?.clearAnalyzer()
            cameraProvider?.unbindAll()
        } catch (exc: Exception) {
            Log.e(TAG, "Error stopping camera", exc)
        }
    }

    /**
     * Release camera resources
     */
    fun release() {
        stop()
        cameraOptimizer = null
        cameraProvider = null
        camera = null
        preview = null
        imageAnalyzer = null
    }
}
