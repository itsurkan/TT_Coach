/*
 * AI Coach for Table Tennis
 * Training Screen with Camera and Analysis
 */

package com.google.mediapipe.examples.poselandmarker

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityTrainingBinding
import com.google.mediapipe.examples.poselandmarker.managers.TrainingStateManager
import com.google.mediapipe.examples.poselandmarker.managers.VideoPlayerManager
import com.google.mediapipe.examples.poselandmarker.models.ExerciseParameters
import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import com.google.mediapipe.examples.poselandmarker.models.StrokePhase
import com.google.mediapipe.examples.poselandmarker.services.FeedbackGenerator
import com.google.mediapipe.examples.poselandmarker.services.MotionAnalyzer
import com.google.mediapipe.examples.poselandmarker.processors.PoseAnalysisProcessor
import android.util.Log

class TrainingActivity : BaseActivity(), PoseLandmarkerHelper.LandmarkerListener {
    private lateinit var binding: ActivityTrainingBinding
    private var exerciseId: String? = null
    private var exerciseName: String? = null
    private var useVideo: Boolean = false
    
    // Managers
    private lateinit var stateManager: TrainingStateManager
    private var videoPlayerManager: VideoPlayerManager? = null
    private lateinit var feedbackAdapter: com.google.mediapipe.examples.poselandmarker.adapters.FeedbackListAdapter
    
    // Analysis processing
    private lateinit var poseAnalysisProcessor: PoseAnalysisProcessor
    
    // Аналітика та параметри
    private lateinit var exerciseParameters: ExerciseParameters

    companion object {
        private const val TAG = "TrainingActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Отримання даних вправи
        exerciseId = intent.getStringExtra("EXERCISE_ID")
        exerciseName = intent.getStringExtra("EXERCISE_NAME")
        useVideo = intent.getBooleanExtra("USE_VIDEO", false)

        // Ініціалізація менеджерів
        initializeManagers()
        
        // Ініціалізація аналітики
        initializeAnalysis()
        
        setupUI()
    }
    
    private fun initializeManagers() {
        stateManager = TrainingStateManager(this)
    }
    
    private fun initializeAnalysis() {
        // Завантажити параметри з SharedPreferences
        val prefs = getSharedPreferences("ai_coach_prefs", MODE_PRIVATE)
        val idealWristAngle = prefs.getInt("ideal_wrist_angle", 180)
        val minBodyRotation = prefs.getInt("min_body_rotation", 45)
        val followThroughAngle = prefs.getInt("follow_through_angle", 120)
        
        exerciseParameters = when (exerciseId) {
            "forehand_drive" -> ExerciseParameters.fromSharedPreferences(
                exerciseId = "forehand_drive",
                idealWristAngle = idealWristAngle,
                minBodyRotation = minBodyRotation,
                followThroughAngle = followThroughAngle
            )
            "backhand_drive" -> ExerciseParameters.backhandDrive()
            else -> ExerciseParameters.forehandDrive()
        }
        
        val motionAnalyzer = MotionAnalyzer(exerciseParameters)
        val feedbackGenerator = FeedbackGenerator(this)
        
        // Initialize pose analysis processor
        poseAnalysisProcessor = PoseAnalysisProcessor(
            application = application as TTCoachApplication,
            motionAnalyzer = motionAnalyzer,
            feedbackGenerator = feedbackGenerator,
            stateManager = stateManager,
            onUIUpdate = { updateStats() }
        )
    }

    private fun setupUI() {
        // Setup Action Bar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = exerciseName ?: getString(R.string.training_title)
            setDisplayHomeAsUpEnabled(true)
        }

        // Auto start camera or video
        startCameraPreview()

        // Setup bottom sheet behavior
        val bottomSheet = binding.bottomSheet
        val bottomSheetBehavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.peekHeight = 120
        bottomSheetBehavior.isHideable = false

        // Pause/Resume button in drawer
        binding.btnPauseResume.setOnClickListener {
            if (stateManager.isTrainingActive) {
                pauseTraining()
            } else {
                resumeTraining()
            }
        }

        // FAB Pause/Play button (center)
        binding.fabPausePlay.setOnClickListener {
            if (stateManager.isTrainingActive) {
                pauseTraining()
            } else {
                resumeTraining()
            }
        }

        // End Session button
        binding.btnEndSession.setOnClickListener {
            stopTraining()
        }

        // Setup Feedback RecyclerView
        feedbackAdapter = com.google.mediapipe.examples.poselandmarker.adapters.FeedbackListAdapter()
        binding.rvFeedbackList.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@TrainingActivity)
            adapter = feedbackAdapter
        }

        // Initial state - start training automatically
        binding.root.postDelayed({
            startTraining()
        }, 500)
    }

    private fun startCameraPreview() {
        if (useVideo) {
            // Debug mode: Load video from resources with pose detection
            binding.cameraPreviewContainer.visibility = View.GONE
            binding.videoContainer.visibility = View.VISIBLE
            binding.videoView.visibility = View.VISIBLE
            binding.overlay.visibility = View.VISIBLE
            
            videoPlayerManager = VideoPlayerManager(
                context = this,
                videoView = binding.videoView,
                overlayView = binding.overlay,
                onStatusChange = { status ->
                    // Status updates handled by UI
                }
            )
            
            videoPlayerManager?.playVideoWithPoseDetection("Videos/forehand_drive.mp4")
        } else {
            // Normal mode: Launch camera
            binding.videoContainer.visibility = View.GONE
            binding.videoView.visibility = View.GONE
            binding.cameraPreviewContainer.visibility = View.VISIBLE
            
            val cameraFragment = com.google.mediapipe.examples.poselandmarker.fragment.CameraFragment()
            supportFragmentManager.beginTransaction()
                .replace(binding.cameraPreviewContainer.id, cameraFragment)
                .commit()
        }
    }

    private fun startTraining() {
        stateManager.startTraining()
        updateUIForTrainingState(true)
        
        // Start analysis session
        poseAnalysisProcessor.startSession(
            exerciseId = exerciseId ?: "unknown",
            exerciseName = exerciseName ?: "Unknown Exercise"
        )
    }

    private fun pauseTraining() {
        stateManager.pauseTraining()
        updateUIForTrainingState(false)
    }

    private fun resumeTraining() {
        stateManager.resumeTraining()
        updateUIForTrainingState(true)
    }

    private fun stopTraining() {
        stateManager.stopTraining()
        
        // End analysis session
        poseAnalysisProcessor.endSession()
        
        showSummary()
    }

    private fun updateUIForTrainingState(isActive: Boolean) {
        if (isActive) {
            binding.btnPauseResume.text = "Pause"
            binding.btnPauseResume.setIconResource(android.R.drawable.ic_media_pause)
            binding.fabPausePlay.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            binding.btnPauseResume.text = "Resume"
            binding.btnPauseResume.setIconResource(android.R.drawable.ic_media_play)
            binding.fabPausePlay.setImageResource(android.R.drawable.ic_media_play)
        }
        updateStats()
    }

    private fun updateStats() {
        val strokeCount = stateManager.getStrokeCount()
        val avgScore = stateManager.getAverageScore().toInt()
        
        // Update overlay stats
        binding.tvHitsCount.text = strokeCount.toString()
        binding.tvAccuracyPercent.text = "$avgScore%"
        
        // Update drawer stats
        binding.tvTotalHits.text = strokeCount.toString()
        binding.tvAccuracy.text = "$avgScore%"
        
        // Update drill progress
        val progress = if (strokeCount > 0) (strokeCount * 100 / 20).coerceAtMost(100) else 0
        binding.progressDrill.progress = progress
        binding.tvDrillProgress.text = "$strokeCount/20 successful hits"
        
        // Update feedback list
        val feedbackItems = stateManager.getLatestFeedbackItems()
        feedbackAdapter.updateFeedback(feedbackItems)
    }

    private fun showSummary() {
        val summary = stateManager.getSummaryText()
        val tip = stateManager.getImprovementTip()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.training_summary_title))
            .setMessage("$summary\n\n$tip")
            .setPositiveButton(getString(R.string.btn_complete)) { _, _ ->
                finish()
            }
            .setNegativeButton(getString(R.string.btn_continue)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (stateManager.isTrainingActive) {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(getString(R.string.finish_training_title))
                        .setMessage(getString(R.string.finish_training_message))
                        .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                            finish()
                        }
                        .setNegativeButton(getString(R.string.dialog_no), null)
                        .show()
                } else {
                    finish()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        videoPlayerManager?.release()
        if (::poseAnalysisProcessor.isInitialized) {
            poseAnalysisProcessor.release()
        }
    }
    
    // PoseLandmarkerHelper.LandmarkerListener implementation
    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Log.e(TAG, "Pose detection error: $error (code: $errorCode)")
        }
    }
    
    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        // Process results using PoseAnalysisProcessor
        poseAnalysisProcessor.processResults(resultBundle) {
            // UI update callback (already runs on main thread via runOnUiThread in processor)
        }
    }
}
