package com.ttcoachai.util

import com.ttcoachai.managers.RepPoseCapture
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.Keypoint2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepCarouselDataSourceTest {

    private val pose = listOf(Keypoint2D(0.1f, 0.2f, 0.9f))

    private fun capture(
        atMs: Long,
        start: List<Keypoint2D> = pose,
        end: List<Keypoint2D> = pose,
        flaggedTypes: Set<CorrectionType> = emptySet(),
    ) = RepPoseCapture(atMs = atMs, start = start, end = end, flaggedTypes = flaggedTypes)

    @Test
    fun prefersPersistedCaptures_whenUsableOnesExist() {
        val persisted = listOf(capture(1L), capture(2L))
        val inMemory = listOf(capture(3L))
        val result = RepCarouselDataSource.resolve(persisted, inMemory, pose, pose, null)
        assertEquals(persisted, result)
    }

    @Test
    fun persistedCapturesAllUnusable_fallsBackToInMemory() {
        val persisted = listOf(capture(1L, start = emptyList()))
        val inMemory = listOf(capture(2L), capture(3L))
        val result = RepCarouselDataSource.resolve(persisted, inMemory, pose, pose, null)
        assertEquals(inMemory, result)
    }

    @Test
    fun noPersistedOrInMemory_fallsBackToLegacySinglePairAsOnePageCarousel() {
        val legacyStart = listOf(Keypoint2D(0.3f, 0.4f, 0.8f))
        val legacyEnd = listOf(Keypoint2D(0.5f, 0.6f, 0.7f))
        val result = RepCarouselDataSource.resolve(emptyList(), emptyList(), legacyStart, legacyEnd, null)
        assertEquals(1, result.size)
        assertEquals(legacyStart, result[0].start)
        assertEquals(legacyEnd, result[0].end)
        assertTrue(result[0].flaggedTypes.isEmpty())
    }

    @Test
    fun legacyFallback_marksTopFocusTypeAsFlagged_whenProvided() {
        val legacyStart = listOf(Keypoint2D(0.3f, 0.4f, 0.8f))
        val legacyEnd = listOf(Keypoint2D(0.5f, 0.6f, 0.7f))
        val result = RepCarouselDataSource.resolve(
            emptyList(), emptyList(), legacyStart, legacyEnd, CorrectionType.ELBOW_BEND
        )
        assertEquals(setOf(CorrectionType.ELBOW_BEND), result[0].flaggedTypes)
    }

    @Test
    fun legacyPairAlsoUnusable_returnsEmptyList() {
        val result = RepCarouselDataSource.resolve(emptyList(), emptyList(), emptyList(), emptyList(), null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun legacyPairPartiallyEmpty_returnsEmptyList() {
        val legacyStart = listOf(Keypoint2D(0.3f, 0.4f, 0.8f))
        val result = RepCarouselDataSource.resolve(emptyList(), emptyList(), legacyStart, emptyList(), null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun persistedCaptures_filtersOutUnusableEntriesButKeepsUsableOnes() {
        val persisted = listOf(
            capture(1L),
            capture(2L, end = emptyList()),
            capture(3L),
        )
        val result = RepCarouselDataSource.resolve(persisted, emptyList(), pose, pose, null)
        assertEquals(listOf(1L, 3L), result.map { it.atMs })
    }
}
