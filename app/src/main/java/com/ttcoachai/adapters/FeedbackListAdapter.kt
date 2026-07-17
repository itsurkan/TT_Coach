package com.ttcoachai.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ttcoachai.R
import com.ttcoachai.databinding.ItemLiveFeedbackRowBinding
import com.ttcoachai.shared.models.CorrectionType

class FeedbackListAdapter(
    private val onRowClick: (CorrectionType, Int) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<FeedbackListAdapter.FeedbackViewHolder>() {

    private val feedbackCounts = mutableListOf<Pair<CorrectionType, Int>>()

    fun updateFeedback(newItems: List<Pair<CorrectionType, Int>>) {
        feedbackCounts.clear()
        feedbackCounts.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedbackViewHolder {
        val binding = ItemLiveFeedbackRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FeedbackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeedbackViewHolder, position: Int) {
        holder.bind(feedbackCounts[position])
    }

    override fun getItemCount(): Int = feedbackCounts.size

    inner class FeedbackViewHolder(
        private val binding: ItemLiveFeedbackRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Pair<CorrectionType, Int>) {
            val (type, count) = item
            val context = binding.root.context

            binding.tvLabel.setText(labelRes(type))
            binding.tvCount.text = "×$count"
            binding.badgePremium.visibility = View.GONE
            binding.dot.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, dotColorRes(count))
            )
            binding.root.setOnClickListener { onRowClick(type, count) }
        }
    }

    companion object {
        private fun dotColorRes(count: Int): Int = when {
            count >= 3 -> R.color.ttc_danger
            count == 2 -> R.color.ttc_amber
            else -> R.color.ttc_gold_bright
        }

        private fun labelRes(type: CorrectionType): Int = when (type) {
            CorrectionType.WRIST -> R.string.live_fb_wrist_angle
            CorrectionType.BODY_ROTATION -> R.string.live_fb_body_rotation
            CorrectionType.CONTACT_HEIGHT -> R.string.live_fb_contact_height
            CorrectionType.ELBOW_POSITION -> R.string.live_fb_elbow_position
            CorrectionType.STROKE_SPEED -> R.string.live_fb_stroke_speed
            CorrectionType.FOLLOW_THROUGH -> R.string.live_fb_follow_through
            CorrectionType.KNEE_BEND -> R.string.live_fb_knee_bend
            CorrectionType.GENERAL -> R.string.live_fb_general
        }
    }
}
