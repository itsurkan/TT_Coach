/*
 * AI Coach for Table Tennis
 * RecyclerView Adapter for Exercise List
 */

package com.google.mediapipe.examples.poselandmarker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.poselandmarker.databinding.ItemExerciseBinding

class ExerciseAdapter(
    private val exercises: List<Exercise>,
    private val onExerciseClick: (Exercise) -> Unit
) : RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder>() {

    inner class ExerciseViewHolder(
        private val binding: ItemExerciseBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(exercise: Exercise) {
            binding.apply {
                tvExerciseName.text = exercise.name
                tvExerciseDescription.text = exercise.description
                tvDifficulty.text = "Складність: ${exercise.difficulty}"
                tvDuration.text = "Тривалість: ${exercise.duration}"

                // Відображення статусу блокування
                if (exercise.isLocked) {
                    tvLockStatus.text = "🔒 Заблоковано"
                    tvLockStatus.visibility = android.view.View.VISIBLE
                    root.alpha = 0.6f
                } else {
                    tvLockStatus.visibility = android.view.View.GONE
                    root.alpha = 1.0f
                }

                root.setOnClickListener {
                    onExerciseClick(exercise)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val binding = ItemExerciseBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ExerciseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        holder.bind(exercises[position])
    }

    override fun getItemCount() = exercises.size
}
