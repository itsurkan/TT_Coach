/*
 * AI Coach for Table Tennis
 * Exercise Selection Screen
 */

package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityExerciseSelectionBinding

class ExerciseSelectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExerciseSelectionBinding
    private val exercises = listOf(
        Exercise(
            id = "forehand_drive",
            name = "Накат справа (Forehand Drive)",
            description = "Базовий удар справа з ротацією корпусу",
            difficulty = "Початковий",
            duration = "10-15 хв"
        ),
        Exercise(
            id = "backhand_drive",
            name = "Накат зліва (Backhand Drive)",
            description = "Базовий удар зліва",
            difficulty = "Початковий",
            duration = "10-15 хв",
            isLocked = true
        ),
        Exercise(
            id = "forehand_topspin",
            name = "Топ-спін справа",
            description = "Атакуючий удар з сильним обертанням",
            difficulty = "Середній",
            duration = "15-20 хв",
            isLocked = true
        ),
        Exercise(
            id = "service",
            name = "Подача",
            description = "Техніка подачі м'яча",
            difficulty = "Середній",
            duration = "10-15 хв",
            isLocked = true
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // Налаштування Action Bar
        supportActionBar?.apply {
            title = "Оберіть вправу"
            setDisplayHomeAsUpEnabled(true)
        }

        // Налаштування RecyclerView
        binding.exerciseRecyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = ExerciseAdapter(exercises) { exercise ->
            onExerciseSelected(exercise)
        }
        binding.exerciseRecyclerView.adapter = adapter

        // Текст підказки
        binding.tvHint.text = "Оберіть вправу для початку тренування"
    }

    private fun onExerciseSelected(exercise: Exercise) {
        if (exercise.isLocked) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Заблоковано")
                .setMessage("Ця вправа буде доступна у наступних версіях додатку.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // Перехід до екрану тренування
        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra("EXERCISE_ID", exercise.id)
            putExtra("EXERCISE_NAME", exercise.name)
        }
        startActivity(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

// Data class для вправи
data class Exercise(
    val id: String,
    val name: String,
    val description: String,
    val difficulty: String,
    val duration: String,
    val isLocked: Boolean = false
)
