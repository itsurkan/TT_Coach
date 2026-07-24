package com.ttcoachai.managers

import android.view.View
import androidx.fragment.app.FragmentActivity
import com.ttcoachai.databinding.ActivityTrainingBinding
import com.ttcoachai.fragment.CameraFragment
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
     * @param skipCamera true when a caller (e.g. [com.ttcoachai.pose.RtmposeTrainingController])
     * is already taking over `cameraPreviewContainer` itself — the legacy [CameraFragment] must
     * not also be attached to the same container in that case.
     */
    fun setup(skipCamera: Boolean = false) {
        if (!skipCamera) {
            setupCamera()
        } else {
            binding.videoContainer.visibility = View.GONE
            binding.videoView.visibility = View.GONE
            binding.overlay.visibility = View.GONE
            binding.cameraPreviewContainer.visibility = View.VISIBLE
        }
    }

    private fun setupCamera() {
        binding.videoContainer.visibility = View.GONE
        binding.videoView.visibility = View.GONE
        binding.overlay.visibility = View.GONE
        binding.cameraPreviewContainer.visibility = View.VISIBLE
        
        activity.supportFragmentManager.beginTransaction()
            .replace(binding.cameraPreviewContainer.id, CameraFragment())
            .commit()
    }

    fun release() {
        videoPlayerManager?.release()
    }
}
