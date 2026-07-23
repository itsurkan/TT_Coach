package com.ttcoachai.work

import org.junit.Assert.assertEquals
import org.junit.Test

class PoseCacheEvictionTest {

    private val oneDayMs = 24L * 60 * 60 * 1000
    private val now = 1_000_000_000_000L

    @Test
    fun staleFileIsEvictedEvenIfSmall() {
        val entries = listOf(
            PoseCacheEviction.Entry(path = "old.json.gz", lastModifiedMs = now - 8 * oneDayMs, sizeBytes = 1_000),
            PoseCacheEviction.Entry(path = "fresh.json.gz", lastModifiedMs = now - 1 * oneDayMs, sizeBytes = 1_000),
        )
        val toDelete = PoseCacheEviction.entriesToEvict(entries, now, maxAgeDays = 7, maxBytes = Long.MAX_VALUE)
        assertEquals(listOf("old.json.gz"), toDelete)
    }

    @Test
    fun freshFilesUnderCapAreKept() {
        val entries = listOf(
            PoseCacheEviction.Entry(path = "a.json.gz", lastModifiedMs = now, sizeBytes = 50L * 1024 * 1024),
            PoseCacheEviction.Entry(path = "b.json.gz", lastModifiedMs = now, sizeBytes = 50L * 1024 * 1024),
        )
        val toDelete = PoseCacheEviction.entriesToEvict(entries, now, maxAgeDays = 7, maxBytes = 200L * 1024 * 1024)
        assertEquals(emptyList<String>(), toDelete)
    }

    @Test
    fun overCapEvictsOldestFirstUntilUnderBudget() {
        val entries = listOf(
            PoseCacheEviction.Entry(path = "oldest.json.gz", lastModifiedMs = now - 3 * oneDayMs, sizeBytes = 80L * 1024 * 1024),
            PoseCacheEviction.Entry(path = "middle.json.gz", lastModifiedMs = now - 2 * oneDayMs, sizeBytes = 80L * 1024 * 1024),
            PoseCacheEviction.Entry(path = "newest.json.gz", lastModifiedMs = now - 1 * oneDayMs, sizeBytes = 80L * 1024 * 1024),
        )
        // total 240MB > 200MB cap; deleting "oldest" alone brings it to 160MB, under budget.
        val toDelete = PoseCacheEviction.entriesToEvict(entries, now, maxAgeDays = 7, maxBytes = 200L * 1024 * 1024)
        assertEquals(listOf("oldest.json.gz"), toDelete)
    }
}
