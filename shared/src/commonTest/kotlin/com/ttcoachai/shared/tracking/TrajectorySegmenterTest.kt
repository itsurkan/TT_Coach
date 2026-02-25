package com.ttcoachai.shared.tracking

import com.ttcoachai.shared.models.BallDetection
import com.ttcoachai.shared.models.BallDetectionStatus
import com.ttcoachai.shared.models.ContactType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrajectorySegmenterTest {

    private val segmenter = TrajectorySegmenter()

    // --- Helper ---
    private fun det(frameIndex: Int, x: Float, y: Float, tsMs: Long = frameIndex * 33L) =
        BallDetection(
            x = x, y = y, confidence = 0.9f, radiusPx = 10f,
            frameIndex = frameIndex, timestampMs = tsMs,
            status = BallDetectionStatus.DETECTED
        )

    // =====================================================================
    // detectContacts() — edge cases
    // =====================================================================

    @Test
    fun `detectContacts returns empty for fewer than 3 detections`() {
        val contacts2 = segmenter.detectContacts(listOf(det(0, 0.1f, 0.5f), det(1, 0.2f, 0.5f)))
        assertTrue(contacts2.isEmpty(), "Need ≥ 3 detections to detect contacts")
    }

    @Test
    fun `detectContacts returns empty for straight line trajectory`() {
        // No velocity changes → no contacts expected
        val dets = (0..5).map { i -> det(i, 0.1f + i * 0.05f, 0.5f) }
        val contacts = segmenter.detectContacts(dets)
        assertTrue(contacts.isEmpty(), "Straight line should produce no contacts")
    }

    // =====================================================================
    // detectContacts() — bounce (vertical velocity reversal)
    // =====================================================================

    @Test
    fun `detectContacts detects bounce from vertical velocity reversal`() {
        // Ball descends then ascends — vertical velocity reversal
        val dets = listOf(
            det(0, 0.30f, 0.20f),
            det(1, 0.35f, 0.35f),
            det(2, 0.40f, 0.50f),  // ← lowest point (bounce here)
            det(3, 0.45f, 0.35f),
            det(4, 0.50f, 0.20f)
        )
        val contacts = segmenter.detectContacts(dets)
        assertTrue(contacts.isNotEmpty(), "Should detect a bounce contact")
        val bounce = contacts.first()
        assertEquals(ContactType.BOUNCE, bounce.type)
        // Bounce should be near frame 2 (at the lowest point)
        assertTrue(bounce.frameIndex in 1..3, "Bounce frameIndex should be near the reversal point")
    }

    // =====================================================================
    // detectContacts() — paddle contact (speed ratio > 1.8x)
    // =====================================================================

    @Test
    fun `detectContacts detects paddle contact from speed spike`() {
        // Ball moving slowly, then suddenly much faster after contact
        // Slow: delta 0.02/frame → fast: delta 0.10/frame → ratio = 5x
        val dets = listOf(
            det(0, 0.10f, 0.50f),
            det(1, 0.12f, 0.50f),  // dx=0.02
            det(2, 0.14f, 0.50f),  // dx=0.02 (slow approach)
            det(3, 0.24f, 0.50f),  // dx=0.10 (fast after paddle)
            det(4, 0.34f, 0.50f)   // dx=0.10
        )
        val contacts = segmenter.detectContacts(dets)
        assertTrue(contacts.isNotEmpty(), "Should detect a paddle contact")
        val paddle = contacts.first()
        assertEquals(ContactType.PADDLE_CONTACT, paddle.type)
        assertEquals(2, paddle.frameIndex)
    }

    // =====================================================================
    // segment() — splitting at contacts
    // =====================================================================

    @Test
    fun `segment returns empty for empty detections`() {
        val segments = segmenter.segment(emptyList(), 33L)
        assertTrue(segments.isEmpty())
    }

    @Test
    fun `segment returns single segment for straight-line trajectory`() {
        val dets = (0..4).map { i -> det(i, 0.1f + i * 0.05f, 0.5f) }
        val segments = segmenter.segment(dets, 33L)
        assertEquals(1, segments.size, "No contacts → 1 segment")
    }

    @Test
    fun `segment splits trajectory at bounce contact`() {
        // Bounce trajectory: two arcs separated by vertical reversal
        val dets = listOf(
            det(0, 0.20f, 0.20f),
            det(1, 0.30f, 0.35f),
            det(2, 0.40f, 0.50f),  // bounce
            det(3, 0.50f, 0.35f),
            det(4, 0.60f, 0.20f)
        )
        val segments = segmenter.segment(dets, 33L)
        assertTrue(segments.size >= 2, "Bounce should split trajectory into ≥ 2 segments")
    }

    @Test
    fun `segment fills gaps in detected positions`() {
        // Straight line with two missing frames in the middle
        val dets = listOf(
            det(0, 0.10f, 0.50f),
            det(1, 0.20f, 0.50f),
            BallDetection(0f, 0f, 0f, 0f, 2, 66L, BallDetectionStatus.NOT_DETECTED),
            BallDetection(0f, 0f, 0f, 0f, 3, 99L, BallDetectionStatus.NOT_DETECTED),
            det(4, 0.50f, 0.50f)
        )
        val segments = segmenter.segment(dets, 33L)
        assertTrue(segments.isNotEmpty())
        val totalFitted = segments.sumOf { it.fittedPositions.size }
        assertTrue(totalFitted >= 3, "Fitted positions should cover detected + gap frames")
    }

    @Test
    fun `segment segment indices are sequential from 0`() {
        val dets = listOf(
            det(0, 0.20f, 0.20f),
            det(1, 0.30f, 0.35f),
            det(2, 0.40f, 0.50f),
            det(3, 0.50f, 0.35f),
            det(4, 0.60f, 0.20f)
        )
        val segments = segmenter.segment(dets, 33L)
        segments.forEachIndexed { idx, seg ->
            assertEquals(idx, seg.segmentIndex, "Segment indices must be sequential")
        }
    }

    // =====================================================================
    // Recursive sub-splitting on high RMS (contract check)
    // =====================================================================

    @Test
    fun `segments have non-negative fitRmsError`() {
        val dets = (0..6).map { i -> det(i, 0.1f + i * 0.05f, 0.5f + i * 0.01f) }
        val segments = segmenter.segment(dets, 33L)
        segments.forEach { seg ->
            assertTrue(seg.fitRmsError >= 0.0, "RMS error must be non-negative")
        }
    }
}
