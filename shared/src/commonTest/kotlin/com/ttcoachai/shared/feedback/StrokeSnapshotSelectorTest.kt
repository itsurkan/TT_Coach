/*
 * AI Coach for Table Tennis
 * StrokeSnapshotSelectorTest — Unit tests for peak/contact frame selection over a stroke rep.
 */

package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.models.Landmark3D
import kotlin.test.Test
import kotlin.test.assertEquals

class StrokeSnapshotSelectorTest {

    // ── peakFrameIndex ────────────────────────────────────────────────────────

    @Test
    fun peakFrameIndex_emptyList_returnsMinusOne() {
        assertEquals(-1, StrokeSnapshotSelector.peakFrameIndex(emptyList()))
    }

    @Test
    fun peakFrameIndex_singleFrame_returnsHalfSize() {
        val frames = listOf(makeFrame(wrist = Landmark3D(0.5f, 0.5f, 0f, visibility = 1f)))
        assertEquals(frames.size / 2, StrokeSnapshotSelector.peakFrameIndex(frames))
    }

    @Test
    fun peakFrameIndex_fewerThanTwoUsableFrames_returnsHalfSize() {
        // 3 frames but only one has a visible wrist -> fewer than 2 usable
        val frames = listOf(
            makeFrame(wrist = Landmark3D(0.1f, 0.1f, 0f, visibility = 0.1f)),
            makeFrame(wrist = Landmark3D(0.5f, 0.5f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.2f, 0.2f, 0f, visibility = 0.2f))
        )
        assertEquals(frames.size / 2, StrokeSnapshotSelector.peakFrameIndex(frames))
    }

    @Test
    fun peakFrameIndex_picksFastestWristFrame() {
        // Wrist barely moves frame0->1, jumps a lot frame1->2, small move frame2->3
        val frames = listOf(
            makeFrame(wrist = Landmark3D(0.10f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.11f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.80f, 0.75f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.82f, 0.76f, 0f, visibility = 1f))
        )
        // Largest delta is between frame index 1 and 2 -> peak reported at index 2
        assertEquals(2, StrokeSnapshotSelector.peakFrameIndex(frames))
    }

    @Test
    fun peakFrameIndex_skipsLowVisibilityFrames() {
        val frames = listOf(
            makeFrame(wrist = Landmark3D(0.10f, 0.10f, 0f, visibility = 1f)),
            // Low-visibility frame with a huge (spurious) jump — must be skipped as a candidate/sample
            makeFrame(wrist = Landmark3D(0.95f, 0.95f, 0f, visibility = 0.1f)),
            makeFrame(wrist = Landmark3D(0.30f, 0.25f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.31f, 0.25f, 0f, visibility = 1f))
        )
        // Usable frames are indices 0, 2, 3. Largest usable-to-usable delta is 0->2.
        assertEquals(2, StrokeSnapshotSelector.peakFrameIndex(frames))
    }

    @Test
    fun peakFrameIndex_missingWristLandmark_treatedAsLowVisibility() {
        val shortFrame = listOf(Landmark3D(0.5f, 0.5f, 0f, visibility = 1f)) // no index 16
        val frames = listOf(
            shortFrame,
            makeFrame(wrist = Landmark3D(0.5f, 0.5f, 0f, visibility = 1f))
        )
        assertEquals(frames.size / 2, StrokeSnapshotSelector.peakFrameIndex(frames))
    }

    // ── contactFrameIndex ─────────────────────────────────────────────────────

    @Test
    fun contactFrameIndex_matchesPeakFrameIndex() {
        val frames = listOf(
            makeFrame(wrist = Landmark3D(0.10f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.11f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.80f, 0.75f, 0f, visibility = 1f))
        )
        assertEquals(
            StrokeSnapshotSelector.peakFrameIndex(frames),
            StrokeSnapshotSelector.contactFrameIndex(frames)
        )
    }

    @Test
    fun contactFrameIndex_emptyList_returnsMinusOne() {
        assertEquals(-1, StrokeSnapshotSelector.contactFrameIndex(emptyList()))
    }

    // ── bestSnapshotFrameIndex ────────────────────────────────────────────────

    @Test
    fun bestSnapshotFrameIndex_emptyList_returnsMinusOne() {
        assertEquals(-1, StrokeSnapshotSelector.bestSnapshotFrameIndex(emptyList()))
    }

    @Test
    fun bestSnapshotFrameIndex_peakDegraded_neighborFullyVisibleWins() {
        // Wrist speed peaks at index 2 (frame1 -> frame2 jump), but frame2's CORE landmarks are
        // mostly occluded by motion blur. Frame 3 (within the +-4 window) is fully visible and
        // should be picked instead; every other candidate in the window is also degraded so the
        // outcome isn't a coincidence of the lower-index tiebreak.
        val frames = listOf(
            makeDegradedFrame(
                wrist = Landmark3D(0.10f, 0.10f, 0f, visibility = 1f),
                visibleCoreCount = 2
            ),
            makeDegradedFrame(
                wrist = Landmark3D(0.11f, 0.10f, 0f, visibility = 1f),
                visibleCoreCount = 2
            ),
            makeDegradedFrame(
                wrist = Landmark3D(0.80f, 0.75f, 0f, visibility = 1f),
                visibleCoreCount = 2
            ),
            makeFrame(wrist = Landmark3D(0.82f, 0.76f, 0f, visibility = 1f))
        )

        assertEquals(2, StrokeSnapshotSelector.peakFrameIndex(frames))
        assertEquals(3, StrokeSnapshotSelector.bestSnapshotFrameIndex(frames))
    }

    @Test
    fun bestSnapshotFrameIndex_allEqualInWindow_returnsPeak() {
        val frames = listOf(
            makeFrame(wrist = Landmark3D(0.10f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.11f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.80f, 0.75f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.82f, 0.76f, 0f, visibility = 1f))
        )

        val peak = StrokeSnapshotSelector.peakFrameIndex(frames)
        assertEquals(peak, StrokeSnapshotSelector.bestSnapshotFrameIndex(frames))
    }

    @Test
    fun bestSnapshotFrameIndex_windowClampedAtSequenceStart() {
        // Peak lands near the start of a short sequence; window (peak-4..peak+4) must clamp to 0,
        // not go negative / throw.
        val frames = listOf(
            makeFrame(wrist = Landmark3D(0.10f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.80f, 0.75f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.81f, 0.76f, 0f, visibility = 1f))
        )

        val peak = StrokeSnapshotSelector.peakFrameIndex(frames)
        val result = StrokeSnapshotSelector.bestSnapshotFrameIndex(frames)
        assertEquals(peak, result)
    }

    @Test
    fun bestSnapshotFrameIndex_windowClampedAtSequenceEnd() {
        // Peak lands near the end of the sequence; window must clamp to frames.size - 1.
        val frames = listOf(
            makeFrame(wrist = Landmark3D(0.10f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.11f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.80f, 0.75f, 0f, visibility = 1f))
        )

        val peak = StrokeSnapshotSelector.peakFrameIndex(frames)
        assertEquals(2, peak)
        val result = StrokeSnapshotSelector.bestSnapshotFrameIndex(frames)
        assertEquals(peak, result)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds a 33-landmark frame with neutral defaults, overriding the wrist (index 16). */
    private fun makeFrame(wrist: Landmark3D): List<Landmark3D> =
        MutableList(33) { Landmark3D(0.5f, 0.5f, 0f, visibility = 1f) }.also {
            it[16] = wrist
        }

    /**
     * Builds a frame like [makeFrame] but degrades all-but-[visibleCoreCount] of the *other* CORE
     * landmarks (shoulders/elbows/hips/knees/ankles — everything except the wrist at index 16) to
     * below MIN_VISIBILITY, simulating a motion-blur frame with a malformed rendered skeleton. The
     * wrist keeps the passed-in [wrist] value (kept fully visible) so wrist-speed/peak detection
     * is unaffected; [visibleCoreCount] therefore counts landmarks left visible in addition to the
     * wrist.
     */
    private fun makeDegradedFrame(wrist: Landmark3D, visibleCoreCount: Int): List<Landmark3D> {
        val otherCore = intArrayOf(11, 12, 13, 14, 23, 24, 25, 26, 27, 28)
        val frame = MutableList(33) { Landmark3D(0.5f, 0.5f, 0f, visibility = 1f) }
        otherCore.drop(visibleCoreCount).forEach { index ->
            frame[index] = Landmark3D(0.5f, 0.5f, 0f, visibility = 0.1f)
        }
        frame[16] = wrist
        return frame
    }
}
