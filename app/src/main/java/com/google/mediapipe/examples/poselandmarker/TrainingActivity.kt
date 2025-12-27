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
import com.google.mediapipe.examples.poselandmarker.services.FeedbackGenerator
import com.google.mediapipe.examples.poselandmarker.services.MotionAnalyzer

class TrainingActivity : BaseActivity(), PoseLandmarkerHelper.LandmarkerListener {
    private lateinit var binding: ActivityTrainingBinding
    private var exerciseId: String? = null
    private var exerciseName: String? = null
    private var useVideo: Boolean = false
    
    // Managers
    private lateinit var stateManager: TrainingStateManager
    private lateinit var uiController: TrainingUIController
    private var videoPlayerManager: VideoPlayerManager? = null
    
    // Аналітика та параметри
    private lateinit var exerciseParameters: ExerciseParameters
    private lateinit var motionAnalyzer: MotionAnalyzer
    private lateinit var feedbackGenerator: FeedbackGenerator

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
        stateManager = TrainingStateManager()
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
        
        motionAnalyzer = MotionAnalyzer(exerciseParameters)
        feedbackGenerator = FeedbackGenerator()
    }

    private fun setupUI() {
        // Setup Action Bar
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
            binding.videoView.visibility = View.VISIBLE // Show immediately
            binding.overlay.visibility = View.VISIBLE
            
            videoPlayerManager = VideoPlayerManager(
                context = this,
                videoView = binding.videoView,
                overlayView = binding.overlay,
                onStatusChange = { status ->
                    uiController.updateFeedbackText(status)
                }
            )
            
            videoPlayerManager?.playVideoWithPoseDetection(R.raw.forehand_drive)
        } else {
            // Normal mode: Launch camera
            binding.videoView.visibility = View.GONE
            binding.cameraPreviewContainer.visibility = View.VISIBLE
            
            val cameraFragment = com.google.mediapipe.examples.poselandmarker.fragment.CameraFragment()
            supportFragmentManager.beginTransaction()
                .replace(binding.cameraPreviewContainer.id, cameraFragment)
                .commit()
            
            uiController.updateFeedbackText("📹 Камера запущена. Натисніть 'Калібрувати'")
        }
    }

    private fun startCalibration() {
        uiController.showCalibrationInProgress()

        // Simulate calibration (3 sec)
        binding.root.postDelayed({
            uiController.showCalibrationComplete {
                // Calibration complete callback
            }
        }, 3000)
    }

    private fun startTraining() {
        stateManager.startTraining()
        uiController.showTrainingStarted()
        uiController.updateStats()

        // Симуляція фідбеку (в реальності аналіз з MediaPipe)
        uiController.simulateFeedback {
            uiController.updateStats()
        }
    }

    private fun stopTraining() {
        stateManager.stopTraining()
        uiController.showTrainingStopped()
        
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
    }
    
    // PoseLandmarkerHelper.LandmarkerListener implementation
    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            uiController.updateFeedbackText("❌ Pose detection error: $error")
        }
    }
    
    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        // This is called for LIVE_STREAM mode (camera), not used for video
    }
}
