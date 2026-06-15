package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D
import com.ttcoachai.shared.models.StrokeCycle2D

/**
 * Pairs each forward drive with an optional preceding dropped backswing to form
 * a [StrokeCycle2D]. A "dropped backswing" is a raw wrist-speed peak that is NOT
 * in the [forwardStrokes] list (compared by [Stroke2D.peakFrame]).
 *
 * Pairing rule (Design note 1):
 *  For each drive D (in order), find the dropped raw peak R with the LARGEST
 *  peakFrame satisfying:
 *    prevPeak < R.peakFrame < D.peakFrame
 *  where prevPeak is the peakFrame of the previous forward drive (−1 for the first).
 *  Accept R only if frames[D.peakFrame].timestampMs − frames[R.peakFrame].timestampMs
 *  ≤ [maxPairGapMs]. Each dropped peak can only pair to one drive; the lower bound
 *  prevPeak ensures a dropped peak between two drives attaches to the LATER drive only.
 *
 * Timestamps from [frames] are used for the gap check (not intervalMs × frameDelta).
 * [intervalMs] is accepted for API parity; it serves as a fallback if [frames] are
 * absent (should not occur in production).
 */
object CyclePairing {

    /** Maximum allowed gap (ms) between a dropped backswing peak and the drive peak. */
    const val MAX_PAIR_GAP_MS = 800L

    /**
     * @param rawStrokes     All raw wrist-speed peaks (before ForwardStrokeFilter).
     * @param forwardStrokes Subset of [rawStrokes] that survived ForwardStrokeFilter.
     * @param frames         Full frame list; provides actual timestamps for gap checks.
     * @param intervalMs     Nominal frame interval (ms); used only as a timestamp
     *                       fallback when a frame is missing from [frames].
     * @param maxPairGapMs   Gap threshold; defaults to [MAX_PAIR_GAP_MS].
     * @return One [StrokeCycle2D] per forward drive, in the same order as [forwardStrokes].
     */
    fun pair(
        rawStrokes: List<Stroke2D>,
        forwardStrokes: List<Stroke2D>,
        frames: List<PoseFrame2D>,
        intervalMs: Long,
        maxPairGapMs: Long = MAX_PAIR_GAP_MS,
    ): List<StrokeCycle2D> {
        // Build a set of peakFrames that belong to forward strokes (O(n) lookup later).
        val forwardPeakFrames = forwardStrokes.mapTo(HashSet()) { it.peakFrame }

        // Collect all dropped peaks sorted ascending by peakFrame for efficient scanning.
        val droppedPeaks = rawStrokes.filter { it.peakFrame !in forwardPeakFrames }
            .sortedBy { it.peakFrame }

        val result = mutableListOf<StrokeCycle2D>()
        var prevPeak = -1

        for (drive in forwardStrokes) {
            val drivePeakTs = frameTimestamp(frames, drive.peakFrame, intervalMs)

            // Find the dropped raw peak R with the LARGEST peakFrame in (prevPeak, drive.peakFrame).
            // droppedPeaks is sorted ascending so we scan for the last one in that window.
            var bestCandidate: Stroke2D? = null
            for (candidate in droppedPeaks) {
                if (candidate.peakFrame <= prevPeak) continue        // must be after previous drive
                if (candidate.peakFrame >= drive.peakFrame) break    // sorted — nothing further qualifies
                bestCandidate = candidate  // keep overwriting to find the LARGEST peakFrame
            }

            val paired: Stroke2D? = if (bestCandidate != null) {
                val candidatePeakTs = frameTimestamp(frames, bestCandidate.peakFrame, intervalMs)
                if (drivePeakTs - candidatePeakTs <= maxPairGapMs) bestCandidate else null
            } else {
                null
            }

            result += StrokeCycle2D.of(backswing = paired, drive = drive)
            prevPeak = drive.peakFrame
        }

        return result
    }

    /**
     * Returns the timestamp of [frameIndex] from [frames], or estimates it as
     * [frameIndex] × [intervalMs] when the frame is absent.
     */
    private fun frameTimestamp(frames: List<PoseFrame2D>, frameIndex: Int, intervalMs: Long): Long =
        frames.getOrNull(frameIndex)?.timestampMs ?: (frameIndex * intervalMs)
}
