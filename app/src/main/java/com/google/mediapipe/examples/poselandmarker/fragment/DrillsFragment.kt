package com.google.mediapipe.examples.poselandmarker.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.mediapipe.examples.poselandmarker.Exercise
import com.google.mediapipe.examples.poselandmarker.ExerciseAdapter
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.TrainingActivity
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentDrillsBinding

class DrillsFragment : Fragment() {

    private var _binding: FragmentDrillsBinding? = null
    private val binding get() = _binding!!

    private lateinit var exercises: List<Exercise>
    private lateinit var adapter: ExerciseAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDrillsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupData()
        setupUI()
    }

    private fun setupData() {
        exercises = listOf(
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

    private fun setupUI() {
        // Setup RecyclerView
        binding.rvDrills.layoutManager = LinearLayoutManager(context)
        adapter = ExerciseAdapter(exercises) { exercise ->
            onExerciseSelected(exercise)
        }
        binding.rvDrills.adapter = adapter
        
        // Setup Featured Drill Button
        binding.btnStartFeatured.setOnClickListener {
            // Find forehand drive and start it
            val forehand = exercises.find { it.id == "forehand_drive" }
            if (forehand != null) {
                onExerciseSelected(forehand)
            }
        }
        
        // Setup Chips (Simple toast for now as logic is same)
        binding.chipAll.setOnClickListener { filterExercises("All") }
        binding.chipBeginner.setOnClickListener { filterExercises("Beginner") }
        binding.chipIntermediate.setOnClickListener { filterExercises("Intermediate") }
        binding.chipAdvanced.setOnClickListener { filterExercises("Advanced") }
    }
    
    private fun filterExercises(difficulty: String) {
        val filtered = if (difficulty == "All") {
            exercises
        } else {
            exercises.filter { it.difficulty.equals(difficulty, ignoreCase = true) }
        }
        adapter.updateList(filtered)
    }

    private fun onExerciseSelected(exercise: Exercise) {
        if (exercise.isLocked) {
           androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.exercise_locked_title)
                .setMessage(R.string.exercise_locked_message)
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
            return
        }

        // Navigate to TrainingActivity
        val intent = Intent(requireContext(), TrainingActivity::class.java).apply {
            putExtra("EXERCISE_ID", exercise.id)
            putExtra("EXERCISE_NAME", exercise.name)
            putExtra("USE_VIDEO", exercise.useVideo)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
