package com.ttcoachai.fragment

import com.ttcoachai.Exercise

/**
 * Action-availability rule for the Exercises tab (this slice).
 * Clone/Continue apply to every drill; Edit/Rename/Delete only to user-created
 * custom drills. Built-in presets are immutable. Custom drills are identified by
 * the `custom_` id prefix (see DrillsFragment.CUSTOM_DRILL_PREFIX).
 */
object DrillActions {

    private const val CUSTOM_PREFIX = "custom_"

    fun isCustom(exercise: Exercise): Boolean = exercise.id.startsWith(CUSTOM_PREFIX)

    fun canClone(exercise: Exercise): Boolean = true
    fun canContinue(exercise: Exercise): Boolean = true
    fun canEdit(exercise: Exercise): Boolean = isCustom(exercise)
    fun canRename(exercise: Exercise): Boolean = isCustom(exercise)
    fun canDelete(exercise: Exercise): Boolean = isCustom(exercise)
    fun canShareToCommunity(exercise: Exercise, isShared: Boolean): Boolean = isCustom(exercise) && !isShared
    fun canUnshare(exercise: Exercise, isShared: Boolean): Boolean = isCustom(exercise) && isShared

    /**
     * Identifies the most-recent drill for the RECENT card without disturbing the
     * programs list — the recent drill STAYS in [all] so the list doesn't reflow
     * when the recent card loads. Returns null recent when [recentId] is null or
     * not present in [all]; programs is always [all] unchanged.
     */
    fun partition(all: List<Exercise>, recentId: String?): Pair<Exercise?, List<Exercise>> {
        val recent = recentId?.let { id -> all.firstOrNull { it.id == id } }
        return recent to all
    }
}
