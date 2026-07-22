package com.ttcoachai.util

import com.ttcoachai.models.CommunityDrill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityDrillSortTest {

    private fun drill(
        name: String,
        creatorName: String = "creator",
        sharedAtMs: Long = 0L,
        ratingSum: Long = 0L,
        ratingCount: Long = 0L,
    ) = CommunityDrill(
        name = name,
        baseTemplate = "forehand_drive",
        focusCsv = "",
        referenceType = "baseline",
        strictnessX = 1f,
        perPhaseTargetsJson = "{}",
        creatorUid = "uid",
        creatorName = creatorName,
        creatorPhotoUrl = "",
        sharedAtMs = sharedAtMs,
        ratingSum = ratingSum,
        ratingCount = ratingCount,
    )

    @Test fun sort_by_rating_orders_higher_average_first() {
        val low = drill(name = "Low", ratingSum = 6, ratingCount = 2) // avg 3.0
        val high = drill(name = "High", ratingSum = 20, ratingCount = 5) // avg 4.0
        val result = CommunityDrillSort.sort(listOf(low, high), CommunitySortMode.RATING)
        assertEquals(listOf(high, low), result)
    }

    @Test fun sort_by_rating_ties_break_on_higher_ratingCount_then_name() {
        // A avg 4.0 count 2, B avg 4.0 count 10 -> B before A
        val a = drill(name = "A", ratingSum = 8, ratingCount = 2)
        val b = drill(name = "B", ratingSum = 40, ratingCount = 10)
        val result = CommunityDrillSort.sort(listOf(a, b), CommunitySortMode.RATING)
        assertEquals(listOf(b, a), result)
    }

    @Test fun sort_by_rating_final_tiebreak_is_name_case_insensitive() {
        val zeta = drill(name = "zeta", ratingSum = 8, ratingCount = 2)
        val alpha = drill(name = "Alpha", ratingSum = 8, ratingCount = 2)
        val result = CommunityDrillSort.sort(listOf(zeta, alpha), CommunitySortMode.RATING)
        assertEquals(listOf(alpha, zeta), result)
    }

    @Test fun sort_by_newest_orders_larger_sharedAtMs_first() {
        val older = drill(name = "Older", sharedAtMs = 100L)
        val newer = drill(name = "Newer", sharedAtMs = 200L)
        val result = CommunityDrillSort.sort(listOf(older, newer), CommunitySortMode.NEWEST)
        assertEquals(listOf(newer, older), result)
    }

    @Test fun sort_by_creator_is_case_insensitive_and_input_order_irrelevant() {
        val bob = drill(name = "Drill B", creatorName = "Bob")
        val alice = drill(name = "Drill A", creatorName = "alice")
        val result = CommunityDrillSort.sort(listOf(bob, alice), CommunitySortMode.CREATOR)
        assertEquals(listOf(alice, bob), result)
    }

    @Test fun sort_by_creator_ties_break_on_name_case_insensitive() {
        val z = drill(name = "zeta", creatorName = "same")
        val a = drill(name = "Alpha", creatorName = "Same")
        val result = CommunityDrillSort.sort(listOf(z, a), CommunitySortMode.CREATOR)
        assertEquals(listOf(a, z), result)
    }

    @Test fun sort_does_not_mutate_input_list() {
        val first = drill(name = "First", sharedAtMs = 100L)
        val second = drill(name = "Second", sharedAtMs = 200L)
        val input = listOf(first, second)
        CommunityDrillSort.sort(input, CommunitySortMode.NEWEST)
        assertEquals(listOf(first, second), input)
    }

    @Test fun search_blank_query_returns_all_in_input_order() {
        val a = drill(name = "Forehand Drive")
        val b = drill(name = "Backhand Push")
        val result = CommunityDrillSort.search(listOf(a, b), "   ")
        assertEquals(listOf(a, b), result)
    }

    @Test fun search_matches_substring_case_insensitively() {
        val a = drill(name = "Forehand Drive")
        val b = drill(name = "Backhand Push")
        val result = CommunityDrillSort.search(listOf(a, b), "fore")
        assertEquals(listOf(a), result)
    }

    @Test fun search_no_match_returns_empty() {
        val a = drill(name = "Forehand Drive")
        val result = CommunityDrillSort.search(listOf(a), "smash")
        assertTrue(result.isEmpty())
    }

    @Test fun search_preserves_input_order() {
        val a = drill(name = "Alpha Drive")
        val b = drill(name = "Beta Drive")
        val result = CommunityDrillSort.search(listOf(b, a), "drive")
        assertEquals(listOf(b, a), result)
    }
}
