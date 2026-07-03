package com.ttcoachai

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import com.ttcoachai.databinding.ActivityTrainingBinding
import com.ttcoachai.managers.*
import com.ttcoachai.shared.models.ExerciseParameters
import com.ttcoachai.processors.PoseAnalysisProcessor
import com.ttcoachai.services.FeedbackGenerator
import com.ttcoachai.services.MotionAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TrainingActivity : BaseActivity(), PoseLandmarkerHelper.LandmarkerListener {
    private lateinit var binding: ActivityTrainingBinding
    private var exerciseId: String? = null
    private var exerciseName: String? = null
    private var useVideo: Boolean = false
    
    private lateinit var stateManager: TrainingStateManager
    private lateinit var uiController: TrainingUIController
    private lateinit var mediaManager: TrainingMediaManager
    private lateinit var poseAnalysisProcessor: PoseAnalysisProcessor
    private lateinit var exerciseParameters: ExerciseParameters

    companion object {
        private const val TAG = "TrainingActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        exerciseId = intent.getStringExtra("EXERCISE_ID")
        exerciseName = intent.getStringExtra("EXERCISE_NAME")
        useVideo = intent.getBooleanExtra("USE_VIDEO", false)

        initializeManagers()
        initializeAnalysis()
        setupUI()
        setupBackNavigation()
    }
    
    private fun initializeManagers() {
        stateManager = TrainingStateManager.getInstance(this)
        uiController = TrainingUIController(
            this, binding, SettingsManager(this), stateManager,
            ::toggleTraining,
            { stopTraining(discard = false) }
        )
        mediaManager = TrainingMediaManager(this, binding, useVideo)
    }
    
    private fun initializeAnalysis() {
        val prefs = getSharedPreferences("ai_coach_prefs", MODE_PRIVATE)
        exerciseParameters = when (exerciseId) {
            "forehand_drive" -> ExerciseParameters(
                exerciseId = "forehand_drive",
                idealWristAngle = prefs.getInt("ideal_wrist_angle", 180).toFloat(),
                minBodyRotation = prefs.getInt("min_body_rotation", 45).toFloat(),
                followThroughAngle = prefs.getInt("follow_through_angle", 120).toFloat()
            )
            "forehand_andrii" -> ExerciseParameters.forehandDrive().copy(exerciseId = "forehand_andrii")
            "backhand_drive" -> ExerciseParameters.backhandDrive()
            else -> if (exerciseId?.startsWith("custom_") == true) {
                ExerciseParameters.forehandDrive().copy(exerciseId = exerciseId!!)
            } else {
                ExerciseParameters.forehandDrive()
            }
        }
        
        poseAnalysisProcessor = PoseAnalysisProcessor(
            application as TTCoachApplication,
            MotionAnalyzer(exerciseParameters),
            FeedbackGenerator(this),
            stateManager,
            { runOnUiThread { uiController.updateStats() } }
        )
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = exerciseName ?: getString(R.string.training_title)
            setDisplayHomeAsUpEnabled(true)
        }

        uiController.setup()
        mediaManager.setup()
        startTimerLoop()
        binding.root.postDelayed({ startTraining() }, 500)
    }

    private fun toggleTraining() {
        if (stateManager.isTrainingActive) pauseTraining() else resumeTraining()
    }

    private fun startTimerLoop() {
        val timerView = binding.root.findViewById<android.widget.TextView>(R.id.tv_timer)
        binding.root.post(object : Runnable {
            override fun run() {
                if (!isFinishing) {
                    timerView?.text = stateManager.getSessionTimeFormatted()
                    binding.root.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun startTraining() {
        stateManager.startTraining()
        uiController.updateUIForTrainingState(true)
        poseAnalysisProcessor.startSession(
            exerciseId ?: "forehand_drive",
            exerciseName ?: getString(R.string.exercise_forehand_name)
        )
    }

    private fun pauseTraining() {
        stateManager.pauseTraining()
        uiController.updateUIForTrainingState(false)
    }

    private fun resumeTraining() {
        stateManager.resumeTraining()
        uiController.updateUIForTrainingState(true)
    }

    private fun stopTraining(discard: Boolean = false) {
        stateManager.stopTraining()
        uiController.updateUIForTrainingState(false)
        poseAnalysisProcessor.endSession()
        
        if (discard) {
            android.widget.Toast.makeText(this, R.string.session_discarded, android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Save training session to cloud
        saveSessionToCloud()
        
        uiController.showSummary(
            drillName = exerciseName ?: getString(R.string.exercise_forehand_name),
            durationSeconds = stateManager.getSessionDurationSeconds(),
            strokeCount = stateManager.getStrokeCount(),
            cleanCount = stateManager.getGoodStrokesCount(),
            accuracyPercent = stateManager.getAverageScore().toInt()
        )
    }
    
    private fun saveSessionToCloud() {
        val app = application as TTCoachApplication
        
        val exerciseIdToSave = exerciseId ?: "forehand_drive"
        val exerciseNameToSave = exerciseName ?: getString(R.string.exercise_forehand_name)
        val startTimeValue = stateManager.getStartTime()
        val endTimeValue = stateManager.getEndTime()
        val durationSeconds = stateManager.getSessionDurationSeconds()
        val strokeCount = stateManager.getStrokeCount()
        val correctStrokes = stateManager.getGoodStrokesCount()
        val averageScore = stateManager.getAverageScore()

        if (durationSeconds < 5) {
            android.widget.Toast.makeText(this, "Training is too short", android.widget.Toast.LENGTH_SHORT).show()
            android.util.Log.d("TrainingActivity", "Training too short ($durationSeconds s), skipping save")
            return
        }

        // LOGGING FOR DEBUGGING
        android.util.Log.d("TrainingActivity", "Saving to cloud: exercise=$exerciseIdToSave, duration=$durationSeconds sec, strokes=$strokeCount, score=$averageScore")
        android.util.Log.d("TrainingActivity", "CloudSync authenticated: ${app.cloudSyncManager.isAuthenticated}")
        
        app.cloudSyncManager.saveTrainingFromState(
            exerciseId = exerciseIdToSave,
            exerciseName = exerciseNameToSave,
            startTime = startTimeValue,
            endTime = endTimeValue,
            durationSeconds = durationSeconds,
            strokeCount = strokeCount,
            correctStrokes = correctStrokes,
            averageScore = averageScore,
            appVersion = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" },
            onSaved = { sessionId ->
                app.sessionAnalyticsRecorder.record(
                    sessionId = sessionId,
                    results = stateManager.getAnalysisResults(),
                    feedback = stateManager.getLatestFeedbackItems()
                )
            }
        )
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (stateManager.isTrainingActive) {
                    pauseTraining()
                }
                val sheet = com.ttcoachai.ui.dialogs.EndSessionSheet.newInstance(
                    durationSeconds = stateManager.getSessionDurationSeconds(),
                    strokeCount = stateManager.getStrokeCount(),
                    accuracyPercent = stateManager.getAverageScore().toInt()
                )
                sheet.onKeepTraining = { resumeTraining() }
                sheet.onDiscard = { stopTraining(discard = true) }
                sheet.onFinishSave = { stopTraining(discard = false) }
                sheet.show(supportFragmentManager, com.ttcoachai.ui.dialogs.EndSessionSheet.TAG)
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaManager.release()
        if (::poseAnalysisProcessor.isInitialized) poseAnalysisProcessor.release()
    }
    
    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "Pose detection error: $error (code: $errorCode)")
    }
    
    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        poseAnalysisProcessor.processResults(resultBundle)
    }
}
