/*
 * AI Coach for Table Tennis
 * Debug Activity - Video-based debugging using batch processing (like GalleryFragment)
 * Refactored to use Manager Pattern for separation of concerns
 */

package com.google.mediapipe.examples.poselandmarker

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityDebugBinding
import com.google.mediapipe.examples.poselandmarker.managers.DebugPlaybackManager
import com.google.mediapipe.examples.poselandmarker.managers.DebugUIController
import com.google.mediapipe.examples.poselandmarker.managers.DebugVideoLoader
import com.google.mediapipe.examples.poselandmarker.models.ExerciseParameters
import com.google.mediapipe.examples.poselandmarker.processors.VideoDebugProcessor
import com.google.mediapipe.examples.poselandmarker.services.FeedbackGenerator
import com.google.mediapipe.examples.poselandmarker.services.MotionAnalyzer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Debug Activity for video-based analysis testing
 * Uses batch processing for reliable pose detection
 */
class DebugActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugBinding
    private lateinit var videoDebugProcessor: VideoDebugProcessor
    private lateinit var uiController: DebugUIController
    private lateinit var playbackManager: DebugPlaybackManager
    private lateinit var videoLoader: DebugVideoLoader
    private var isPortraitMode = true

    companion object {
        private const val TAG = "DebugActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Video Debug"
        }

        initializeManagers()
        setupUI()
        uiController.setPortraitMode(isPortraitMode)
        uiController.updateToggleViewButton(isPortraitMode)
        loadDefaultVideo()
    }

    private fun initializeManagers() {
        val parameters = ExerciseParameters.forehandDrive()
        val motionAnalyzer = MotionAnalyzer(parameters)
        val feedbackGenerator = FeedbackGenerator(this)

        val application = application as TTCoachApplication
        val fileLogger = application.getFileLogger()
        videoDebugProcessor = VideoDebugProcessor(
            context = this,
            motionAnalyzer = motionAnalyzer,
            feedbackGenerator = feedbackGenerator,
            fileLogger = fileLogger
        )

        uiController = DebugUIController(this, binding, videoDebugProcessor, feedbackGenerator)
        playbackManager = DebugPlaybackManager(this, binding, videoDebugProcessor, uiController)
        videoLoader = DebugVideoLoader(this, binding, videoDebugProcessor, uiController, playbackManager)
    }

    private fun setupUI() {
        // Video selection and logging
        binding.btnLoadVideo.setOnClickListener { showVideoSelectionDialog() }
        binding.btnLoadVideoPortrait.setOnClickListener { showVideoSelectionDialog() }
        binding.btnLogPoses.setOnClickListener { exportPosesToFile() }
        binding.btnLogPosesPortrait.setOnClickListener { exportPosesToFile() }

        // View mode toggle
        binding.btnToggleViewMode.setOnClickListener { toggleViewMode() }
        binding.btnToggleViewModePortrait.setOnClickListener { toggleViewMode() }

        // Playback controls
        binding.btnPlayPause.setOnClickListener { playbackManager.togglePlayPause() }
        binding.btnPlayPausePortrait.setOnClickListener { playbackManager.togglePlayPause() }
        binding.btnStepBack.setOnClickListener { playbackManager.stepPosition(-300) }
        binding.btnStepForward.setOnClickListener { playbackManager.stepPosition(300) }
        binding.btnStepBackPortrait.setOnClickListener { playbackManager.stepPosition(-300) }
        binding.btnStepForwardPortrait.setOnClickListener { playbackManager.stepPosition(300) }

        // Speed control - landscape
        binding.btnSpeed025x.setOnClickListener { playbackManager.setPlaybackSpeed(0.25f) }
        binding.btnSpeed05x.setOnClickListener { playbackManager.setPlaybackSpeed(0.5f) }
        binding.btnSpeed1x.setOnClickListener { playbackManager.setPlaybackSpeed(1.0f) }
        binding.btnSpeed2x.setOnClickListener { playbackManager.setPlaybackSpeed(2.0f) }

        // Speed control - portrait
        binding.btnSpeed025xPortrait.setOnClickListener { playbackManager.setPlaybackSpeed(0.25f) }
        binding.btnSpeed05xPortrait.setOnClickListener { playbackManager.setPlaybackSpeed(0.5f) }
        binding.btnSpeed1xPortrait.setOnClickListener { playbackManager.setPlaybackSpeed(1.0f) }
        binding.btnSpeed2xPortrait.setOnClickListener { playbackManager.setPlaybackSpeed(2.0f) }

        // Seek bar - both modes
        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) playbackManager.seekToPosition(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                if (playbackManager.isPlaying()) playbackManager.pausePlayback()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        binding.seekBarFrame.setOnSeekBarChangeListener(seekBarListener)
        binding.seekBarFramePortrait.setOnSeekBarChangeListener(seekBarListener)
        binding.btnReset.setOnClickListener { resetAnalysis() }
    }

    private fun loadDefaultVideo() {
        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.forehand_drive}")
        videoLoader.loadVideo(videoUri) { _, _ -> }
    }

    private fun toggleViewMode() {
        isPortraitMode = !isPortraitMode
        uiController.setPortraitMode(isPortraitMode)
        uiController.updateToggleViewButton(isPortraitMode)
    }


    private fun showVideoSelectionDialog() {
        val videoOptions = arrayOf(
            "forehand_drive.mp4 (Default)",
            "Choose from gallery..."
        )

        AlertDialog.Builder(this)
            .setTitle("Select Test Video")
            .setItems(videoOptions) { _, which ->
                when (which) {
                    0 -> loadDefaultVideo()
                    1 -> loadDefaultVideo()
                }
            }
            .show()
    }

    private fun resetAnalysis() {
        playbackManager.reset()
        videoLoader.reset()
    }

    private fun exportPosesToFile() {
        if (!videoLoader.isVideoReady()) {
            Toast.makeText(this, "No video loaded", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val poses = videoDebugProcessor.getAllPoseResults()
            if (poses.isEmpty()) {
                Toast.makeText(this, "No poses to export", Toast.LENGTH_SHORT).show()
                return
            }

            val jsonRoot = JSONObject().apply {
                put("videoUri", videoLoader.getCurrentVideoUri().toString())
                put("intervalMs", 100)
                put("totalFrames", poses.size)
                put("exportTimestamp", System.currentTimeMillis())
            }

            val framesArray = JSONArray()
            poses.forEachIndexed { index, poseResult ->
                val frameObj = JSONObject().apply {
                    put("frameIndex", index)
                    put("timestampMs", index * 100L)
                }

                if (poseResult.landmarks().isNotEmpty()) {
                    val landmarksArray = JSONArray()
                    poseResult.landmarks()[0].forEach { landmark ->
                        landmarksArray.put(JSONObject().apply {
                            put("x", landmark.x())
                            put("y", landmark.y())
                            put("z", landmark.z())
                            put("visibility", landmark.visibility().orElse(0f))
                            put("presence", landmark.presence().orElse(0f))
                        })
                    }
                    frameObj.put("landmarks", landmarksArray)
                } else {
                    frameObj.put("landmarks", JSONArray())
                }
                framesArray.put(frameObj)
            }

            jsonRoot.put("frames", framesArray)

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US).format(Date())
            val file = File(getExternalFilesDir(null), "poses_$timestamp.json")
            file.writeText(jsonRoot.toString(2))

            Toast.makeText(this, "Poses exported to:\n${file.absolutePath}\n\nFrames: ${poses.size}", Toast.LENGTH_LONG).show()
            Log.i(TAG, "Exported ${poses.size} poses to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting poses", e)
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        playbackManager.release()
        videoDebugProcessor.close()
    }
}
