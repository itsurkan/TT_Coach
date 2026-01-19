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
package com.google.mediapipe.examples.poselandmarker.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.ImageProxy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.google.mediapipe.examples.poselandmarker.MainViewModel
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentCameraBinding
import com.google.mediapipe.examples.poselandmarker.managers.CameraManager
import com.google.mediapipe.examples.poselandmarker.managers.CameraUIController
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {

    companion object {
        private const val TAG = "TT AI Coach 1"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var cameraManager: CameraManager
    private lateinit var uiController: CameraUIController

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    override fun onResume() {
        super.onResume()
        
        // Check permissions
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            val navHostView = requireActivity().findViewById<View>(R.id.fragment_container)
            if (navHostView != null) {
                Navigation.findNavController(
                    requireActivity(), R.id.fragment_container
                ).navigate(R.id.action_camera_to_permissions)
            } else {
                // Determine missing permissions
                val permissions = arrayOf(android.Manifest.permission.CAMERA)
                requestPermissions(permissions, 0)
            }
        }

        // Restart PoseLandmarkerHelper when returning to foreground
        backgroundExecutor.execute {
            if (::poseLandmarkerHelper.isInitialized) {
                if (poseLandmarkerHelper.isClose()) {
                    poseLandmarkerHelper.setupPoseLandmarker()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        
        if (::poseLandmarkerHelper.isInitialized) {
            // Save current settings to ViewModel
            viewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
            viewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
            viewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
            viewModel.setDelegate(poseLandmarkerHelper.currentDelegate)

            // Release resources
            backgroundExecutor.execute { 
                poseLandmarkerHelper.clearPoseLandmarker() 
            }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shutdown background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Initialize managers
        cameraManager = CameraManager(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            backgroundExecutor = backgroundExecutor,
            onImageAnalysis = { image -> detectPose(image) }
        )

        uiController = CameraUIController(
            binding = fragmentCameraBinding,
            getPoseLandmarkerHelper = { 
                if (::poseLandmarkerHelper.isInitialized) poseLandmarkerHelper else null 
            }
        )

        // Setup UI after view is laid out
        fragmentCameraBinding.viewFinder.post {
            setupCamera()
        }

        // Create PoseLandmarkerHelper
        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                currentDelegate = viewModel.currentDelegate,
                poseLandmarkerHelperListener = this
            )
        }

        // Initialize UI controls
        uiController.initThresholdDisplays(
            detectionConfidence = viewModel.currentMinPoseDetectionConfidence,
            trackingConfidence = viewModel.currentMinPoseTrackingConfidence,
            presenceConfidence = viewModel.currentMinPosePresenceConfidence
        )

        uiController.initBottomSheetControls(
            currentDelegate = viewModel.currentDelegate,
            currentModel = viewModel.currentModel,
            onControlsChanged = { updateControlsUi() }
        )
    }

    /**
     * Setup camera and bind use cases
     */
    private fun setupCamera() {
        cameraManager.setUpCamera(
            surfaceProvider = fragmentCameraBinding.viewFinder.surfaceProvider,
            displayRotation = fragmentCameraBinding.viewFinder.display.rotation
        )
    }

    /**
     * Update UI controls when settings change
     */
    private fun updateControlsUi() {
        if (::poseLandmarkerHelper.isInitialized) {
            uiController.updateControlsUi()
            
            // Reinitialize PoseLandmarkerHelper with new settings
            backgroundExecutor.execute {
                poseLandmarkerHelper.clearPoseLandmarker()
                poseLandmarkerHelper.setupPoseLandmarker()
            }
        }
    }

    /**
     * Detect pose from camera frame
     */
    private fun detectPose(imageProxy: ImageProxy) {
        if (::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = cameraManager.isFrontCamera()
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        cameraManager.updateRotation(fragmentCameraBinding.viewFinder.display.rotation)
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                uiController.updateInferenceTime(resultBundle.inferenceTime)

                // Pass results to OverlayView for drawing
                fragmentCameraBinding.overlay.setResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )

                // Force redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == PoseLandmarkerHelper.GPU_ERROR) {
                uiController.setDelegateToCpu()
            }
        }
    }
}
