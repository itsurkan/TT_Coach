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

                // Single consistent gold icon treatment for every drill (design system).
                // Icon glyph still varies per drill; ring/tile + tint do not.
                val iconRes = when (exercise.id) {
                    "backhand_loop" -> R.drawable.ic_trending_up
                    "footwork_drill" -> R.drawable.ic_person
                    "multiball_rally" -> R.drawable.ic_alert_circle
                    "consistency_challenge" -> R.drawable.ic_check_circle_2
                    else -> R.drawable.ic_target
                }

                ivExerciseIcon.setImageResource(iconRes)
                ivExerciseIcon.setColorFilter(root.context.getColor(R.color.ttc_gold_accent))
                flIconContainer.setBackgroundResource(R.drawable.bg_icon_tile_gold)
                flIconContainer.backgroundTintList = null

                // Difficulty is muted meta text (no colored pill).
                tvDifficulty.background = null
                tvDifficulty.setTextColor(root.context.getColor(R.color.ttc_text_2))

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
