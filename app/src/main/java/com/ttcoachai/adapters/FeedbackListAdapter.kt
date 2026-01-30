package com.ttcoachai.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.recyclerview.widget.RecyclerView
import com.ttcoachai.R
import com.ttcoachai.databinding.ItemFeedbackBinding
import com.ttcoachai.models.FeedbackItem

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
                com.ttcoachai.models.CorrectionType.WRIST,
                com.ttcoachai.models.CorrectionType.BODY_ROTATION,
                com.ttcoachai.models.CorrectionType.FOLLOW_THROUGH,
                com.ttcoachai.models.CorrectionType.CONTACT_HEIGHT,
                com.ttcoachai.models.CorrectionType.ELBOW_POSITION,
                com.ttcoachai.models.CorrectionType.STROKE_SPEED -> {
                    if (item.isPositive) {
                        android.R.color.holo_green_dark
                    } else {
                        android.R.color.holo_orange_dark
                    }
                }
                com.ttcoachai.models.CorrectionType.GENERAL -> {
                    android.R.color.holo_blue_dark
                }
            }
            binding.ivFeedbackIcon.setBackgroundColor(
                binding.root.context.getColor(color)
            )

            // Set details (use message as details for now since FeedbackItem doesn't have details field)
            binding.tvFeedbackDetails.text = binding.root.context.getString(R.string.format_feedback_details, item.type.name, if (item.isPositive) binding.root.context.getString(R.string.feedback_good_simple) else binding.root.context.getString(R.string.feedback_needs_improvement))

            // Set expanded state
            binding.layoutFeedbackDetails.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.ivExpandIcon.rotation = if (isExpanded) 180f else 0f

            // Handle Pose Visualizer
            if (isExpanded) {
                // Use real captured landmarks if available, fall back to mock
                val frames = if (item.strokeLandmarks.isNotEmpty()) {
                    item.strokeLandmarks
                } else {
                    MockPoseGenerator.generateMockStroke(item.type)
                }
                binding.poseVisualizer.setFrames(frames)
                binding.poseVisualizer.playAnimation()
            } else {
                binding.poseVisualizer.stopAnimation()
            }

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

object MockPoseGenerator {
    fun generateMockStroke(type: com.ttcoachai.models.CorrectionType): List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>> {
        val frames = mutableListOf<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>()
        val numFrames = 30
        
        // Base pose (standing)
        // We need 33 landmarks. We'll just mock the main ones and leave others at 0,0
        // Landmarks: 11/12 shoulders, 13/14 elbows, 15/16 wrists
        
        for (i in 0 until numFrames) {
            val t = i.toFloat() / numFrames
            // Simple swing animation for right arm (12, 14, 16)
            
            val landmarks = ArrayList<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>()
            for (j in 0 until 33) {
                var x = 0.5f
                var y = 0.5f
                
                when (j) {
                    11 -> { x = 0.4f; y = 0.3f } // Left Shoulder
                    12 -> { x = 0.6f; y = 0.3f } // Right Shoulder
                    13 -> { x = 0.3f; y = 0.5f } // Left Elbow
                    14 -> { // Right Elbow - moves
                        x = 0.65f + 0.1f * kotlin.math.sin(t * Math.PI).toFloat()
                        y = 0.5f
                    } 
                    15 -> { x = 0.3f; y = 0.7f } // Left Wrist
                    16 -> { // Right Wrist - moves more
                        x = 0.7f + 0.2f * kotlin.math.sin(t * Math.PI).toFloat()
                        y = 0.7f - 0.2f * kotlin.math.sin(t * Math.PI).toFloat()
                    } 
                    23 -> { x = 0.45f; y = 0.7f } // Left Hip
                    24 -> { x = 0.55f; y = 0.7f } // Right Hip
                }
                
                landmarks.add(com.google.mediapipe.tasks.components.containers.NormalizedLandmark.create(x, y, 0f))
            }
            frames.add(landmarks)
        }
        return frames
    }
}
