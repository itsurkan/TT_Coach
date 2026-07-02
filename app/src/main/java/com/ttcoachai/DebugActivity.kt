package com.ttcoachai

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
import com.ttcoachai.managers.*
import com.ttcoachai.shared.models.ExerciseParameters
import com.ttcoachai.processors.VideoDebugProcessor
import com.ttcoachai.services.FeedbackGenerator
import com.ttcoachai.services.MotionAnalyzer
import kotlinx.coroutines.launch

class DebugActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDebugBinding
    private lateinit var videoDebugProcessor: VideoDebugProcessor
    private lateinit var uiController: DebugUIController
    private lateinit var playbackManager: DebugPlaybackManager
    private lateinit var videoLoader: DebugVideoLoader
    private lateinit var settingsController: DebugSettingsController
    private lateinit var poseExporter: PoseExporter
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
    }

    private fun initializeManagers() {
        val parameters = ExerciseParameters.forehandDrive()
        val motionAnalyzer = MotionAnalyzer(parameters)
        val feedbackGenerator = FeedbackGenerator(this)
        val application = application as TTCoachApplication
        
        videoDebugProcessor = VideoDebugProcessor(this, motionAnalyzer, feedbackGenerator)
        uiController = DebugUIController(this, binding, videoDebugProcessor, feedbackGenerator)
        playbackManager = DebugPlaybackManager(this, binding, videoDebugProcessor, uiController, feedbackGenerator)
        videoLoader = DebugVideoLoader(this, binding, videoDebugProcessor, uiController, playbackManager)
        settingsController = DebugSettingsController(binding, application.settingsManager)
        poseExporter = PoseExporter(this)
    }

    private fun setupUI() {
        binding.btnLoadVideo.setOnClickListener { showVideoSelectionDialog() }
        binding.btnLoadVideoPortrait.setOnClickListener { showVideoSelectionDialog() }
        binding.btnLogPoses.setOnClickListener { exportPoses() }
        binding.btnLogPosesPortrait.setOnClickListener { exportPoses() }
        binding.btnToggleViewMode.setOnClickListener { toggleViewMode() }
        binding.btnToggleViewModePortrait.setOnClickListener { toggleViewMode() }
        
        binding.btnSeedSessions.setOnClickListener { seedDemoSessions() }

        binding.btnPlayPause.setOnClickListener { playbackManager.togglePlayPause() }
        binding.btnStepBack.setOnClickListener { playbackManager.stepPosition(-300) }
        binding.btnStepForward.setOnClickListener { playbackManager.stepPosition(300) }
        
        binding.btnSpeed025x.setOnClickListener { playbackManager.setPlaybackSpeed(0.25f) }
        binding.btnSpeed05x.setOnClickListener { playbackManager.setPlaybackSpeed(0.5f) }
        binding.btnSpeed1x.setOnClickListener { playbackManager.setPlaybackSpeed(1.0f) }
        binding.btnSpeed2x.setOnClickListener { playbackManager.setPlaybackSpeed(2.0f) }

        binding.seekBarFrame.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) playbackManager.seekToPosition(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {
                if (playbackManager.isPlaying()) playbackManager.pausePlayback()
            }
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        binding.btnReset.setOnClickListener { resetAnalysis() }
        
        listOf(
            binding.headerCurrentFrame to binding.contentCurrentFrame,
            binding.headerFeedback to binding.contentFeedback,
            binding.headerSessionSummary to binding.contentSessionSummary,
            binding.headerPlayback to binding.contentPlayback,
            binding.headerTechnical to binding.contentTechnical,
            binding.headerFeedbackSettings to binding.contentFeedbackSettings
        ).forEach { (h, c) -> setupCollapsiblePanel(h, c) }
        
        settingsController.setup()
    }

    private fun setupCollapsiblePanel(header: TextView, content: View) {
        header.setOnClickListener {
            val isVisible = content.visibility == View.VISIBLE
            content.visibility = if (isVisible) View.GONE else View.VISIBLE
            header.text = header.text.toString().replace(if (isVisible) "▼" else "▶", if (isVisible) "▶" else "▼")
        }
    }

    private fun toggleViewMode() {
        isPortraitMode = !isPortraitMode
        uiController.setPortraitMode(isPortraitMode)
        uiController.updateToggleViewButton(isPortraitMode)
    }

    private fun showVideoSelectionDialog() {
        val videoFiles = assets.list("Videos")?.filter { it.endsWith(".mp4") || it.endsWith(".3gp") || it.endsWith(".webm") }?.toMutableList() ?: mutableListOf()
        if (videoFiles.isEmpty()) {
            Toast.makeText(this, "No video files found", Toast.LENGTH_SHORT).show()
            return
        }
        videoFiles.sort()

        AlertDialog.Builder(this)
            .setTitle("Select Video (${videoFiles.size})")
            .setItems(videoFiles.toTypedArray()) { _, which ->
                videoLoader.loadVideoFromAssets("Videos/${videoFiles[which]}") { _, _ -> }
            }
            .show()
    }

    private fun resetAnalysis() {
        playbackManager.reset()
        videoLoader.reset()
    }

    private fun seedDemoSessions() {
        lifecycleScope.launch {
            com.ttcoachai.debug.SessionAnalyticsSeeder.seed(this@DebugActivity)
            Toast.makeText(this@DebugActivity, "Seeded demo sessions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportPoses() {
        if (!videoLoader.isVideoReady()) {
            Toast.makeText(this, "No video loaded", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            poseExporter.exportPoses(videoLoader.getCurrentVideoUri().toString(), videoDebugProcessor)
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
