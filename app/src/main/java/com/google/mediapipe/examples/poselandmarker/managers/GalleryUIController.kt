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

import android.view.View
import android.widget.AdapterView
import com.ttcoachai.MainViewModel
import com.ttcoachai.databinding.FragmentGalleryBinding
import com.ttcoachai.fragment.GalleryFragment
import java.util.*

/**
 * Handles UI updates and controls for GalleryFragment
 */
class GalleryUIController(
    private val binding: FragmentGalleryBinding,
    private val viewModel: MainViewModel
) {

    /**
     * Initialize all bottom sheet controls and their listeners
     */
    fun initBottomSheetControls(onControlsChanged: () -> Unit) {
        updateThresholdDisplays()
        setupDetectionThresholdControls(onControlsChanged)
        setupTrackingThresholdControls(onControlsChanged)
        setupPresenceThresholdControls(onControlsChanged)
        setupDelegateSpinner(onControlsChanged)
        setupModelSpinner(onControlsChanged)
    }

    /**
     * Update threshold values displayed in the bottom sheet
     */
    private fun updateThresholdDisplays() {
        binding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPoseDetectionConfidence
            )
        binding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPoseTrackingConfidence
            )
        binding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPosePresenceConfidence
            )
    }

    /**
     * Setup detection threshold controls (plus/minus buttons)
     */
    private fun setupDetectionThresholdControls(onControlsChanged: () -> Unit) {
        binding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
            if (viewModel.currentMinPoseDetectionConfidence >= 0.2) {
                viewModel.setMinPoseDetectionConfidence(
                    viewModel.currentMinPoseDetectionConfidence - 0.1f
                )
                onControlsChanged()
            }
        }

        binding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
            if (viewModel.currentMinPoseDetectionConfidence <= 0.8) {
                viewModel.setMinPoseDetectionConfidence(
                    viewModel.currentMinPoseDetectionConfidence + 0.1f
                )
                onControlsChanged()
            }
        }
    }

    /**
     * Setup tracking threshold controls (plus/minus buttons)
     */
    private fun setupTrackingThresholdControls(onControlsChanged: () -> Unit) {
        binding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
            if (viewModel.currentMinPoseTrackingConfidence >= 0.2) {
                viewModel.setMinPoseTrackingConfidence(
                    viewModel.currentMinPoseTrackingConfidence - 0.1f
                )
                onControlsChanged()
            }
        }

        binding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
            if (viewModel.currentMinPoseTrackingConfidence <= 0.8) {
                viewModel.setMinPoseTrackingConfidence(
                    viewModel.currentMinPoseTrackingConfidence + 0.1f
                )
                onControlsChanged()
            }
        }
    }

    /**
     * Setup presence threshold controls (plus/minus buttons)
     */
    private fun setupPresenceThresholdControls(onControlsChanged: () -> Unit) {
        binding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
            if (viewModel.currentMinPosePresenceConfidence >= 0.2) {
                viewModel.setMinPosePresenceConfidence(
                    viewModel.currentMinPosePresenceConfidence - 0.1f
                )
                onControlsChanged()
            }
        }

        binding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
            if (viewModel.currentMinPosePresenceConfidence <= 0.8) {
                viewModel.setMinPosePresenceConfidence(
                    viewModel.currentMinPosePresenceConfidence + 0.1f
                )
                onControlsChanged()
            }
        }
    }

    /**
     * Setup delegate spinner (CPU/GPU/NNAPI selection)
     */
    private fun setupDelegateSpinner(onControlsChanged: () -> Unit) {
        binding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate,
            false
        )
        binding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    viewModel.setDelegate(position)
                    onControlsChanged()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    /**
     * Setup model spinner (model selection)
     */
    private fun setupModelSpinner(onControlsChanged: () -> Unit) {
        binding.bottomSheetLayout.spinnerModel.setSelection(
            viewModel.currentModel,
            false
        )
        binding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    onControlsChanged()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    /**
     * Reset UI when controls are changed
     */
    fun resetDisplay() {
        if (binding.videoView.isPlaying) {
            binding.videoView.stopPlayback()
        }
        binding.videoView.visibility = View.GONE
        binding.imageResult.visibility = View.GONE
        binding.overlay.clear()
        updateThresholdDisplays()
        binding.tvPlaceholder.visibility = View.VISIBLE
    }

    /**
     * Update display view based on media type
     */
    fun updateDisplayView(mediaType: GalleryFragment.MediaType) {
        binding.imageResult.visibility =
            if (mediaType == GalleryFragment.MediaType.IMAGE) View.VISIBLE else View.GONE
        binding.videoView.visibility =
            if (mediaType == GalleryFragment.MediaType.VIDEO) View.VISIBLE else View.GONE
        binding.tvPlaceholder.visibility =
            if (mediaType == GalleryFragment.MediaType.UNKNOWN) View.VISIBLE else View.GONE
    }

    /**
     * Enable/disable UI controls
     */
    fun setUiEnabled(enabled: Boolean) {
        binding.fabGetContent.isEnabled = enabled
        binding.bottomSheetLayout.detectionThresholdMinus.isEnabled = enabled
        binding.bottomSheetLayout.detectionThresholdPlus.isEnabled = enabled
        binding.bottomSheetLayout.trackingThresholdMinus.isEnabled = enabled
        binding.bottomSheetLayout.trackingThresholdPlus.isEnabled = enabled
        binding.bottomSheetLayout.presenceThresholdMinus.isEnabled = enabled
        binding.bottomSheetLayout.presenceThresholdPlus.isEnabled = enabled
        binding.bottomSheetLayout.spinnerDelegate.isEnabled = enabled
    }

    /**
     * Show/hide progress indicator
     */
    fun showProgress(show: Boolean) {
        binding.progress.visibility = if (show) View.VISIBLE else View.GONE
    }

    /**
     * Update inference time display
     */
    fun updateInferenceTime(timeMs: Long) {
        binding.bottomSheetLayout.inferenceTimeVal.text = String.format("%d ms", timeMs)
    }
}
