package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D
import com.ttcoachai.shared.models.StrokeCycle2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CyclePairingTest {

    /**
     * Builds minimal frames with explicit timestamps. Keypoints are irrelevant for
     * pairing logic (only timestampMs is used); fill with a single dummy keypoint.
     */
    private fun frames(timestampsMs: List<Long>): List<PoseFrame2D> =
        timestampsMs.mapIndexed { i, ts ->
            PoseFrame2D(i, ts, List(17) { Keypoint2D(0.5f, 0.5f, 1f) })
        }

    private fun stroke(index: Int, start: Int, peak: Int, end: Int, speed: Float = 2.0f) =
        Stroke2D(strokeIndex = index, startFrame = start, peakFrame = peak, endFrame = end, peakSpeed = speed)

    // -------------------------------------------------------------------------
    // Case (a): drive WITH an adjacent dropped backswing within gap → paired
    // -------------------------------------------------------------------------
    @Test
    fun driveWithAdjacentDroppedBackswingIsPaired() {
        // Frame layout (each frame = 100 ms):
        //   frame 0  ts=0    backswing starts
        //   frame 2  ts=200  backswing peak (RAW, not forward → dropped by FSF)
        //   frame 4  ts=400  backswing ends / drive starts
        //   frame 6  ts=600  drive peak (forward → in forwardStrokes)
        //   frame 8  ts=800  drive ends
        //
        // Gap = 600 - 200 = 400 ms < 800 ms → pair accepted.
        val ts = (0..8).map { it * 100L }
        val f = frames(ts)

        val backswingRaw = stroke(index = 0, start = 0, peak = 2, end = 4)
        val driveRaw     = stroke(index = 1, start = 4, peak = 6, end = 8)

        val rawStrokes     = listOf(backswingRaw, driveRaw)
        val forwardStrokes = listOf(driveRaw) // backswing dropped

        val cycles = CyclePairing.pair(rawStrokes, forwardStrokes, f, intervalMs = 100L)

        assertEquals(1, cycles.size)
        val c = cycles[0]
        assertEquals(backswingRaw, c.backswing, "backswing should be paired")
        assertEquals(driveRaw, c.drive)
        assertEquals(0, c.startFrame, "span starts at backswing.startFrame")
        assertEquals(8, c.endFrame,   "span ends at drive.endFrame")
        assertEquals(6, c.peakFrame)
    }

    // -------------------------------------------------------------------------
    // Case (b): drive with NO preceding dropped peak → backswing = null
    // -------------------------------------------------------------------------
    @Test
    fun driveWithNoDroppedPeakHasNullBackswing() {
        // Only one raw peak, and it IS the forward drive — no dropped candidates.
        val ts = (0..4).map { it * 100L }
        val f = frames(ts)

        val driveRaw = stroke(index = 0, start = 0, peak = 2, end = 4)

        val rawStrokes     = listOf(driveRaw)
        val forwardStrokes = listOf(driveRaw)

        val cycles = CyclePairing.pair(rawStrokes, forwardStrokes, f, intervalMs = 100L)

        assertEquals(1, cycles.size)
        val c = cycles[0]
        assertNull(c.backswing)
        assertEquals(driveRaw, c.drive)
        assertEquals(0, c.startFrame, "span starts at drive.startFrame when no backswing")
        assertEquals(4, c.endFrame)
    }

    // -------------------------------------------------------------------------
    // Case (c): dropped peak OUTSIDE maxPairGapMs → not paired
    // -------------------------------------------------------------------------
    @Test
    fun droppedPeakOutsideGapIsNotPaired() {
        // backswing peak at ts=0, drive peak at ts=900 → gap = 900 ms > 800 ms.
        val ts = listOf(0L, 100L, 200L, 400L, 600L, 700L, 800L, 900L, 1000L)
        val f = frames(ts)

        val backswingRaw = stroke(index = 0, start = 0, peak = 0, end = 3)
        val driveRaw     = stroke(index = 1, start = 3, peak = 7, end = 8)

        val rawStrokes     = listOf(backswingRaw, driveRaw)
        val forwardStrokes = listOf(driveRaw)

        val cycles = CyclePairing.pair(rawStrokes, forwardStrokes, f, intervalMs = 100L)

        assertEquals(1, cycles.size)
        assertNull(cycles[0].backswing, "gap exceeds MAX_PAIR_GAP_MS → no pairing")
        assertEquals(driveRaw.startFrame, cycles[0].startFrame)
    }

    // -------------------------------------------------------------------------
    // Case (d): continuous play — one dropped peak between two drives attaches
    //           to the LATER drive only
    // -------------------------------------------------------------------------
    @Test
    fun droppedPeakBetweenTwoDrivesAttachesToLaterDriveOnly() {
        // Timeline:
        //   frame 0–2  drive1 (forward)
        //   frame 3–5  dropped backswing (raw only)
        //   frame 6–8  drive2 (forward)
        //
        // Drive1's prevPeak = -1; candidate for drive1 must have peakFrame > -1
        //   AND peakFrame < drive1.peakFrame(=1) → no candidate (dropped.peakFrame=4 > 1).
        // Drive2's prevPeak = 1 (drive1.peakFrame); candidate must have peakFrame > 1
        //   AND peakFrame < drive2.peakFrame(=7) → dropped at frame 4 qualifies.
        val ts = (0..8).map { it * 100L }
        val f = frames(ts)

        val drive1   = stroke(index = 0, start = 0, peak = 1, end = 2)
        val dropped  = stroke(index = 1, start = 3, peak = 4, end = 5)
        val drive2   = stroke(index = 2, start = 6, peak = 7, end = 8)

        val rawStrokes     = listOf(drive1, dropped, drive2)
        val forwardStrokes = listOf(drive1, drive2)

        val cycles = CyclePairing.pair(rawStrokes, forwardStrokes, f, intervalMs = 100L)

        assertEquals(2, cycles.size)
        assertNull(cycles[0].backswing, "drive1 has no dropped peak before it")
        assertEquals(drive2, cycles[1].drive)
        assertEquals(dropped, cycles[1].backswing, "dropped peak pairs to the LATER drive2")
        assertEquals(3, cycles[1].startFrame, "cycle2 span starts at dropped.startFrame")
    }

    // -------------------------------------------------------------------------
    // Case (e): first drive never pairs to a peak before frame 0
    // -------------------------------------------------------------------------
    @Test
    fun firstDriveNeverPairsToNegativeFrame() {
        // Verify that even if raw strokes list included a stroke at peakFrame = 0,
        // the first drive (prevPeak = -1) only accepts candidates with peakFrame > -1
        // AND peakFrame < drive.peakFrame; since drive.peakFrame=2 and dropped.peakFrame=0,
        // it should still pair if within gap… but the key is prevPeak bound prevents
        // any imaginary peak "before frame 0".
        // Separate scenario: there IS a valid dropped peak at frame 0 (still > prevPeak=-1),
        // drive peak at frame 3, gap = 3*100 = 300 ms < 800 ms → SHOULD pair.
        // This confirms the lower bound is -1 (not 0), so frame 0 is a valid candidate.
        val ts = (0..5).map { it * 100L }
        val f = frames(ts)

        val backswingRaw = stroke(index = 0, start = 0, peak = 0, end = 1)
        val driveRaw     = stroke(index = 1, start = 1, peak = 3, end = 5)

        val rawStrokes     = listOf(backswingRaw, driveRaw)
        val forwardStrokes = listOf(driveRaw)

        val cycles = CyclePairing.pair(rawStrokes, forwardStrokes, f, intervalMs = 100L)

        assertEquals(1, cycles.size)
        assertEquals(backswingRaw, cycles[0].backswing,
            "dropped peak at frame 0 is valid for the first drive (prevPeak = -1 < 0)")
        assertEquals(0, cycles[0].startFrame)
    }

    // -------------------------------------------------------------------------
    // Boundary: gap exactly at MAX_PAIR_GAP_MS → paired (≤ not <)
    // -------------------------------------------------------------------------
    @Test
    fun droppedPeakExactlyAtMaxGapIsPaired() {
        // backswing peak at frame index 1 (ts=100), drive peak at frame index 3 (ts=900).
        // gap = 900 - 100 = 800 = MAX_PAIR_GAP_MS → should pair (≤).
        val ts = listOf(0L, 100L, 500L, 900L, 1000L)
        val f = frames(ts)
        val backswingRaw = stroke(index = 0, start = 0, peak = 1, end = 2)
        val driveRaw     = stroke(index = 1, start = 2, peak = 3, end = 4)

        val rawStrokes     = listOf(backswingRaw, driveRaw)
        val forwardStrokes = listOf(driveRaw)

        val cycles = CyclePairing.pair(rawStrokes, forwardStrokes, f, intervalMs = 100L)

        assertEquals(1, cycles.size)
        assertEquals(backswingRaw, cycles[0].backswing)
    }
}
