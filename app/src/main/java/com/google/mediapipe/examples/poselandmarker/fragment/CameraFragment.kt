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

    private lateinit var feedbackAdapter: com.google.mediapipe.examples.poselandmarker.adapters.FeedbackListAdapter
    private lateinit var stateManager: com.google.mediapipe.examples.poselandmarker.managers.TrainingStateManager
    private lateinit var poseAnalysisProcessor: com.google.mediapipe.examples.poselandmarker.processors.PoseAnalysisProcessor

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    override fun onResume() {
        super.onResume()
        
        // Check permissions
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            // Request permissions directly since we are now embedded in TrainingActivity
            val permissions = arrayOf(android.Manifest.permission.CAMERA)
            requestPermissions(permissions, 0)
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

        // Initialize Training State
        stateManager = com.google.mediapipe.examples.poselandmarker.managers.TrainingStateManager.getInstance(requireContext())
        
        // Initialize feedback list
        setupFeedbackUI()

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

        // Initialize analysis processor for standalone mode
        val params = com.google.mediapipe.examples.poselandmarker.models.ExerciseParameters(
            exerciseId = "forehand_drive"
        )
        val motionAnalyzer = com.google.mediapipe.examples.poselandmarker.services.MotionAnalyzer(params)
        val feedbackGenerator = com.google.mediapipe.examples.poselandmarker.services.FeedbackGenerator(requireContext())
        
        poseAnalysisProcessor = com.google.mediapipe.examples.poselandmarker.processors.PoseAnalysisProcessor(
            application = requireActivity().application as com.google.mediapipe.examples.poselandmarker.TTCoachApplication,
            motionAnalyzer = motionAnalyzer,
            feedbackGenerator = feedbackGenerator,
            stateManager = stateManager,
            onUIUpdate = { updateStats() }
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

        // BottomSheet removed - specific logic moved to TrainingActivity

        // Initialize UI controls for drill menu (bottom sheet)
        // setupDrillMenu() - Removed: Drill menu is now managed by TrainingActivity
        
        // Auto-start training - Removed: Handled by TrainingActivity
        /*
        fragmentCameraBinding.root.postDelayed({
            if (activity !is com.google.mediapipe.examples.poselandmarker.TrainingActivity) {
                stateManager.startTraining()
                updateTrainingUIState()
            }
        }, 500)
        */
    }

    private fun setupFeedbackUI() {
        feedbackAdapter = com.google.mediapipe.examples.poselandmarker.adapters.FeedbackListAdapter()
        fragmentCameraBinding.root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_feedback_list)?.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
            adapter = feedbackAdapter
        }
    }

    private fun setupDrillMenu() {
        val root = fragmentCameraBinding.root
        
        // Pause/Resume in drawer
        root.findViewById<View>(R.id.btn_pause_resume)?.setOnClickListener {
            toggleTraining()
        }
        
        // FAB Pause/Play in overlay
        root.findViewById<View>(R.id.fab_pause_play)?.setOnClickListener {
            toggleTraining()
        }
        
        // End Session
        root.findViewById<View>(R.id.btn_end_session)?.setOnClickListener {
            stateManager.stopTraining()
            Toast.makeText(requireContext(), "Session Summary Coming Soon", Toast.LENGTH_LONG).show()
        }
        
        // Start timer update loop
        startTimerLoop()
        
        updateStats()
    }

    private fun toggleTraining() {
        if (stateManager.isTrainingActive) {
            stateManager.pauseTraining()
        } else {
            stateManager.resumeTraining()
        }
        updateTrainingUIState()
    }

    private fun updateTrainingUIState() {
        val root = fragmentCameraBinding.root
        val isActive = stateManager.isTrainingActive
        val icon = if (isActive) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val text = if (isActive) "Pause" else "Resume"
        
        // Update drawer button
        root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_pause_resume)?.apply {
            this.text = text
            setIconResource(icon)
        }
        
        // Update FAB
        root.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_pause_play)?.setImageResource(icon)
    }

    private fun startTimerLoop() {
        val root = fragmentCameraBinding.root
        val timerView = root.findViewById<android.widget.TextView>(R.id.tv_timer)
        
        // Timer is now managed by TrainingActivity, but keeping loop for basic overlay if used standalone
        root.post(object : Runnable {
            override fun run() {
                if (_fragmentCameraBinding != null) {
                    if (stateManager.isTrainingActive) {
                        timerView?.text = stateManager.getSessionTimeFormatted()
                    }
                    root.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun updateStats() {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding == null) return@runOnUiThread
            val root = fragmentCameraBinding.root
            
            val totalHits = stateManager.getTotalHits()
            val accuracy = if (totalHits > 0) {
                (stateManager.getSuccessfulHits().toFloat() / totalHits * 100).toInt()
            } else 0
            
            // Drawer stats
            root.findViewById<android.widget.TextView>(R.id.tv_total_hits)?.text = totalHits.toString()
            root.findViewById<android.widget.TextView>(R.id.tv_accuracy)?.text = "$accuracy%"
            
            // Overlay stats
            root.findViewById<android.widget.TextView>(R.id.tv_hits_count)?.text = totalHits.toString()
            root.findViewById<android.widget.TextView>(R.id.tv_accuracy_percent)?.text = "$accuracy%"
            
            // Update feedback list
            val latestFeedback = stateManager.getLatestFeedbackItems()
            feedbackAdapter.updateFeedback(latestFeedback)
            
            updateTrainingUIState()
        }
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
        // Standalone processing if not in TrainingActivity
        if (activity !is com.google.mediapipe.examples.poselandmarker.TrainingActivity) {
            poseAnalysisProcessor.processResults(resultBundle)
        }

        // Propagate to activity if it's a listener
        (activity as? PoseLandmarkerHelper.LandmarkerListener)?.onResults(resultBundle)

        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                uiController.updateInferenceTime(resultBundle.inferenceTime)

                // Pass results to OverlayView for drawing
                if (resultBundle.results.isNotEmpty()) {
                    fragmentCameraBinding.overlay.setResults(
                        resultBundle.results.first(),
                        resultBundle.inputImageHeight,
                        resultBundle.inputImageWidth,
                        com.google.mediapipe.tasks.vision.core.RunningMode.LIVE_STREAM
                    )

                    // Force redraw
                    fragmentCameraBinding.overlay.invalidate()
                }
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        // Propagate to activity
        (activity as? PoseLandmarkerHelper.LandmarkerListener)?.onError(error, errorCode)

        activity?.runOnUiThread {
            context?.let {
                Toast.makeText(it, error, Toast.LENGTH_SHORT).show()
                if (errorCode == PoseLandmarkerHelper.GPU_ERROR) {
                    uiController.setDelegateToCpu()
                }
            }
        }
    }
}
