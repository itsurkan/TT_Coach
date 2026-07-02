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

    /**
     * Splits [all] into (mostRecent, programs). The recent drill is removed from
     * the programs list; original order is otherwise preserved. Returns null recent
     * and the full list when [recentId] is null or not present in [all].
     */
    fun partition(all: List<Exercise>, recentId: String?): Pair<Exercise?, List<Exercise>> {
        val recent = recentId?.let { id -> all.firstOrNull { it.id == id } }
        val programs = if (recent == null) all else all.filter { it.id != recent.id }
        return recent to programs
    }
}
