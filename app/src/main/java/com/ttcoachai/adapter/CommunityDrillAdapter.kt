/*
 * AI Coach for Table Tennis
 * RecyclerView Adapter for the Community Drills browse list
 */

package com.ttcoachai.adapter

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.ttcoachai.R
import com.ttcoachai.databinding.ItemCommunityDrillBinding
import com.ttcoachai.models.CommunityDrill

/**
 * Renders the client-side sorted/filtered list of [CommunityDrill]s produced by
 * `CommunityDrillsActivity.applyFilter()`. Purely presentational — sort/search live in
 * `CommunityDrillSort` (Task 3).
 */
class CommunityDrillAdapter(
    private var items: List<CommunityDrill>,
    private val onClick: (CommunityDrill) -> Unit
) : RecyclerView.Adapter<CommunityDrillAdapter.CommunityDrillViewHolder>() {

    fun setData(list: List<CommunityDrill>) {
        items = list
        notifyDataSetChanged()
    }

    inner class CommunityDrillViewHolder(
        private val binding: ItemCommunityDrillBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(drill: CommunityDrill) {
            binding.apply {
                tvName.text = drill.name
                tvCreator.text = drill.creatorName
                tvRating.text = itemView.context.getString(
                    R.string.community_rating_format,
                    drill.averageRating,
                    drill.ratingCount
                )
                tvSharedAt.text = DateUtils.getRelativeTimeSpanString(
                    drill.sharedAtMs,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )

                if (drill.creatorPhotoUrl.isBlank()) {
                    ivCreatorAvatar.setImageResource(R.drawable.ic_person)
                } else {
                    ivCreatorAvatar.load(drill.creatorPhotoUrl) {
                        transformations(CircleCropTransformation())
                        error(R.drawable.ic_person)
                    }
                }

                root.setOnClickListener { onClick(drill) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommunityDrillViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return CommunityDrillViewHolder(ItemCommunityDrillBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: CommunityDrillViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
