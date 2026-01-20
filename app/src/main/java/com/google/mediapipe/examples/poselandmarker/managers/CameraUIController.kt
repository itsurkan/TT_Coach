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
package com.google.mediapipe.examples.poselandmarker.managers

import android.util.Log
import android.view.View
import android.widget.AdapterView
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentCameraBinding
import java.util.*

/**
 * Handles UI updates and controls for CameraFragment
 */
class CameraUIController(
    private val binding: FragmentCameraBinding,
    private val getPoseLandmarkerHelper: () -> PoseLandmarkerHelper?
) {

    companion object {
        private const val TAG = "CameraUIController"
    }

    // Helper to safety check if bottom sheet is present in the layout
    private fun hasBottomSheet(): Boolean {
        // Since we are using data binding, we can check if the field exists via reflection 
        // or just rely on the fact that if we removed it from XML, we shouldn't call it.
        // For a more robust way in this specific task, we'll check the root view for the ID.
        return binding.root.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.bottom_sheet_layout) != null
    }

    /**
     * Initialize all bottom sheet controls and their listeners
     */
    fun initBottomSheetControls(
        currentDelegate: Int,
        currentModel: Int,
        onControlsChanged: () -> Unit
    ) {
        if (!hasBottomSheet()) return
        
        updateThresholdDisplays()
        setupDetectionThresholdControls(onControlsChanged)
        setupTrackingThresholdControls(onControlsChanged)
        setupPresenceThresholdControls(onControlsChanged)
        setupDelegateSpinner(currentDelegate, onControlsChanged)
        setupModelSpinner(currentModel, onControlsChanged)
    }

    /**
     * Update threshold values displayed in the bottom sheet
     */
    private fun updateThresholdDisplays() {
        if (!hasBottomSheet()) return
        val helper = getPoseLandmarkerHelper() ?: return

        try {
            val bottomSheet = binding.root.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.bottom_sheet_layout)
            if (bottomSheet != null) {
                // We use findViewById directly because the binding field might be missing in some versions of the layout
                val detectionValue = bottomSheet.findViewById<android.widget.TextView>(com.google.mediapipe.examples.poselandmarker.R.id.detection_threshold_value)
                val trackingValue = bottomSheet.findViewById<android.widget.TextView>(com.google.mediapipe.examples.poselandmarker.R.id.tracking_threshold_value)
                val presenceValue = bottomSheet.findViewById<android.widget.TextView>(com.google.mediapipe.examples.poselandmarker.R.id.presence_threshold_value)
                
                detectionValue?.text = String.format(Locale.US, "%.2f", helper.minPoseDetectionConfidence)
                trackingValue?.text = String.format(Locale.US, "%.2f", helper.minPoseTrackingConfidence)
                presenceValue?.text = String.format(Locale.US, "%.2f", helper.minPosePresenceConfidence)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating threshold displays: ${e.message}")
        }
    }

    /**
     * Initialize threshold displays with initial values
     */
    fun initThresholdDisplays(
        detectionConfidence: Float,
        trackingConfidence: Float,
        presenceConfidence: Float
    ) {
        if (!hasBottomSheet()) return
        
        try {
            val bottomSheet = binding.root.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.bottom_sheet_layout)
            if (bottomSheet != null) {
                bottomSheet.findViewById<android.widget.TextView>(com.google.mediapipe.examples.poselandmarker.R.id.detection_threshold_value)?.text =
                    String.format(Locale.US, "%.2f", detectionConfidence)
                bottomSheet.findViewById<android.widget.TextView>(com.google.mediapipe.examples.poselandmarker.R.id.tracking_threshold_value)?.text =
                    String.format(Locale.US, "%.2f", trackingConfidence)
                bottomSheet.findViewById<android.widget.TextView>(com.google.mediapipe.examples.poselandmarker.R.id.presence_threshold_value)?.text =
                    String.format(Locale.US, "%.2f", presenceConfidence)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing threshold displays: ${e.message}")
        }
    }

    /**
     * Setup detection threshold controls (plus/minus buttons)
     */
    private fun setupDetectionThresholdControls(onControlsChanged: () -> Unit) {
        val bottomSheet = binding.root.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.bottom_sheet_layout) ?: return
        
        bottomSheet.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.detection_threshold_minus)?.setOnClickListener {
            val helper = getPoseLandmarkerHelper() ?: return@setOnClickListener
            if (helper.minPoseDetectionConfidence >= 0.2) {
                helper.minPoseDetectionConfidence -= 0.1f
                onControlsChanged()
            }
        }

        bottomSheet.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.detection_threshold_plus)?.setOnClickListener {
            val helper = getPoseLandmarkerHelper() ?: return@setOnClickListener
            if (helper.minPoseDetectionConfidence <= 0.8) {
                helper.minPoseDetectionConfidence += 0.1f
                onControlsChanged()
            }
        }
    }

    /**
     * Setup tracking threshold controls (plus/minus buttons)
     */
    private fun setupTrackingThresholdControls(onControlsChanged: () -> Unit) {
        val bottomSheet = binding.root.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.bottom_sheet_layout) ?: return

        bottomSheet.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.tracking_threshold_minus)?.setOnClickListener {
            val helper = getPoseLandmarkerHelper() ?: return@setOnClickListener
            if (helper.minPoseTrackingConfidence >= 0.2) {
                helper.minPoseTrackingConfidence -= 0.1f
                onControlsChanged()
            }
        }

        bottomSheet.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.tracking_threshold_plus)?.setOnClickListener {
            val helper = getPoseLandmarkerHelper() ?: return@setOnClickListener
            if (helper.minPoseTrackingConfidence <= 0.8) {
                helper.minPoseTrackingConfidence += 0.1f
                onControlsChanged()
            }
        }
    }

    /**
     * Setup presence threshold controls (plus/minus buttons)
     */
    private fun setupPresenceThresholdControls(onControlsChanged: () -> Unit) {
        val bottomSheet = binding.root.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.bottom_sheet_layout) ?: return

        bottomSheet.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.presence_threshold_minus)?.setOnClickListener {
            val helper = getPoseLandmarkerHelper() ?: return@setOnClickListener
            if (helper.minPosePresenceConfidence >= 0.2) {
                helper.minPosePresenceConfidence -= 0.1f
                onControlsChanged()
            }
        }

        bottomSheet.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.presence_threshold_plus)?.setOnClickListener {
            val helper = getPoseLandmarkerHelper() ?: return@setOnClickListener
            if (helper.minPosePresenceConfidence <= 0.8) {
                helper.minPosePresenceConfidence += 0.1f
                onControlsChanged()
            }
        }
    }

    /**
     * Setup delegate spinner (CPU/GPU selection)
     */
    private fun setupDelegateSpinner(currentDelegate: Int, onControlsChanged: () -> Unit) {
        val bottomSheet = binding.root.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.bottom_sheet_layout) ?: return
        val spinner = bottomSheet.findViewById<android.widget.Spinner>(com.google.mediapipe.examples.poselandmarker.R.id.spinner_delegate) ?: return

        spinner.setSelection(currentDelegate, false)
        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    try {
                        val helper = getPoseLandmarkerHelper()
                        if (helper != null) {
                            helper.currentDelegate = position
                            onControlsChanged()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating delegate: ${e.message}")
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    /**
     * Setup model spinner (model selection)
     */
    private fun setupModelSpinner(currentModel: Int, onControlsChanged: () -> Unit) {
        val bottomSheet = binding.root.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.bottom_sheet_layout) ?: return
        val spinner = bottomSheet.findViewById<android.widget.Spinner>(com.google.mediapipe.examples.poselandmarker.R.id.spinner_model) ?: return

        spinner.setSelection(currentModel, false)
        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val helper = getPoseLandmarkerHelper()
                    if (helper != null) {
                        helper.currentModel = position
                        onControlsChanged()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    /**
     * Update UI controls when settings change
     */
    fun updateControlsUi() {
        updateThresholdDisplays()
        // Overlay visibility might change if we use it differently, but for now just clear
        try {
            // Find overlay by ID if not in binding (though it should be)
            val overlay = binding.root.findViewById<com.google.mediapipe.examples.poselandmarker.OverlayView>(com.google.mediapipe.examples.poselandmarker.R.id.overlay)
            overlay?.clear()
        } catch (e: Exception) {
            // fallback
            binding.overlay.clear()
        }
    }

    /**
     * Update inference time display
     */
    fun updateInferenceTime(timeMs: Long) {
        val bottomSheet = binding.root.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.bottom_sheet_layout) ?: return
        bottomSheet.findViewById<android.widget.TextView>(com.google.mediapipe.examples.poselandmarker.R.id.inference_time_val)?.text = 
            String.format("%d ms", timeMs)
    }

    /**
     * Update delegate spinner to CPU (used on GPU error)
     */
    fun setDelegateToCpu() {
        val bottomSheet = binding.root.findViewById<View>(com.google.mediapipe.examples.poselandmarker.R.id.bottom_sheet_layout) ?: return
        val spinner = bottomSheet.findViewById<android.widget.Spinner>(com.google.mediapipe.examples.poselandmarker.R.id.spinner_delegate) ?: return
        
        spinner.setSelection(
            PoseLandmarkerHelper.DELEGATE_CPU,
            false
        )
    }
}
