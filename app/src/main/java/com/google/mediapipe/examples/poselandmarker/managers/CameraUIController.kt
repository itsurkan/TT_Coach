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

    /**
     * Initialize all bottom sheet controls and their listeners
     */
    fun initBottomSheetControls(
        currentDelegate: Int,
        currentModel: Int,
        onControlsChanged: () -> Unit
    ) {
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
        val helper = getPoseLandmarkerHelper() ?: return

        binding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(Locale.US, "%.2f", helper.minPoseDetectionConfidence)
        binding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(Locale.US, "%.2f", helper.minPoseTrackingConfidence)
        binding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(Locale.US, "%.2f", helper.minPosePresenceConfidence)
    }

    /**
     * Initialize threshold displays with initial values
     */
    fun initThresholdDisplays(
        detectionConfidence: Float,
        trackingConfidence: Float,
        presenceConfidence: Float
    ) {
        binding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(Locale.US, "%.2f", detectionConfidence)
        binding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(Locale.US, "%.2f", trackingConfidence)
        binding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(Locale.US, "%.2f", presenceConfidence)
    }

    /**
     * Setup detection threshold controls (plus/minus buttons)
     */
    private fun setupDetectionThresholdControls(onControlsChanged: () -> Unit) {
        binding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
            val helper = getPoseLandmarkerHelper() ?: return@setOnClickListener
            if (helper.minPoseDetectionConfidence >= 0.2) {
                helper.minPoseDetectionConfidence -= 0.1f
                onControlsChanged()
            }
        }

        binding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
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
        binding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
            val helper = getPoseLandmarkerHelper() ?: return@setOnClickListener
            if (helper.minPoseTrackingConfidence >= 0.2) {
                helper.minPoseTrackingConfidence -= 0.1f
                onControlsChanged()
            }
        }

        binding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
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
        binding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
            val helper = getPoseLandmarkerHelper() ?: return@setOnClickListener
            if (helper.minPosePresenceConfidence >= 0.2) {
                helper.minPosePresenceConfidence -= 0.1f
                onControlsChanged()
            }
        }

        binding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
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
        binding.bottomSheetLayout.spinnerDelegate.setSelection(currentDelegate, false)
        binding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
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
                    } catch (e: UninitializedPropertyAccessException) {
                        Log.e(TAG, "PoseLandmarkerHelper has not been initialized yet.")
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
        binding.bottomSheetLayout.spinnerModel.setSelection(currentModel, false)
        binding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
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
        binding.overlay.clear()
    }

    /**
     * Update inference time display
     */
    fun updateInferenceTime(timeMs: Long) {
        binding.bottomSheetLayout.inferenceTimeVal.text = String.format("%d ms", timeMs)
    }

    /**
     * Update delegate spinner to CPU (used on GPU error)
     */
    fun setDelegateToCpu() {
        binding.bottomSheetLayout.spinnerDelegate.setSelection(
            PoseLandmarkerHelper.DELEGATE_CPU,
            false
        )
    }
}
