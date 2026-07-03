/*
 * AI Coach for Table Tennis
 * RecyclerView Adapter for Exercise List
 */

package com.ttcoachai

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ttcoachai.databinding.ItemExerciseBinding
import com.ttcoachai.databinding.ItemLockedToggleBinding
import com.ttcoachai.fragment.DrillActions
import com.ttcoachai.ui.SwipeRevealLayout
import com.ttcoachai.ui.SwipeState

class ExerciseAdapter(
    private var exercises: List<Exercise>,
    private val onExerciseClick: (Exercise) -> Unit,
    private val onExerciseLongClick: ((Exercise) -> Unit)? = null,
    private val onCloneClick: (Exercise) -> Unit = {},
    private val onDeleteClick: (Exercise) -> Unit = {},
    private val onToggleLocked: () -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** The single row currently open, if any — enforces one-open-at-a-time. */
    private var openRow: SwipeRevealLayout? = null

    /** When there are hidden locked drills, a footer toggle row is appended. */
    private var hasLocked = false
    private var lockedExpanded = false

    /**
     * Replaces the visible drill list. [hasLocked] controls whether the expand/collapse
     * footer is shown; [expanded] drives its label + chevron direction.
     */
    fun setData(newExercises: List<Exercise>, hasLocked: Boolean, expanded: Boolean) {
        exercises = newExercises
        this.hasLocked = hasLocked
        this.lockedExpanded = expanded
        openRow = null
        notifyDataSetChanged()
    }

    /** Closes whichever row is open (used by the fragment's scroll listener). */
    fun closeOpenRow() {
        openRow?.close(animate = true)
    }

    override fun getItemCount(): Int = exercises.size + if (hasLocked) 1 else 0

    override fun getItemViewType(position: Int): Int =
        if (hasLocked && position == exercises.size) VIEW_TYPE_FOOTER else VIEW_TYPE_ITEM

    inner class ExerciseViewHolder(
        private val binding: ItemExerciseBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(exercise: Exercise) {
            val layout = binding.root // SwipeRevealLayout

            // CRITICAL: reset the recycled row to CLOSED before rebinding, or a recycled view
            // shows a stale open state. Null the listener first so the reset's state callback
            // does not disturb the coordinator, then wire the fresh listener.
            layout.onStateChanged = null
            layout.close(animate = false)
            layout.setDeleteEnabled(DrillActions.canDelete(exercise))
            layout.onStateChanged = { newState ->
                if (newState != SwipeState.CLOSED) {
                    if (openRow != null && openRow !== layout) openRow?.close(animate = true)
                    openRow = layout
                } else if (openRow === layout) {
                    openRow = null
                }
            }

            binding.apply {
                tvExerciseName.text = exercise.name
                tvExerciseDescription.text = exercise.description
                tvDuration.text = exercise.duration
                tvCategory.text = exercise.category

                // Single consistent gold icon treatment for every drill (design system).
                // Icon glyph still varies per drill; ring/tile + tint do not.
                val iconRes = when (exercise.id) {
                    "forehand_drive", "forehand_andrii" -> R.drawable.ic_skill_forehand
                    "backhand_loop" -> R.drawable.ic_skill_backhand
                    "serve_practice" -> R.drawable.ic_skill_topspin
                    "footwork_drill" -> R.drawable.ic_skill_footwork
                    "multiball_rally" -> R.drawable.ic_alert_circle
                    "consistency_challenge" -> R.drawable.ic_check_circle_2
                    else -> R.drawable.ic_target
                }

                ivExerciseIcon.setImageResource(iconRes)
                ivExerciseIcon.setColorFilter(root.context.getColor(R.color.ttc_gold_accent))
                flIconContainer.setBackgroundResource(R.drawable.bg_icon_tile_gold)
                flIconContainer.backgroundTintList = null

                // Lock status display (using alpha) — applied to the foreground card only.
                swipeForeground.alpha = if (exercise.isLocked) 0.5f else 1.0f

                // Action buttons: run the action, then close the row.
                swipeClonePanel.setOnClickListener {
                    onCloneClick(exercise)
                    layout.close(animate = true)
                }
                swipeDeletePanel.setOnClickListener {
                    onDeleteClick(exercise)
                    layout.close(animate = true)
                }

                // Foreground tap: select only when closed (the open case is consumed inside
                // SwipeRevealLayout, which closes the row). Long-press → options menu when closed.
                swipeForeground.setOnClickListener {
                    if (!layout.isOpen) onExerciseClick(exercise)
                }
                swipeForeground.setOnLongClickListener {
                    if (!layout.isOpen) {
                        onExerciseLongClick?.invoke(exercise)
                        onExerciseLongClick != null
                    } else false
                }
            }
        }
    }

    inner class LockedToggleViewHolder(
        private val binding: ItemLockedToggleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.btnToggleLocked.apply {
                setText(if (lockedExpanded) R.string.drills_hide_locked else R.string.drills_show_locked)
                setIconResource(if (lockedExpanded) R.drawable.icn_chevron_up else R.drawable.ic_chevron_down)
                setOnClickListener { onToggleLocked() }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_FOOTER) {
            LockedToggleViewHolder(ItemLockedToggleBinding.inflate(inflater, parent, false))
        } else {
            ExerciseViewHolder(ItemExerciseBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ExerciseViewHolder -> holder.bind(exercises[position])
            is LockedToggleViewHolder -> holder.bind()
        }
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_FOOTER = 1
    }
}
