package com.ttcoachai.util

import com.ttcoachai.models.CommunityDrill

enum class CommunitySortMode { RATING, NEWEST, CREATOR }

/**
 * Pure client-side sort/search helpers for community drill lists.
 * No Android/Firebase dependency — safe to unit test on the JVM.
 */
object CommunityDrillSort {

    fun sort(drills: List<CommunityDrill>, mode: CommunitySortMode): List<CommunityDrill> {
        val comparator = when (mode) {
            CommunitySortMode.RATING -> compareByDescending<CommunityDrill> { it.averageRating }
                .thenByDescending { it.ratingCount }
                .thenBy { it.name.lowercase() }
            CommunitySortMode.NEWEST -> compareByDescending { it.sharedAtMs }
            CommunitySortMode.CREATOR -> compareBy<CommunityDrill> { it.creatorName.lowercase() }
                .thenBy { it.name.lowercase() }
        }
        return drills.sortedWith(comparator)
    }

    fun search(drills: List<CommunityDrill>, query: String): List<CommunityDrill> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return drills
        return drills.filter { it.name.contains(trimmed, ignoreCase = true) }
    }
}
