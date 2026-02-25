package com.ttcoachai.shared.tracking

import com.ttcoachai.shared.models.BallDetection
import com.ttcoachai.shared.models.BallDetectionStatus
import com.ttcoachai.shared.models.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrajectoryFilterTest {

    // --- Helper to build DETECTED BallDetection instances ---
    private fun det(frameIndex: Int, x: Float, y: Float, tsMs: Long = frameIndex * 33L) =
        BallDetection(
            x = x, y = y, confidence = 0.9f, radiusPx = 10f,
            frameIndex = frameIndex, timestampMs = tsMs,
            status = BallDetectionStatus.DETECTED
        )

    // =====================================================================
    // fit() — null / linear / parabolic
    // =====================================================================

    @Test
    fun `fit returns null for empty list`() {
        assertNull(TrajectoryFilter.fit(emptyList()))
    }

    @Test
    fun `fit returns null for single detection`() {
        assertNull(TrajectoryFilter.fit(listOf(det(0, 0.1f, 0.5f))))
    }

    @Test
    fun `fit returns linear fit (cy=0) for exactly 2 detections`() {
        val dets = listOf(
            det(0, 0.1f, 0.5f),
            det(1, 0.2f, 0.5f)
        )
        val fit = TrajectoryFilter.fit(dets)
        assertNotNull(fit)
        assertEquals(0.0, fit.cy, 1e-9, "2-point fit must be linear (cy == 0)")
    }

    @Test
    fun `fit returns parabolic fit for 5 horizontal detections`() {
        // Straight horizontal line → cy should be near 0, by near 0
        val dets = (0..4).map { i -> det(i, 0.1f + i * 0.1f, 0.5f) }
        val fit = TrajectoryFilter.fit(dets)
        assertNotNull(fit)
        // bx should be ≈ 0.1 (change in x per frame unit)
        assertEquals(0.1, fit.bx, 1e-3)
        // cy should be ≈ 0 (no vertical curvature)
        assertEquals(0.0, fit.cy, 1e-3)
    }

    @Test
    fun `fit captures parabolic curvature for arc trajectory`() {
        // y(t) = 0.3 + 0.02*t + 0.02*t² → cy ≈ 0.02
        val dets = (0..4).map { i ->
            val t = i.toDouble()
            val y = (0.3 + 0.02 * t + 0.02 * t * t).toFloat()
            det(i, 0.1f + i * 0.1f, y)
        }
        val fit = TrajectoryFilter.fit(dets)
        assertNotNull(fit)
        assertTrue(fit.cy > 0.0, "cy should be positive for downward parabola")
    }

    // =====================================================================
    // evaluate()
    // =====================================================================

    @Test
    fun `evaluate returns reference position at t=0`() {
        val dets = listOf(det(0, 0.2f, 0.4f), det(1, 0.3f, 0.4f), det(2, 0.4f, 0.4f))
        val fit = TrajectoryFilter.fit(dets)!!
        val (x, y) = TrajectoryFilter.evaluate(fit, timestampMs = 0L, referenceTimestampMs = 0L)
        assertEquals(fit.ax.toFloat(), x, 1e-4f)
        assertEquals(fit.ay.toFloat(), y, 1e-4f)
    }

    @Test
    fun `evaluate interpolates mid-point correctly for linear x`() {
        val dets = listOf(det(0, 0.1f, 0.5f), det(2, 0.3f, 0.5f))
        val fit = TrajectoryFilter.fit(dets)!!
        // At t=1 (33ms from start), x should be ≈ 0.2
        val (x, _) = TrajectoryFilter.evaluate(fit, timestampMs = 33L, referenceTimestampMs = 0L)
        assertEquals(0.2f, x, 0.02f)
    }

    // =====================================================================
    // rmsError()
    // =====================================================================

    @Test
    fun `rmsError returns 0 for perfect fit on straight line`() {
        val dets = (0..4).map { i -> det(i, 0.1f + i * 0.1f, 0.5f) }
        val fit = TrajectoryFilter.fit(dets)!!
        val rms = TrajectoryFilter.rmsError(fit, dets)
        assertEquals(0.0, rms, 1e-6)
    }

    @Test
    fun `rmsError is positive when detections deviate from fit`() {
        val dets = listOf(
            det(0, 0.1f, 0.5f),
            det(1, 0.2f, 0.5f),
            det(2, 0.3f, 0.6f),  // ← deliberate outlier
            det(3, 0.4f, 0.5f),
            det(4, 0.5f, 0.5f)
        )
        val fit = TrajectoryFilter.fit(dets)!!
        val rms = TrajectoryFilter.rmsError(fit, dets)
        assertTrue(rms > 0.0, "RMS should be positive for imperfect detections")
    }

    // =====================================================================
    // fillGaps()
    // =====================================================================

    @Test
    fun `fillGaps produces one position per frame in range`() {
        val dets = listOf(
            det(0, 0.1f, 0.5f),
            det(1, 0.2f, 0.5f),
            det(4, 0.5f, 0.5f)
        )
        val fit = TrajectoryFilter.fit(dets)!!
        val filled = TrajectoryFilter.fillGaps(fit, dets, 0, 4, 33L)
        assertEquals(5, filled.size, "Should produce one position for each frame 0..4")
    }

    @Test
    fun `fillGaps tags detected positions as DETECTED and gaps as INTERPOLATED`() {
        val dets = listOf(
            det(0, 0.1f, 0.5f),
            det(1, 0.2f, 0.5f),
            det(4, 0.5f, 0.5f)
        )
        val fit = TrajectoryFilter.fit(dets)!!
        val filled = TrajectoryFilter.fillGaps(fit, dets, 0, 4, 33L)

        assertEquals(DataSource.DETECTED,     filled[0].source)
        assertEquals(DataSource.DETECTED,     filled[1].source)
        assertEquals(DataSource.INTERPOLATED, filled[2].source)
        assertEquals(DataSource.INTERPOLATED, filled[3].source)
        assertEquals(DataSource.DETECTED,     filled[4].source)
    }

    @Test
    fun `fillGaps interpolated positions are within reasonable range of detected`() {
        // Straight line x=0.1+0.1*t, y=0.5 → frames 2 and 3 gaps should be x≈0.3/0.4
        val dets = listOf(
            det(0, 0.1f, 0.5f),
            det(1, 0.2f, 0.5f),
            det(4, 0.5f, 0.5f)
        )
        val fit = TrajectoryFilter.fit(dets)!!
        val filled = TrajectoryFilter.fillGaps(fit, dets, 0, 4, 33L)

        assertEquals(0.3f, filled[2].x, 0.02f, "Frame 2 x should be ≈ 0.3")
        assertEquals(0.4f, filled[3].x, 0.02f, "Frame 3 x should be ≈ 0.4")
    }
}
