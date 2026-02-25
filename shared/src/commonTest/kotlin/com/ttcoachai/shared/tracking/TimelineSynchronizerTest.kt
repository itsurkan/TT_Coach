package com.ttcoachai.shared.tracking

import com.ttcoachai.shared.models.BallDetection
import com.ttcoachai.shared.models.BallDetectionStatus
import com.ttcoachai.shared.models.DataSource
import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.PoseFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineSynchronizerTest {

    private val synchronizer = TimelineSynchronizer()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun ball(frameIndex: Int, tsMs: Long, x: Float = 0.5f, y: Float = 0.5f) =
        BallDetection(
            x = x, y = y, confidence = 0.9f, radiusPx = 10f,
            frameIndex = frameIndex, timestampMs = tsMs,
            status = BallDetectionStatus.DETECTED
        )

    private fun pose(frameIndex: Int, tsMs: Long) =
        PoseFrame(
            frameIndex = frameIndex,
            timestampMs = tsMs,
            landmarks = emptyList()
        )

    // -------------------------------------------------------------------------
    // merge() — output ordering and count
    // -------------------------------------------------------------------------

    @Test
    fun `merge produces exactly one frame per timestamp in allTimestampsMs`() {
        val timestamps = listOf(0L, 33L, 66L, 99L)
        val poses = listOf(pose(0, 0L), pose(1, 33L))
        val balls = listOf(ball(0, 0L), ball(2, 66L))

        val result = synchronizer.merge(poses, balls, timestamps)

        assertEquals(timestamps.size, result.size, "Output count must match allTimestampsMs size")
    }

    @Test
    fun `merge output order matches allTimestampsMs order`() {
        val timestamps = listOf(0L, 33L, 66L)
        val result = synchronizer.merge(emptyList(), emptyList(), timestamps)

        for (i in timestamps.indices) {
            assertEquals(timestamps[i], result[i].timestampMs)
        }
    }

    @Test
    fun `merge returns empty list for empty timestamps`() {
        val result = synchronizer.merge(emptyList(), emptyList(), emptyList())
        assertTrue(result.isEmpty())
    }

    // -------------------------------------------------------------------------
    // merge() — exact timestamp match
    // -------------------------------------------------------------------------

    @Test
    fun `merge marks DETECTED when pose and ball timestamps match exactly`() {
        val timestamps = listOf(0L, 33L)
        val poses = listOf(pose(0, 0L), pose(1, 33L))
        val balls = listOf(ball(0, 0L), ball(1, 33L))

        val result = synchronizer.merge(poses, balls, timestamps)

        assertEquals(DataSource.DETECTED, result[0].poseSource)
        assertEquals(DataSource.DETECTED, result[0].ballSource)
        assertNotNull(result[0].pose)
        assertNotNull(result[0].ball)
    }

    @Test
    fun `merge assigns frameIndex from allTimestampsMs position`() {
        val timestamps = listOf(100L, 133L, 166L)
        val result = synchronizer.merge(emptyList(), emptyList(), timestamps)

        for (i in result.indices) {
            assertEquals(i, result[i].frameIndex)
        }
    }

    // -------------------------------------------------------------------------
    // merge() — missing data marked ABSENT
    // -------------------------------------------------------------------------

    @Test
    fun `merge marks pose ABSENT and null when no pose data exists for timestamp`() {
        val timestamps = listOf(0L, 33L)
        // Only ball data — no poses at all
        val balls = listOf(ball(0, 0L), ball(1, 33L))

        val result = synchronizer.merge(emptyList(), balls, timestamps)

        assertEquals(DataSource.ABSENT, result[0].poseSource)
        assertNull(result[0].pose)
        assertEquals(DataSource.ABSENT, result[1].poseSource)
        assertNull(result[1].pose)
    }

    @Test
    fun `merge marks ball ABSENT and null when no ball data exists for timestamp`() {
        val timestamps = listOf(0L, 33L)
        // Only pose data — no balls at all
        val poses = listOf(pose(0, 0L), pose(1, 33L))

        val result = synchronizer.merge(poses, emptyList(), timestamps)

        assertEquals(DataSource.ABSENT, result[0].ballSource)
        assertNull(result[0].ball)
        assertEquals(DataSource.ABSENT, result[1].ballSource)
        assertNull(result[1].ball)
    }

    @Test
    fun `merge marks frame ABSENT when both streams have no data for that timestamp`() {
        val timestamps = listOf(0L, 33L, 66L)
        // Data only at 0 and 66 — frame at 33 has no data
        val poses = listOf(pose(0, 0L), pose(2, 66L))
        val balls = listOf(ball(0, 0L), ball(2, 66L))

        val result = synchronizer.merge(poses, balls, timestamps)

        assertEquals(DataSource.ABSENT, result[1].poseSource)
        assertEquals(DataSource.ABSENT, result[1].ballSource)
        assertNull(result[1].pose)
        assertNull(result[1].ball)
    }

    // -------------------------------------------------------------------------
    // merge() — 1-frame interpolation
    // -------------------------------------------------------------------------

    @Test
    fun `merge interpolates ball for single-frame gap between two detections`() {
        val timestamps = listOf(0L, 33L, 66L)
        // Ball detected at 0 and 66 — single gap at 33
        val balls = listOf(ball(0, 0L, x = 0.2f, y = 0.4f), ball(2, 66L, x = 0.6f, y = 0.8f))

        val result = synchronizer.merge(emptyList(), balls, timestamps)

        val midFrame = result[1]
        assertEquals(DataSource.INTERPOLATED, midFrame.ballSource)
        assertNotNull(midFrame.ball)
    }

    @Test
    fun `merge interpolated ball position is midpoint of neighbours`() {
        val timestamps = listOf(0L, 33L, 66L)
        val balls = listOf(ball(0, 0L, x = 0.2f, y = 0.4f), ball(2, 66L, x = 0.6f, y = 0.8f))

        val result = synchronizer.merge(emptyList(), balls, timestamps)

        val interpolated = result[1].ball
        assertNotNull(interpolated)
        assertEquals(0.4f, interpolated.x, 0.01f, "Interpolated x should be midpoint (0.4)")
        assertEquals(0.6f, interpolated.y, 0.01f, "Interpolated y should be midpoint (0.6)")
    }

    @Test
    fun `merge does not interpolate ball across gap larger than 1 frame`() {
        val timestamps = listOf(0L, 33L, 66L, 99L, 132L)
        // Ball at frame 0 and frame 4 — gap of 3 frames — no interpolation expected
        val balls = listOf(ball(0, 0L, x = 0.1f, y = 0.5f), ball(4, 132L, x = 0.9f, y = 0.5f))

        val result = synchronizer.merge(emptyList(), balls, timestamps)

        // Frames 1, 2, 3 should be ABSENT (gap too large to interpolate)
        assertEquals(DataSource.ABSENT, result[1].ballSource)
        assertEquals(DataSource.ABSENT, result[2].ballSource)
        assertEquals(DataSource.ABSENT, result[3].ballSource)
    }

    // -------------------------------------------------------------------------
    // interpolateBall()
    // -------------------------------------------------------------------------

    @Test
    fun `interpolateBall returns midpoint position for equal time intervals`() {
        val before = ball(0, 0L, x = 0.1f, y = 0.2f)
        val after  = ball(2, 66L, x = 0.9f, y = 0.8f)

        val result = synchronizer.interpolateBall(before, after, targetTimestampMs = 33L)

        assertEquals(0.5f, result.x, 0.01f)
        assertEquals(0.5f, result.y, 0.01f)
    }

    @Test
    fun `interpolateBall result has INTERPOLATED status`() {
        val before = ball(0, 0L)
        val after  = ball(2, 66L)

        val result = synchronizer.interpolateBall(before, after, targetTimestampMs = 33L)

        assertEquals(BallDetectionStatus.DETECTED, result.status,
            "interpolateBall should produce a DETECTED-status ball (data is present)")
    }

    @Test
    fun `interpolateBall result timestamp matches target`() {
        val before = ball(0, 0L)
        val after  = ball(2, 66L)

        val result = synchronizer.interpolateBall(before, after, targetTimestampMs = 33L)

        assertEquals(33L, result.timestampMs)
    }

    @Test
    fun `interpolateBall clamps to before when target equals before timestamp`() {
        val before = ball(0, 0L, x = 0.2f, y = 0.3f)
        val after  = ball(2, 66L, x = 0.8f, y = 0.9f)

        val result = synchronizer.interpolateBall(before, after, targetTimestampMs = 0L)

        assertEquals(0.2f, result.x, 0.01f)
        assertEquals(0.3f, result.y, 0.01f)
    }
}
