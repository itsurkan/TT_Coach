package com.google.mediapipe.examples.poselandmarker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.ItemFeedbackBinding
import com.google.mediapipe.examples.poselandmarker.models.FeedbackItem

class FeedbackListAdapter : RecyclerView.Adapter<FeedbackListAdapter.FeedbackViewHolder>() {

    private val feedbackItems = mutableListOf<FeedbackItem>()
    private val expandedPositions = mutableSetOf<Int>()

    fun updateFeedback(newItems: List<FeedbackItem>) {
        feedbackItems.clear()
        feedbackItems.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedbackViewHolder {
        val binding = ItemFeedbackBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FeedbackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeedbackViewHolder, position: Int) {
        holder.bind(feedbackItems[position], position)
    }

    override fun getItemCount(): Int = feedbackItems.size

    inner class FeedbackViewHolder(
        private val binding: ItemFeedbackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FeedbackItem, position: Int) {
            val isExpanded = expandedPositions.contains(position)

            // Set message
            binding.tvFeedbackMessage.text = item.message

            // Set icon color based on type
            val color = when (item.type) {
                com.google.mediapipe.examples.poselandmarker.models.CorrectionType.WRIST,
                com.google.mediapipe.examples.poselandmarker.models.CorrectionType.BODY_ROTATION,
                com.google.mediapipe.examples.poselandmarker.models.CorrectionType.FOLLOW_THROUGH,
                com.google.mediapipe.examples.poselandmarker.models.CorrectionType.CONTACT_HEIGHT,
                com.google.mediapipe.examples.poselandmarker.models.CorrectionType.ELBOW_POSITION,
                com.google.mediapipe.examples.poselandmarker.models.CorrectionType.STROKE_SPEED -> {
                    if (item.isPositive) {
                        android.R.color.holo_green_dark
                    } else {
                        android.R.color.holo_orange_dark
                    }
                }
                com.google.mediapipe.examples.poselandmarker.models.CorrectionType.GENERAL -> {
                    android.R.color.holo_blue_dark
                }
            }
            binding.ivFeedbackIcon.setBackgroundColor(
                binding.root.context.getColor(color)
            )

            // Set details (use message as details for now since FeedbackItem doesn't have details field)
            binding.tvFeedbackDetails.text = "Type: ${item.type.name}\n${if (item.isPositive) "✓ Good" else "⚠ Needs improvement"}"

            // Set expanded state
            binding.layoutFeedbackDetails.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.ivExpandIcon.rotation = if (isExpanded) 180f else 0f

            // Click listener
            binding.layoutFeedbackHeader.setOnClickListener {
                val newPosition = adapterPosition
                if (newPosition != RecyclerView.NO_POSITION) {
                    if (expandedPositions.contains(newPosition)) {
                        expandedPositions.remove(newPosition)
                    } else {
                        expandedPositions.add(newPosition)
                    }
                    notifyItemChanged(newPosition)
                    animateExpandIcon(binding.ivExpandIcon, !isExpanded)
                }
            }
        }

        private fun animateExpandIcon(view: View, expand: Boolean) {
            val fromDegrees = if (expand) 0f else 180f
            val toDegrees = if (expand) 180f else 0f
            val rotate = RotateAnimation(
                fromDegrees, toDegrees,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            )
            rotate.duration = 200
            rotate.fillAfter = true
            view.startAnimation(rotate)
        }
    }
}
