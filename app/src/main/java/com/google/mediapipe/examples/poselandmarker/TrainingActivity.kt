/*
 * AI Coach for Table Tennis
 * Training Screen with Camera and Analysis
 */

package com.google.mediapipe.examples.poselandmarker

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.MenuItem
import android.view.View
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityTrainingBinding
import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import com.google.mediapipe.examples.poselandmarker.models.ExerciseParameters
import com.google.mediapipe.examples.poselandmarker.services.FeedbackGenerator
import com.google.mediapipe.examples.poselandmarker.services.MotionAnalyzer
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class TrainingActivity : BaseActivity(), PoseLandmarkerHelper.LandmarkerListener {
    private lateinit var binding: ActivityTrainingBinding
    private var exerciseId: String? = null
    private var exerciseName: String? = null
    private var useVideo: Boolean = false
    private var isTrainingActive = false
    private val feedbackHistory = mutableListOf<String>()
    
    // Аналітика та параметри
    private lateinit var exerciseParameters: ExerciseParameters
    private lateinit var motionAnalyzer: MotionAnalyzer
    private lateinit var feedbackGenerator: FeedbackGenerator
    private val analysisResults = mutableListOf<AnalysisResult>()
    private var consecutiveGoodStrokes = 0
    
    // Pose detection for video
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private lateinit var backgroundExecutor: ScheduledExecutorService
    
    companion object {
        private const val TAG = "TrainingActivity"
        private const val VIDEO_INTERVAL_MS = 300L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Отримання даних вправи
        exerciseId = intent.getStringExtra("EXERCISE_ID")
        exerciseName = intent.getStringExtra("EXERCISE_NAME")
        useVideo = intent.getBooleanExtra("USE_VIDEO", false)

        // Ініціалізація аналітики
        initializeAnalysis()
        
        setupUI()
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

        // Auto start camera
        startCameraPreview()

        // Calibrate button
        binding.btnCalibrate.setOnClickListener {
            startCalibration()
        }

        // Start/stop button
        binding.btnStartStop.setOnClickListener {
            if (isTrainingActive) {
                stopTraining()
            } else {
                startTraining()
            }
        }

        // Initial state
        binding.btnStartStop.isEnabled = false
        updateFeedbackText(getString(R.string.press_calibrate))
    }

    private fun startCameraPreview() {
        if (useVideo) {
            // Debug mode: Load video from resources with pose detection
            binding.feedbackText.text = "🎬 Debug mode: Processing video..."
            binding.cameraPreviewContainer.visibility = View.GONE
            binding.videoView.visibility = View.VISIBLE
            
            // Load video from raw resources
            val videoUri = Uri.parse("android.resource://$packageName/${R.raw.forehand_drive}")
            binding.videoView.setVideoURI(videoUri)
            
            // Set up video playback
            binding.videoView.setOnPreparedListener { mediaPlayer ->
                mediaPlayer.setVolume(0f, 0f) // Mute audio
                
                // Scale video to fill width (crop height if needed)
                val videoWidth = mediaPlayer.videoWidth
                val videoHeight = mediaPlayer.videoHeight
                val viewWidth = binding.videoView.width
                val viewHeight = binding.videoView.height
                
                if (videoWidth > 0 && videoHeight > 0 && viewWidth > 0 && viewHeight > 0) {
                    val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
                    val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
                    
                    if (videoRatio < viewRatio) {
                        // Video is narrower, scale up to fill width
                        val scale = viewWidth.toFloat() / videoWidth.toFloat()
                        binding.videoView.scaleX = scale
                        binding.videoView.scaleY = scale
                    }
                }
            }
            
            binding.videoView.setOnErrorListener { _, what, extra ->
                binding.feedbackText.text = "❌ Error loading video: $what, $extra"
                false
            }
            
            // Process video with pose detection
            processVideoWithPoseDetection(videoUri)
        } else {
            // Normal mode: Launch camera
            binding.videoView.visibility = View.GONE
            binding.cameraPreviewContainer.visibility = View.VISIBLE
            
            val cameraFragment = com.google.mediapipe.examples.poselandmarker.fragment.CameraFragment()
            supportFragmentManager.beginTransaction()
                .replace(binding.cameraPreviewContainer.id, cameraFragment)
                .commit()
            
            binding.feedbackText.text = "📹 Камера запущена. Натисніть 'Калібрувати'"
        }
    }

    private fun startCalibration() {
        binding.tvCalibrationStatus.visibility = View.VISIBLE
        binding.tvCalibrationStatus.text = getString(R.string.calibration_prompt) + "\n\n• Ноги на ширині плечей\n• Трохи присядьте\n• Ракетка перед собою"
        binding.btnCalibrate.isEnabled = false
        updateFeedbackText(getString(R.string.calibrating))

        // Simulate calibration (3 sec)
        binding.root.postDelayed({
            binding.tvCalibrationStatus.text = "✅ Калібрування завершено!"
            binding.root.postDelayed({
                binding.tvCalibrationStatus.visibility = View.GONE
                binding.btnStartStop.isEnabled = true
                binding.btnCalibrate.isEnabled = true
                updateFeedbackText(getString(R.string.ready_to_train))
                android.widget.Toast.makeText(this, getString(R.string.ready_toast), android.widget.Toast.LENGTH_SHORT).show()
            }, 1000)
        }, 3000)
    }

    private fun startTraining() {
        isTrainingActive = true
        binding.btnStartStop.text = getString(R.string.btn_pause)
        binding.btnStartStop.setBackgroundColor(getColor(android.R.color.holo_red_light))
        binding.btnCalibrate.isEnabled = false
        binding.layoutStats.visibility = View.VISIBLE
        updateFeedbackText("🏓 Тренування активне - виконуйте удари!")
        
        updateStats()

        // Симуляція фідбеку (в реальності аналіз з MediaPipe)
        simulateFeedback()
    }

    private fun stopTraining() {
        isTrainingActive = false
        binding.btnStartStop.text = "▶ Почати"
        binding.btnStartStop.setBackgroundColor(getColor(android.R.color.holo_green_light))
        binding.btnCalibrate.isEnabled = true
        binding.layoutStats.visibility = View.GONE
        updateFeedbackText(getString(R.string.training_paused))
        
        showSummary()
    }

    private fun updateFeedbackText(text: String) {
        binding.feedbackText.text = text
    }

    private fun updateStats() {
        val totalStrokes = analysisResults.size
        val successfulStrokes = analysisResults.count { it.isSuccessful() }
        val accuracy = if (totalStrokes > 0) {
            (successfulStrokes.toFloat() / totalStrokes * 100).toInt()
        } else {
            0
        }
        
        binding.tvStrokeCount.text = getString(R.string.stroke_count, totalStrokes)
        binding.tvAverageScore.text = getString(R.string.accuracy, accuracy)
    }

    private fun simulateFeedback() {
        if (!isTrainingActive) return

        val simulatedResult = generateSimulatedAnalysisResult()
        analysisResults.add(simulatedResult)
        
        val shortFeedback = feedbackGenerator.generateShortFeedback(simulatedResult)
        val feedbackColor = if (simulatedResult.isSuccessful()) {
            getColor(android.R.color.holo_green_dark)
        } else {
            getColor(android.R.color.holo_orange_dark)
        }
        
        binding.feedbackText.text = shortFeedback
        binding.feedbackText.setTextColor(feedbackColor)
        
        feedbackHistory.add(shortFeedback)
        
        // Підрахунок успішних ударів підряд
        if (simulatedResult.isSuccessful()) {
            consecutiveGoodStrokes++
            
            // Мотиваційне повідомлення
            feedbackGenerator.generateMotivationalMessage(consecutiveGoodStrokes)?.let { message ->
                binding.root.postDelayed({
                    binding.feedbackText.text = message
                    binding.feedbackText.setTextColor(getColor(android.R.color.holo_green_dark))
                }, 1000)
            }
        } else {
            consecutiveGoodStrokes = 0
        }
        
        updateStats()

        // Наступний фідбек через 3-5 секунд
        binding.root.postDelayed({
            simulateFeedback()
        }, (3000..5000).random().toLong())
    }
    
    /**
     * Генерувати симульований результат аналізу для тестування
     * В реальності тут буде результат з MotionAnalyzer
     */
    private fun generateSimulatedAnalysisResult(): AnalysisResult {
        val random = java.util.Random()
        val score = 60f + random.nextFloat() * 40f // 60-100%
        
        val wristAngle = exerciseParameters.idealWristAngle + 
            (random.nextFloat() - 0.5f) * 30f
        val bodyRotation = exerciseParameters.minBodyRotation + 
            (random.nextFloat() - 0.5f) * 20f
        val followThrough = exerciseParameters.followThroughAngle + 
            (random.nextFloat() - 0.5f) * 30f
        
        val isWristValid = exerciseParameters.isWristAngleValid(wristAngle)
        val isRotationValid = exerciseParameters.isBodyRotationValid(bodyRotation)
        val isFollowThroughValid = exerciseParameters.isFollowThroughValid(followThrough)
        
        val errors = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        
        if (!isWristValid) {
            errors.add(getString(R.string.error_wrist_bent))
            recommendations.add(getString(R.string.recommendation_wrist))
        }
        if (!isRotationValid) {
            errors.add(getString(R.string.error_low_rotation))
            recommendations.add(getString(R.string.recommendation_rotation))
        }
        if (!isFollowThroughValid) {
            errors.add(getString(R.string.error_insufficient_follow))
            recommendations.add(getString(R.string.recommendation_follow))
        }
        
        return AnalysisResult(
            wristAngle = wristAngle,
            bodyRotation = bodyRotation,
            followThroughAngle = followThrough,
            isWristAngleValid = isWristValid,
            isBodyRotationValid = isRotationValid,
            isFollowThroughValid = isFollowThroughValid,
            overallScore = score,
            errors = errors,
            recommendations = recommendations
        )
    }

    private fun showSummary() {
        val totalStrokes = analysisResults.size
        val successfulStrokes = analysisResults.count { it.isSuccessful() }
        val averageScore = if (totalStrokes > 0) {
            analysisResults.map { it.overallScore }.average().toFloat()
        } else {
            0f
        }
        
        val summaryText = feedbackGenerator.generateSessionSummary(
            totalStrokes = totalStrokes,
            successfulStrokes = successfulStrokes,
            averageScore = averageScore
        )
        
        // Знайти найчастішу помилку
        val mostCommonError = analysisResults
            .flatMap { it.errors }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        
        val improvementTip = feedbackGenerator.generateImprovementTip(
            mostCommonError,
            exerciseId ?: "forehand_drive"
        )
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Підсумок тренування")
            .setMessage("$summaryText\n\n$improvementTip")
            .setPositiveButton("Завершити") { _, _ ->
                finish()
            }
            .setNegativeButton("Продовжити", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (isTrainingActive) {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(R.string.finish_training_title)
                        .setMessage(R.string.finish_training_message)
                        .setPositiveButton(R.string.dialog_yes) { _, _ -> finish() }
                        .setNegativeButton(R.string.dialog_no, null)
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
        // Очищення ресурсів (камера, MediaPipe тощо)
        if (::backgroundExecutor.isInitialized) {
            backgroundExecutor.shutdown()
        }
        poseLandmarkerHelper?.clearPoseLandmarker()
    }
    
    // Video processing with pose detection
    private fun processVideoWithPoseDetection(uri: Uri) {
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = this,
                runningMode = RunningMode.VIDEO,
                minPoseDetectionConfidence = 0.5f,
                minPoseTrackingConfidence = 0.5f,
                minPosePresenceConfidence = 0.5f,
                currentDelegate = 0 // CPU
            )
            
            runOnUiThread {
                binding.feedbackText.text = "🎬 Processing video with pose detection..."
            }
            
            poseLandmarkerHelper?.detectVideoFile(uri, VIDEO_INTERVAL_MS)
                ?.let { resultBundle ->
                    runOnUiThread { 
                        displayVideoResult(resultBundle)
                    }
                }
                ?: run { 
                    Log.e(TAG, "Error running pose landmarker on video")
                    runOnUiThread {
                        binding.feedbackText.text = "❌ Failed to detect poses in video"
                    }
                }
        }
    }
    
    private fun displayVideoResult(result: PoseLandmarkerHelper.ResultBundle) {
        binding.videoView.visibility = View.VISIBLE
        binding.feedbackText.text = "✅ Video processed - ${result.results.size} frames with poses"
        
        binding.videoView.start()
        val videoStartTimeMs = SystemClock.uptimeMillis()
        
        backgroundExecutor.scheduleAtFixedRate(
            {
                runOnUiThread {
                    val videoElapsedTimeMs = SystemClock.uptimeMillis() - videoStartTimeMs
                    val resultIndex = videoElapsedTimeMs.div(VIDEO_INTERVAL_MS).toInt()
                    
                    if (resultIndex >= result.results.size || binding.videoView.visibility == View.GONE) {
                        // Video playback finished, loop it
                        if (binding.videoView.isPlaying) {
                            binding.videoView.seekTo(0)
                        }
                    } else {
                        binding.overlay.setResults(
                            result.results[resultIndex],
                            result.inputImageHeight,
                            result.inputImageWidth,
                            RunningMode.VIDEO
                        )
                    }
                }
            },
            0,
            VIDEO_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }
    
    // PoseLandmarkerHelper.LandmarkerListener implementation
    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            binding.feedbackText.text = "❌ Pose detection error: $error"
            Log.e(TAG, "Pose landmarker error: $error (code: $errorCode)")
        }
    }
    
    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        // This is called for LIVE_STREAM mode (camera), not used for video
    }
}
