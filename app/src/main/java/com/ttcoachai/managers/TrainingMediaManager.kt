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
    private val binding: ActivityTrainingBinding,
    private val useVideo: Boolean
) {
    private var videoPlayerManager: VideoPlayerManager? = null

    /**
     * @param skipCamera true when a caller (e.g. [com.ttcoachai.pose.RtmposeTrainingController])
     * is already taking over `cameraPreviewContainer` itself — the legacy [CameraFragment] must
     * not also be attached to the same container in that case. Has no effect on the video branch.
     */
    fun setup(skipCamera: Boolean = false) {
        if (useVideo) {
            setupVideo()
        } else if (!skipCamera) {
            setupCamera()
        } else {
            binding.videoContainer.visibility = View.GONE
            binding.videoView.visibility = View.GONE
            binding.overlay.visibility = View.GONE
            binding.cameraPreviewContainer.visibility = View.VISIBLE
        }
    }

    private fun setupVideo() {
        binding.cameraPreviewContainer.visibility = View.GONE
        binding.videoContainer.visibility = View.VISIBLE
        binding.videoView.visibility = View.VISIBLE
        binding.overlay.visibility = View.VISIBLE
        
        videoPlayerManager = VideoPlayerManager(
            context = activity,
            videoView = binding.videoView,
            overlayView = binding.overlay,
            onStatusChange = {}
        )
        
        videoPlayerManager?.playVideoWithPoseDetection("Videos/forehand_drive.mp4")
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
