/*
 * AI Coach for Table Tennis
 * RecyclerView Adapter for Exercise List
 */

package com.ttcoachai

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ttcoachai.databinding.ItemExerciseBinding

class ExerciseAdapter(
    private var exercises: List<Exercise>,
    private val onExerciseClick: (Exercise) -> Unit,
    private val onExerciseLongClick: ((Exercise) -> Unit)? = null
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

                // Set icon, tint and background based on exercise ID
                val (iconRes, iconTint, iconBackground) = when (exercise.id) {
                    "forehand_drive" -> Triple(R.drawable.ic_target, R.color.blue_500, R.drawable.bg_icon_container_blue)
                    "forehand_andrii" -> Triple(R.drawable.ic_target, R.color.purple_500, R.drawable.bg_icon_container_purple)
                    "backhand_loop" -> Triple(R.drawable.ic_trending_up, R.color.green_500, R.drawable.bg_icon_container_green)
                    "serve_practice" -> Triple(R.drawable.ic_target, R.color.purple_500, R.drawable.bg_icon_container_purple)
                    "footwork_drill" -> Triple(R.drawable.ic_person, R.color.orange_500, R.drawable.bg_icon_container_orange)
                    "multiball_rally" -> Triple(R.drawable.ic_alert_circle, R.color.red_500, R.drawable.bg_icon_container_red)
                    "consistency_challenge" -> Triple(R.drawable.ic_check_circle_2, R.color.blue_500, R.drawable.bg_icon_container_blue)
                    else -> Triple(R.drawable.ic_target, R.color.blue_500, R.drawable.bg_icon_container_blue)
                }
                
                ivExerciseIcon.setImageResource(iconRes)
                ivExerciseIcon.setColorFilter(root.context.getColor(iconTint))
                flIconContainer.setBackgroundResource(iconBackground)

                // Set difficulty badge background based on difficulty level
                val (badgeBackground, badgeColor) = when {
                    exercise.difficulty.contains("Beginner", ignoreCase = true) || 
                    exercise.difficulty.contains("Початковий", ignoreCase = true) -> 
                        Pair(R.drawable.bg_badge_filled_green, R.color.badge_text_green)
                    exercise.difficulty.contains("Intermediate", ignoreCase = true) || 
                    exercise.difficulty.contains("Середній", ignoreCase = true) -> 
                        Pair(R.drawable.bg_badge_filled_orange, R.color.badge_text_orange)
                    exercise.difficulty.contains("Advanced", ignoreCase = true) || 
                    exercise.difficulty.contains("Просунутий", ignoreCase = true) -> 
                        Pair(R.drawable.bg_badge_filled_red, R.color.badge_text_red)
                    exercise.difficulty.contains("All Level", ignoreCase = true) || 
                    exercise.difficulty.contains("Всі рівні", ignoreCase = true) -> 
                        Pair(R.drawable.bg_badge_filled_blue, R.color.badge_text_blue)
                    else -> Pair(R.drawable.bg_badge_filled_green, R.color.badge_text_green)
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
                root.setOnLongClickListener {
                    onExerciseLongClick?.invoke(exercise)
                    onExerciseLongClick != null
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
