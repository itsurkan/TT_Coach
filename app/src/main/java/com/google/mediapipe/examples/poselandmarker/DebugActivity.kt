/*
 * AI Coach for Table Tennis
 * Debug Activity - Video-based debugging using batch processing (like GalleryFragment)
 * Refactored to use Manager Pattern for separation of concerns
 */

package com.ttcoachai

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ttcoachai.databinding.ActivityDebugBinding
import com.ttcoachai.managers.DebugPlaybackManager
import com.ttcoachai.managers.DebugSettingsController
import com.ttcoachai.managers.DebugUIController
import com.ttcoachai.managers.DebugVideoLoader
import com.ttcoachai.models.ExerciseParameters
import com.ttcoachai.processors.VideoDebugProcessor
import com.ttcoachai.services.FeedbackGenerator
import com.ttcoachai.services.MotionAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private lateinit var settingsController: DebugSettingsController
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
            title = "Video Debug" // Consider localizing title too? Not critical as "Video Debug" is technical.
            title = "Video Debug"
        }

        initializeManagers()
        setupUI()
        uiController.setPortraitMode(isPortraitMode)
        uiController.updateToggleViewButton(isPortraitMode)
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
        playbackManager = DebugPlaybackManager(this, binding, videoDebugProcessor, uiController, feedbackGenerator)
        videoLoader = DebugVideoLoader(this, binding, videoDebugProcessor, uiController, playbackManager)
        settingsController = DebugSettingsController(binding, application.settingsManager)
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
        binding.btnStepBack.setOnClickListener { playbackManager.stepPosition(-300) }
        binding.btnStepForward.setOnClickListener { playbackManager.stepPosition(300) }

        // Speed control
        binding.btnSpeed025x.setOnClickListener { playbackManager.setPlaybackSpeed(0.25f) }
        binding.btnSpeed05x.setOnClickListener { playbackManager.setPlaybackSpeed(0.5f) }
        binding.btnSpeed1x.setOnClickListener { playbackManager.setPlaybackSpeed(1.0f) }
        binding.btnSpeed2x.setOnClickListener { playbackManager.setPlaybackSpeed(2.0f) }

        // Seek bar
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
        binding.btnReset.setOnClickListener { resetAnalysis() }
        
        // Collapsible panels
        setupCollapsiblePanel(binding.headerCurrentFrame, binding.contentCurrentFrame)
        setupCollapsiblePanel(binding.headerFeedback, binding.contentFeedback)
        setupCollapsiblePanel(binding.headerSessionSummary, binding.contentSessionSummary)
        setupCollapsiblePanel(binding.headerPlayback, binding.contentPlayback)
        setupCollapsiblePanel(binding.headerTechnical, binding.contentTechnical)
        setupCollapsiblePanel(binding.headerFeedbackSettings, binding.contentFeedbackSettings)
        
        settingsController.setup()
    }

    private fun setupCollapsiblePanel(header: TextView, content: View) {
        Log.d(TAG, "Setting up collapsible panel for header: ${header.text}")

        header.setOnClickListener {
            Log.d(TAG, "!!! CLICK DETECTED !!! Header: ${header.text}")

            // Toggle visibility
            if (content.visibility == View.VISIBLE) {
                content.visibility = View.GONE
                header.text = header.text.toString().replace("▼", "▶")
                Log.d(TAG, "Collapsed content")
            } else {
                content.visibility = View.VISIBLE
                header.text = header.text.toString().replace("▶", "▼")
                Log.d(TAG, "Expanded content")
            }
        }
    }

    private fun toggleViewMode() {
        isPortraitMode = !isPortraitMode
        uiController.setPortraitMode(isPortraitMode)
        uiController.updateToggleViewButton(isPortraitMode)
    }


    private fun showVideoSelectionDialog() {
        // Discover video files from assets/Videos folder
        val videoFiles = mutableListOf<String>()

        try {
            val files = assets.list("Videos") ?: emptyArray()
            for (file in files) {
                // Only show video files
                if (file.endsWith(".mp4") || file.endsWith(".3gp") || file.endsWith(".webm")) {
                    videoFiles.add(file)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing assets", e)
        }

        if (videoFiles.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_video_files), Toast.LENGTH_SHORT).show()
            return
        }

        // Sort alphabetically
        videoFiles.sort()

        AlertDialog.Builder(this)
            .setTitle("Select Video (${videoFiles.size} available)")
            .setItems(videoFiles.toTypedArray()) { _, which ->
                val videoPath = "Videos/${videoFiles[which]}"
                videoLoader.loadVideoFromAssets(videoPath) { _, _ -> }
            }
            .show()
    }

    private fun resetAnalysis() {
        playbackManager.reset()
        videoLoader.reset()
    }

    private fun exportPosesToFile() {
        if (!videoLoader.isVideoReady()) {
            Toast.makeText(this, getString(R.string.toast_no_video_loaded), Toast.LENGTH_SHORT).show()
            return
        }

        val poses = videoDebugProcessor.getAllPoseResults()
        if (poses.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_poses_export), Toast.LENGTH_SHORT).show()
            return
        }

        val videoUriString = videoLoader.getCurrentVideoUri().toString()
        val filesDir = getExternalFilesDir(null)

        Toast.makeText(this, getString(R.string.toast_exporting_background, poses.size), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Heavy JSON construction
                val jsonRoot = JSONObject().apply {
                    put("videoUri", videoUriString)
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

                // File writing
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US).format(Date())
                val file = File(filesDir, "poses_$timestamp.json")
                file.writeText(jsonRoot.toString(2))

                // UI Update
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DebugActivity, getString(R.string.toast_export_success, file.absolutePath, poses.size), Toast.LENGTH_LONG).show()
                    Log.i(TAG, "Exported ${poses.size} poses to ${file.absolutePath}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Error exporting poses", e)
                    Toast.makeText(this@DebugActivity, getString(R.string.toast_export_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
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
