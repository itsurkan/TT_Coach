package com.google.mediapipe.examples.poselandmarker.managers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.poselandmarker.databinding.ItemFeedbackLogBinding

/**
 * Data model for a single entry in the feedback log.
 */
data class FeedbackLogEntry(
    val label: String,
    val message: String,
    val color: Int
)

/**
 * Adapter for displaying feedback entries in a RecyclerView.
 */
class FeedbackLogAdapter : RecyclerView.Adapter<FeedbackLogAdapter.ViewHolder>() {
    private var items = emptyList<FeedbackLogEntry>()

    /**
     * Update the list of entries and notify the adapter.
     */
    fun updateList(newList: List<FeedbackLogEntry>) {
        items = newList.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFeedbackLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(private val binding: ItemFeedbackLogBinding) : RecyclerView.ViewHolder(binding.root) {
        /**
         * Bind the model data to the UI components.
         */
        fun bind(entry: FeedbackLogEntry) {
            binding.tvLabel.text = entry.label
            binding.tvLabel.setTextColor(entry.color)
            binding.tvMessage.text = entry.message
        }
    }
}
