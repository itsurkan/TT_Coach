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

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.mediapipe.examples.poselandmarker.MainViewModel
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentGalleryBinding
import com.google.mediapipe.examples.poselandmarker.managers.GalleryMediaProcessor
import com.google.mediapipe.examples.poselandmarker.managers.GalleryUIController
import com.google.mediapipe.tasks.vision.core.RunningMode

class GalleryFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {

    enum class MediaType {
        IMAGE,
        VIDEO,
        UNKNOWN
    }

    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null
    private val fragmentGalleryBinding
        get() = _fragmentGalleryBinding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var uiController: GalleryUIController
    private lateinit var mediaProcessor: GalleryMediaProcessor

    private val getContent =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { mediaUri ->
                when (val mediaType = loadMediaType(mediaUri)) {
                    MediaType.IMAGE -> runDetectionOnImage(mediaUri)
                    MediaType.VIDEO -> runDetectionOnVideo(mediaUri)
                    MediaType.UNKNOWN -> {
                        uiController.updateDisplayView(mediaType)
                        Toast.makeText(
                            requireContext(),
                            "Unsupported data type.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentGalleryBinding =
            FragmentGalleryBinding.inflate(inflater, container, false)
        return fragmentGalleryBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize controllers
        uiController = GalleryUIController(fragmentGalleryBinding, viewModel)
        mediaProcessor = GalleryMediaProcessor(
            context = requireContext(),
            onImageLoaded = { bitmap ->
                activity?.runOnUiThread {
                    fragmentGalleryBinding.imageResult.setImageBitmap(bitmap)
                }
            },
            onImageResults = { result ->
                activity?.runOnUiThread {
                    fragmentGalleryBinding.overlay.setResults(
                        result.results[0],
                        result.inputImageHeight,
                        result.inputImageWidth,
                        RunningMode.IMAGE
                    )
                    uiController.setUiEnabled(true)
                    uiController.updateInferenceTime(result.inferenceTime)
                }
            },
            onVideoResults = { result ->
                activity?.runOnUiThread {
                    displayVideoResult(result)
                }
            },
            onError = { error, errorCode ->
                onError(error, errorCode)
            }
        )

        // Setup UI
        fragmentGalleryBinding.fabGetContent.setOnClickListener {
            getContent.launch(arrayOf("image/*", "video/*"))
        }

        uiController.initBottomSheetControls {
            updateControlsUi()
        }
    }

    override fun onPause() {
        fragmentGalleryBinding.overlay.clear()
        if (fragmentGalleryBinding.videoView.isPlaying) {
            fragmentGalleryBinding.videoView.stopPlayback()
        }
        fragmentGalleryBinding.videoView.visibility = View.GONE
        mediaProcessor.shutdown()
        super.onPause()
    }

    /**
     * Reset UI when controls are changed
     */
    private fun updateControlsUi() {
        uiController.resetDisplay()
    }

    /**
     * Run pose detection on selected image
     */
    private fun runDetectionOnImage(uri: Uri) {
        uiController.setUiEnabled(false)
        uiController.updateDisplayView(MediaType.IMAGE)

        mediaProcessor.processImage(
            uri = uri,
            minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
            minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
            minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
            currentDelegate = viewModel.currentDelegate
        )
    }

    /**
     * Run pose detection on selected video
     */
    private fun runDetectionOnVideo(uri: Uri) {
        uiController.setUiEnabled(false)
        uiController.updateDisplayView(MediaType.VIDEO)

        // Setup video view
        with(fragmentGalleryBinding.videoView) {
            setVideoURI(uri)
            setOnPreparedListener { it.setVolume(0f, 0f) }
            requestFocus()
        }

        // Show progress and start processing
        uiController.showProgress(true)
        fragmentGalleryBinding.videoView.visibility = View.GONE

        mediaProcessor.processVideo(
            uri = uri,
            minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
            minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
            minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
            currentDelegate = viewModel.currentDelegate
        )
    }

    /**
     * Display video with pose detection results
     */
    private fun displayVideoResult(result: PoseLandmarkerHelper.ResultBundle) {
        fragmentGalleryBinding.videoView.visibility = View.VISIBLE
        uiController.showProgress(false)

        fragmentGalleryBinding.videoView.start()

        mediaProcessor.scheduleVideoResultDisplay(
            result = result,
            getVideoPositionMs = {
                fragmentGalleryBinding.videoView.currentPosition
            },
            isVideoPlaying = {
                fragmentGalleryBinding.videoView.visibility == View.VISIBLE
            },
            onFrameUpdate = { resultIndex ->
                activity?.runOnUiThread {
                    fragmentGalleryBinding.overlay.setResults(
                        result.results[resultIndex],
                        result.inputImageHeight,
                        result.inputImageWidth,
                        RunningMode.VIDEO
                    )
                    uiController.setUiEnabled(true)
                    uiController.updateInferenceTime(result.inferenceTime)
                }
            }
        )
    }

    /**
     * Check the type of media that user selected
     */
    private fun loadMediaType(uri: Uri): MediaType {
        val mimeType = context?.contentResolver?.getType(uri)
        mimeType?.let {
            if (mimeType.startsWith("image")) return MediaType.IMAGE
            if (mimeType.startsWith("video")) return MediaType.VIDEO
        }
        return MediaType.UNKNOWN
    }

    /**
     * Handle classification errors
     */
    private fun classifyingError() {
        activity?.runOnUiThread {
            uiController.showProgress(false)
            uiController.setUiEnabled(true)
            uiController.updateDisplayView(MediaType.UNKNOWN)
        }
    }

    override fun onError(error: String, errorCode: Int) {
        classifyingError()
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == PoseLandmarkerHelper.GPU_ERROR) {
                fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    PoseLandmarkerHelper.DELEGATE_CPU,
                    false
                )
            }
        }
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        // no-op
    }

    companion object {
        private const val TAG = "GalleryFragment"
    }
}
