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
                tvDifficulty.text = root.context.getString(R.string.difficulty_label, exercise.difficulty)
                tvDuration.text = root.context.getString(R.string.duration_label, exercise.duration)

                // Checkbox for debug video mode
                cbUseVideo.isChecked = exercise.useVideo
                cbUseVideo.setOnCheckedChangeListener { _, isChecked ->
                    exercise.useVideo = isChecked
                }

                // Lock status display
                if (exercise.isLocked) {
                    tvLockStatus.text = root.context.getString(R.string.lock_icon)
                    tvLockStatus.visibility = android.view.View.VISIBLE
                    root.alpha = 0.6f
                    ivExerciseIcon.alpha = 0.5f
                    cbUseVideo.isEnabled = false
                } else {
                    tvLockStatus.visibility = android.view.View.GONE
                    root.alpha = 1.0f
                    ivExerciseIcon.alpha = 1.0f
                    cbUseVideo.isEnabled = true
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
