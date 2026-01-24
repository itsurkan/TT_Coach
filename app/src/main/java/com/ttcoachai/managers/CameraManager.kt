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
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.ttcoachai.PoseLandmarkerHelper
import java.util.concurrent.ExecutorService

/**
 * Manages CameraX setup and lifecycle for CameraFragment
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
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
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
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
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
        cameraProvider?.unbindAll()
    }

    /**
     * Release camera resources
     */
    fun release() {
        stop()
        cameraProvider = null
        camera = null
        preview = null
        imageAnalyzer = null
    }
}
