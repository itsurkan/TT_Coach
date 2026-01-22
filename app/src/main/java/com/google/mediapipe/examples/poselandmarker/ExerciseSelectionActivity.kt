/*
 * AI Coach for Table Tennis
 * Exercise Selection Screen
 */

package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityExerciseSelectionBinding

class ExerciseSelectionActivity : BaseActivity() {
    private lateinit var binding: ActivityExerciseSelectionBinding
    
    private val exercises by lazy {
        listOf(
            Exercise(
                id = "forehand_drive",
                name = getString(R.string.exercise_forehand_name),
                description = getString(R.string.exercise_forehand_desc),
                difficulty = getString(R.string.difficulty_beginner),
                duration = getString(R.string.duration_10_15)
            ),
            Exercise(
                id = "backhand_drive",
                name = getString(R.string.exercise_backhand_name),
                description = getString(R.string.exercise_backhand_desc),
                difficulty = getString(R.string.difficulty_beginner),
                duration = getString(R.string.duration_10_15),
                isLocked = true
            ),
            Exercise(
                id = "forehand_topspin",
                name = getString(R.string.exercise_topspin_name),
                description = getString(R.string.exercise_topspin_desc),
                difficulty = getString(R.string.difficulty_intermediate),
                duration = getString(R.string.duration_10_15),
                isLocked = true
            ),
            Exercise(
                id = "service",
                name = getString(R.string.exercise_service_name),
                description = getString(R.string.exercise_service_desc),
                difficulty = getString(R.string.difficulty_intermediate),
                duration = getString(R.string.duration_10_15),
                isLocked = true
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // Setup Action Bar
        supportActionBar?.apply {
            title = getString(R.string.exercise_selection_title)
            setDisplayHomeAsUpEnabled(true)
        }

        // Setup RecyclerView
        binding.exerciseRecyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = ExerciseAdapter(exercises) { exercise ->
            onExerciseSelected(exercise)
        }
        binding.exerciseRecyclerView.adapter = adapter

        // Текст підказки
        binding.tvHint.text = getString(R.string.select_exercise_hint)
    }

    private fun onExerciseSelected(exercise: Exercise) {
        if (exercise.isLocked) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.exercise_locked_title)
                .setMessage(R.string.exercise_locked_message)
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
            return
        }

        // Перехід до екрану тренування
        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra("EXERCISE_ID", exercise.id)
            putExtra("EXERCISE_NAME", exercise.name)
            putExtra("USE_VIDEO", exercise.useVideo)
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
    val category: String = "Technique",
    val isLocked: Boolean = false,
    var useVideo: Boolean = false
)
