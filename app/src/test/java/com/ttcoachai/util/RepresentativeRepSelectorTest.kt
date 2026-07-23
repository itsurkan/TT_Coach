package com.ttcoachai.util

import com.ttcoachai.managers.RepPoseCapture
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.Keypoint2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepresentativeRepSelectorTest {

    private val pose = listOf(Keypoint2D(0.1f, 0.2f, 0.9f))

    private fun capture(
        atMs: Long,
        start: List<Keypoint2D> = pose,
        end: List<Keypoint2D> = pose,
        flaggedTypes: Set<CorrectionType> = emptySet(),
    ) = RepPoseCapture(atMs = atMs, start = start, end = end, flaggedTypes = flaggedTypes)

    @Test
    fun emptyCaptures_returnsNull() {
        assertNull(RepresentativeRepSelector.select(emptyList(), CorrectionType.ELBOW_BEND))
    }

    @Test
    fun noTopFocusType_returnsMostRecentUsableCapture() {
        val captures = listOf(capture(atMs = 1L), capture(atMs = 2L), capture(atMs = 3L))
        val selected = RepresentativeRepSelector.select(captures, null)
        assertEquals(3L, selected?.atMs)
    }

    @Test
    fun prefersMostRecentCaptureFlaggedWithTopFocusType() {
        val captures = listOf(
            capture(atMs = 1L, flaggedTypes = setOf(CorrectionType.ELBOW_BEND)),
            capture(atMs = 2L, flaggedTypes = emptySet()),
            capture(atMs = 3L, flaggedTypes = setOf(CorrectionType.ELBOW_BEND)),
            capture(atMs = 4L, flaggedTypes = setOf(CorrectionType.POSTURE)),
        )
        val selected = RepresentativeRepSelector.select(captures, CorrectionType.ELBOW_BEND)
        assertEquals(3L, selected?.atMs)
    }

    @Test
    fun fallsBackToMostRecentUsableCapture_whenNoCaptureFlaggedWithTopFocusType() {
        val captures = listOf(
            capture(atMs = 1L, flaggedTypes = setOf(CorrectionType.POSTURE)),
            capture(atMs = 2L, flaggedTypes = emptySet()),
        )
        val selected = RepresentativeRepSelector.select(captures, CorrectionType.ELBOW_BEND)
        assertEquals(2L, selected?.atMs)
    }

    @Test
    fun skipsCapturesWithEmptyStartOrEndKeypoints() {
        val captures = listOf(
            capture(atMs = 1L, flaggedTypes = setOf(CorrectionType.ELBOW_BEND)),
            capture(atMs = 2L, start = emptyList(), flaggedTypes = setOf(CorrectionType.ELBOW_BEND)),
            capture(atMs = 3L, end = emptyList()),
        )
        val selected = RepresentativeRepSelector.select(captures, CorrectionType.ELBOW_BEND)
        assertEquals(1L, selected?.atMs)
    }

    @Test
    fun allCapturesUnusable_returnsNull() {
        val captures = listOf(
            capture(atMs = 1L, start = emptyList()),
            capture(atMs = 2L, end = emptyList()),
        )
        assertNull(RepresentativeRepSelector.select(captures, CorrectionType.ELBOW_BEND))
    }
}
