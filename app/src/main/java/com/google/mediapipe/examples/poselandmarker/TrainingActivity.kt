/*
 * AI Coach for Table Tennis
 * Training Screen with Camera and Analysis
 */

package com.google.mediapipe.examples.poselandmarker

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityTrainingBinding
import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import com.google.mediapipe.examples.poselandmarker.models.ExerciseParameters
import com.google.mediapipe.examples.poselandmarker.services.FeedbackGenerator
import com.google.mediapipe.examples.poselandmarker.services.MotionAnalyzer

class TrainingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTrainingBinding
    private var exerciseId: String? = null
    private var exerciseName: String? = null
    private var isTrainingActive = false
    private val feedbackHistory = mutableListOf<String>()
    
    // Аналітика та параметри
    private lateinit var exerciseParameters: ExerciseParameters
    private lateinit var motionAnalyzer: MotionAnalyzer
    private lateinit var feedbackGenerator: FeedbackGenerator
    private val analysisResults = mutableListOf<AnalysisResult>()
    private var consecutiveGoodStrokes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Отримання даних вправи
        exerciseId = intent.getStringExtra("EXERCISE_ID")
        exerciseName = intent.getStringExtra("EXERCISE_NAME")

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
        // Налаштування Action Bar
        supportActionBar?.apply {
            title = exerciseName ?: "Тренування"
            setDisplayHomeAsUpEnabled(true)
        }

        // Автоматичний старт камери
        startCameraPreview()

        // Кнопка калібрування
        binding.btnCalibrate.setOnClickListener {
            startCalibration()
        }

        // Кнопка старт/стоп
        binding.btnStartStop.setOnClickListener {
            if (isTrainingActive) {
                stopTraining()
            } else {
                startTraining()
            }
        }

        // Початковий стан
        binding.btnStartStop.isEnabled = false
        updateFeedbackText("Натисніть 'Калібрувати' для початку")
    }

    private fun startCameraPreview() {
        // Запуск CameraFragment у контейнері
        val cameraFragment = com.google.mediapipe.examples.poselandmarker.fragment.CameraFragment()
        supportFragmentManager.beginTransaction()
            .replace(binding.cameraPreviewContainer.id, cameraFragment)
            .commit()
        
        binding.feedbackText.text = "📹 Камера запущена. Натисніть 'Калібрувати'"
    }

    private fun startCalibration() {
        binding.tvCalibrationStatus.visibility = View.VISIBLE
        binding.tvCalibrationStatus.text = "📍 Станьте в стартову позицію для накату справа\n\n• Ноги на ширині плечей\n• Трохи присядьте\n• Ракетка перед собою"
        binding.btnCalibrate.isEnabled = false
        updateFeedbackText("Калібрування... Утримуйте позицію")

        // Симуляція калібрування (3 сек)
        binding.root.postDelayed({
            binding.tvCalibrationStatus.text = "✅ Калібрування завершено!"
            binding.root.postDelayed({
                binding.tvCalibrationStatus.visibility = View.GONE
                binding.btnStartStop.isEnabled = true
                binding.btnCalibrate.isEnabled = true
                updateFeedbackText("Готово! Натисніть 'Почати' для тренування")
                android.widget.Toast.makeText(this, "Готово до тренування!", android.widget.Toast.LENGTH_SHORT).show()
            }, 1000)
        }, 3000)
    }

    private fun startTraining() {
        isTrainingActive = true
        binding.btnStartStop.text = "⏸ Зупинити"
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
        updateFeedbackText("Тренування призупинено")
        
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
        
        binding.tvStrokeCount.text = "Ударів: $totalStrokes"
        binding.tvAverageScore.text = "Точність: $accuracy%"
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
            errors.add("Зап'ястя зігнуте")
            recommendations.add("Тримайте зап'ястя рівно")
        }
        if (!isRotationValid) {
            errors.add("Недостатня ротація корпусу")
            recommendations.add("Більше ротації для потужності")
        }
        if (!isFollowThroughValid) {
            errors.add("Недостатнє проведення")
            recommendations.add("Доведіть рух до кінця")
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
                        .setTitle("Завершити тренування?")
                        .setMessage("Ви впевнені, що хочете завершити поточне тренування?")
                        .setPositiveButton("Так") { _, _ -> finish() }
                        .setNegativeButton("Ні", null)
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
    }
}
