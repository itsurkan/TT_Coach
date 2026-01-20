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
import com.google.mediapipe.examples.poselandmarker.managers.TrainingUIController
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
    private lateinit var uiController: TrainingUIController
    private var videoPlayerManager: VideoPlayerManager? = null
    
    // Analysis processing
    private lateinit var poseAnalysisProcessor: PoseAnalysisProcessor
    
    // Аналітика та параметри
    private lateinit var exerciseParameters: ExerciseParameters

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
        uiController = TrainingUIController(this, binding, stateManager)
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
            uiController = uiController
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

        // Calibrate button
        binding.btnCalibrate.setOnClickListener {
            startCalibration()
        }

        // Start/stop button
        binding.btnStartStop.setOnClickListener {
            if (stateManager.isTrainingActive) {
                stopTraining()
            } else {
                startTraining()
            }
        }

        // Initial state
        uiController.setupInitialState()
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
                    uiController.updateFeedbackText(status)
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
            
            uiController.updateFeedbackText(getString(R.string.camera_started))
        }
    }

    private fun startCalibration() {
        if (!useVideo) {
            // For camera mode: capture reference pose
            uiController.showCalibrationInProgress()
            
            // TODO: Capture actual pose from camera
            // For now, simulate calibration
            binding.root.postDelayed({
                uiController.showCalibrationComplete {
                    // Calibration complete callback
                }
            }, 3000)
        } else {
            // For video mode: skip calibration or use first frame
            uiController.updateFeedbackText(getString(R.string.calibration_skipped))
            uiController.showCalibrationComplete {
                // Ready to start training
            }
        }
    }

    private fun startTraining() {
        stateManager.startTraining()
        uiController.showTrainingStarted()
        uiController.updateStats()
        
        // Start analysis session
        poseAnalysisProcessor.startSession(
            exerciseId = exerciseId ?: "unknown",
            exerciseName = exerciseName ?: "Unknown Exercise"
        )
    }

    private fun stopTraining() {
        stateManager.stopTraining()
        uiController.showTrainingStopped()
        
        // End analysis session
        poseAnalysisProcessor.endSession()
        
        showSummary()
    }

    private fun showSummary() {
        uiController.showSummaryDialog(
            onFinish = { finish() },
            onContinue = { /* Continue training */ }
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (stateManager.isTrainingActive) {
                    uiController.showFinishTrainingDialog { finish() }
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
            uiController.updateFeedbackText(getString(R.string.pose_detection_error, error))
        }
    }
    
    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        // Process results using PoseAnalysisProcessor
        poseAnalysisProcessor.processResults(resultBundle) {
            // UI update callback (already runs on main thread via runOnUiThread in processor)
        }
    }
    
    companion object {
        private const val TAG = "TrainingActivity"
    }
}
