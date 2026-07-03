import { PoseFrame2D, Stroke2D, StrokeCycle2D, makeCycle } from './types'

/**
 * Pairs each forward drive with an optional preceding dropped backswing to form
 * a StrokeCycle2D. Mirrors CyclePairing.kt (source of truth: shared/drill/CyclePairing.kt).
 *
 * A "dropped backswing" is a raw wrist-speed peak NOT in the forwardStrokes list
 * (compared by peakFrame).
 *
 * Pairing rule (Design note 1):
 *  For each drive D (in order), find the dropped raw peak R with the LARGEST
 *  peakFrame satisfying:
 *    prevPeak < R.peakFrame < D.peakFrame
 *  where prevPeak is the peakFrame of the previous forward drive (−1 for the first).
 *  Accept R only if frames[D.peakFrame].timestampMs − frames[R.peakFrame].timestampMs
 *  ≤ maxPairGapMs. The lower bound prevPeak ensures a dropped peak between two drives
 *  attaches to the LATER drive only.
 *
 * Timestamps from `frames` are preferred for gap checks; falls back to
 * frameIndex × intervalMs when a frame is missing.
 */

/** Maximum allowed gap (ms) between a dropped backswing peak and the drive peak. */
export const MAX_PAIR_GAP_MS = 800

/**
 * @param rawStrokes     All raw wrist-speed peaks (before ForwardStrokeFilter).
 * @param forwardStrokes Subset of rawStrokes that survived ForwardStrokeFilter.
 * @param frames         Full frame list; provides actual timestamps for gap checks.
 * @param intervalMs     Nominal frame interval (ms); used only as a timestamp fallback.
 * @param maxPairGapMs   Gap threshold; defaults to MAX_PAIR_GAP_MS.
 * @returns One StrokeCycle2D per forward drive, in the same order as forwardStrokes.
 */
export function pairCycles(
  rawStrokes: Stroke2D[],
  forwardStrokes: Stroke2D[],
  frames: PoseFrame2D[],
  intervalMs: number,
  maxPairGapMs: number = MAX_PAIR_GAP_MS,
): StrokeCycle2D[] {
  // Set of peakFrames belonging to forward strokes for O(1) membership test.
  const forwardPeakFrames = new Set(forwardStrokes.map(s => s.peakFrame))

  // Dropped peaks sorted ascending by peakFrame.
  const droppedPeaks = rawStrokes
    .filter(s => !forwardPeakFrames.has(s.peakFrame))
    .sort((a, b) => a.peakFrame - b.peakFrame)

  const result: StrokeCycle2D[] = []
  let prevPeak = -1

  for (const drive of forwardStrokes) {
    const drivePeakTs = frameTimestamp(frames, drive.peakFrame, intervalMs)

    // Find the dropped peak with the LARGEST peakFrame in (prevPeak, drive.peakFrame).
    let bestCandidate: Stroke2D | null = null
    for (const candidate of droppedPeaks) {
      if (candidate.peakFrame <= prevPeak) continue       // must be after previous drive
      if (candidate.peakFrame >= drive.peakFrame) break   // sorted — nothing further qualifies
      bestCandidate = candidate  // keep overwriting to find the LARGEST peakFrame
    }

    let paired: Stroke2D | null = null
    if (bestCandidate !== null) {
      const candidatePeakTs = frameTimestamp(frames, bestCandidate.peakFrame, intervalMs)
      if (drivePeakTs - candidatePeakTs <= maxPairGapMs) {
        paired = bestCandidate
      }
    }

    result.push(makeCycle(paired, drive))
    prevPeak = drive.peakFrame
  }

  return result
}

/** Returns the timestamp of frameIndex from frames, or frameIndex × intervalMs as fallback. */
function frameTimestamp(frames: PoseFrame2D[], frameIndex: number, intervalMs: number): number {
  return frames[frameIndex]?.timestampMs ?? frameIndex * intervalMs
}
