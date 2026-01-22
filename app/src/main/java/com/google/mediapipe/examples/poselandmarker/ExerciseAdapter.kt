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
    private var exercises: List<Exercise>,
    private val onExerciseClick: (Exercise) -> Unit
) : RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder>() {

    fun updateList(newExercises: List<Exercise>) {
        exercises = newExercises
        notifyDataSetChanged()
    }

    inner class ExerciseViewHolder(
        private val binding: ItemExerciseBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(exercise: Exercise) {
            binding.apply {
                tvExerciseName.text = exercise.name
                tvExerciseDescription.text = exercise.description
                tvDifficulty.text = exercise.difficulty
                tvDuration.text = exercise.duration
                tvCategory.text = exercise.category

                // Set icon background based on exercise ID/category
                val iconBackground = when (exercise.id) {
                    "forehand_drive" -> R.drawable.bg_icon_container_blue
                    "backhand_drive" -> R.drawable.bg_icon_container_green
                    "forehand_topspin" -> R.drawable.bg_icon_container_purple
                    "service" -> R.drawable.bg_icon_container_orange
                    else -> R.drawable.bg_icon_container_blue
                }
                flIconContainer.setBackgroundResource(iconBackground)

                // Set difficulty badge background based on difficulty level
                val (badgeBackground, badgeColor) = when {
                    exercise.difficulty.contains("Beginner", ignoreCase = true) || 
                    exercise.difficulty.contains("Початковий", ignoreCase = true) -> 
                        Pair(R.drawable.bg_badge_bordered_green, R.color.green_500)
                    exercise.difficulty.contains("Intermediate", ignoreCase = true) || 
                    exercise.difficulty.contains("Середній", ignoreCase = true) -> 
                        Pair(R.drawable.bg_badge_bordered_orange, R.color.orange_500)
                    else -> Pair(R.drawable.bg_badge_bordered_green, R.color.green_500)
                }
                tvDifficulty.setBackgroundResource(badgeBackground)
                tvDifficulty.setTextColor(root.context.getColor(badgeColor))

                // Lock status display (using alpha and clickability)
                if (exercise.isLocked) {
                    root.alpha = 0.5f
                } else {
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
