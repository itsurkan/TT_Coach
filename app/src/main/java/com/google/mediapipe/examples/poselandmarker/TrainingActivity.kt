/*
 * AI Coach for Table Tennis
 * Training Screen with Camera and Analysis
 */

package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityTrainingBinding

class TrainingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTrainingBinding
    private var exerciseId: String? = null
    private var exerciseName: String? = null
    private var isTrainingActive = false
    private val feedbackHistory = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Отримання даних вправи
        exerciseId = intent.getStringExtra("EXERCISE_ID")
        exerciseName = intent.getStringExtra("EXERCISE_NAME")

        setupUI()
    }

    private fun setupUI() {
        // Налаштування Action Bar
        supportActionBar?.apply {
            title = exerciseName ?: "Тренування"
            setDisplayHomeAsUpEnabled(true)
        }

        // Інформація про вправу
        binding.tvExerciseInfo.text = "Вправа: $exerciseName"
        binding.tvStatus.text = "Статус: Готовий до початку"

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

        // Кнопка для відкриття камери (інтеграція з існуючим CameraActivity)
        binding.btnOpenCamera.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java).apply {
                putExtra("EXERCISE_ID", exerciseId)
            }
            startActivity(intent)
        }

        // Початкова видимість
        binding.layoutFeedback.visibility = View.GONE
        binding.btnStartStop.isEnabled = false
    }

    private fun startCalibration() {
        binding.tvStatus.text = "Статус: Калібрування..."
        binding.tvCalibrationHint.visibility = View.VISIBLE
        binding.tvCalibrationHint.text = """
            📍 Станьте у стартову позицію:
            • Ноги на ширині плечей
            • Трохи присядьте
            • Ракетка перед собою
            • Дивіться на камеру
        """.trimIndent()

        // Симуляція калібрування (в реальності тут буде MediaPipe)
        binding.root.postDelayed({
            binding.tvStatus.text = "Статус: ✅ Калібровано"
            binding.tvCalibrationHint.visibility = View.GONE
            binding.btnStartStop.isEnabled = true
            showFeedback("Калібрування завершено! Готовий до тренування.", "success")
        }, 3000)
    }

    private fun startTraining() {
        isTrainingActive = true
        binding.btnStartStop.text = "⏸ Зупинити"
        binding.btnStartStop.setBackgroundColor(getColor(android.R.color.holo_red_light))
        binding.tvStatus.text = "Статус: 🏓 Тренування активне"
        binding.layoutFeedback.visibility = View.VISIBLE

        // Симуляція фідбеку (в реальності аналіз з MediaPipe)
        simulateFeedback()
    }

    private fun stopTraining() {
        isTrainingActive = false
        binding.btnStartStop.text = "▶ Почати тренування"
        binding.btnStartStop.setBackgroundColor(getColor(android.R.color.holo_green_light))
        binding.tvStatus.text = "Статус: Призупинено"
        
        showSummary()
    }

    private fun simulateFeedback() {
        if (!isTrainingActive) return

        val feedbacks = listOf(
            "✅ Чудово! Ідеальна техніка!",
            "⚠️ Більше ротації корпусу!",
            "⚠️ Тримай зап'ястя рівно!",
            "✅ Гарне проведення руху!",
            "⚠️ Контакт надто високо - опусти точку удару"
        )

        val randomFeedback = feedbacks.random()
        val feedbackType = if (randomFeedback.startsWith("✅")) "success" else "warning"
        
        showFeedback(randomFeedback, feedbackType)
        feedbackHistory.add(randomFeedback)
        
        updateFeedbackHistory()

        // Наступний фідбек через 3-5 секунд
        binding.root.postDelayed({
            simulateFeedback()
        }, (3000..5000).random().toLong())
    }

    private fun showFeedback(message: String, type: String) {
        binding.tvCurrentFeedback.text = message
        binding.tvCurrentFeedback.setTextColor(
            when (type) {
                "success" -> getColor(android.R.color.holo_green_dark)
                "warning" -> getColor(android.R.color.holo_orange_dark)
                else -> getColor(android.R.color.black)
            }
        )

        // TTS буде тут (flutter_tts або Android TextToSpeech)
        // speakFeedback(message)
    }

    private fun updateFeedbackHistory() {
        val lastFeedbacks = feedbackHistory.takeLast(5).reversed()
        binding.tvFeedbackHistory.text = "Історія фідбеку:\n" + 
            lastFeedbacks.joinToString("\n") { "• $it" }
    }

    private fun showSummary() {
        val totalStrokes = feedbackHistory.size
        val successCount = feedbackHistory.count { it.startsWith("✅") }
        val accuracy = if (totalStrokes > 0) (successCount * 100 / totalStrokes) else 0

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Підсумок тренування")
            .setMessage("""
                Всього ударів: $totalStrokes
                Успішних: $successCount
                Точність: $accuracy%
                
                Продовжити тренування?
            """.trimIndent())
            .setPositiveButton("Продовжити") { _, _ ->
                startTraining()
            }
            .setNegativeButton("Завершити") { _, _ ->
                finish()
            }
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
