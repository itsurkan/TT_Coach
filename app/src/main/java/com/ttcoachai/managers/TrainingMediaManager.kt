package com.ttcoachai.managers

import android.view.View
import androidx.fragment.app.FragmentActivity
import com.ttcoachai.databinding.ActivityTrainingBinding
import com.ttcoachai.R

/**
 * Manages camera and video playback for TrainingActivity
 */
class TrainingMediaManager(
    private val activity: FragmentActivity,
    private val binding: ActivityTrainingBinding
) {
    private var videoPlayerManager: VideoPlayerManager? = null

    /**
     * The caller (e.g. [com.ttcoachai.pose.RtmposeTrainingController]) takes over
     * `cameraPreviewContainer` itself, so this only prepares the container's visibility.
     */
    fun setup() {
        binding.videoContainer.visibility = View.GONE
        binding.videoView.visibility = View.GONE
        binding.overlay.visibility = View.GONE
        binding.cameraPreviewContainer.visibility = View.VISIBLE
    }

    fun release() {
        videoPlayerManager?.release()
    }
}
