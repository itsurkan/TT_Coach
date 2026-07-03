import { Coco17, Handedness, PoseFrame2D, Stroke2D } from './types'
import { DEFAULT_MIN_SCORE, scored } from './facing'

/**
 * Mirrors StrokeDetector2D.kt: wrist-speed local maxima with keep-max NMS and
 * valley-clamped boundaries. Speeds are TORSO-LENGTHS PER SECOND (invariant to
 * camera distance and fps); all tuning windows are MILLISECONDS converted to
 * frame counts via intervalMs.
 */
export interface StrokeDetectorOptions {
  minScore?: number
  smoothingWindowMs?: number
  peakWindowRadiusMs?: number
  /** Torso-lengths/sec, applied to the SMOOTHED signal. */
  minPeakSpeed?: number
  boundaryFraction?: number
  minPeakGapMs?: number
}

export const DETECTOR_DEFAULTS: Required<StrokeDetectorOptions> = {
  minScore: DEFAULT_MIN_SCORE,
  smoothingWindowMs: 300,
  peakWindowRadiusMs: 300,
  minPeakSpeed: 1.0,
  boundaryFraction: 0.3,
  minPeakGapMs: 500,
}

const MIN_TORSO_LEN = 1e-4

export function detectStrokes(
  frames: PoseFrame2D[],
  handedness: Handedness,
  xScale: number,
  intervalMs: number,
  options: StrokeDetectorOptions = {},
): Stroke2D[] {
  const opts = { ...DETECTOR_DEFAULTS, ...options }
  if (intervalMs <= 0) throw new Error(`intervalMs must be > 0, got ${intervalMs}`)
  if (frames.length < 2) return []
  const torsoLen = medianTorsoLength(frames, xScale, opts.minScore)
  if (torsoLen === null) return []

  const smoothFrames = framesFor(opts.smoothingWindowMs, intervalMs)
  const speed = smooth(
    rawWristSpeeds(frames, handedness, xScale, torsoLen, intervalMs, opts.minScore),
    smoothFrames,
  )
  // Signed horizontal wrist direction (forward vs backward), smoothed on the same
  // window. The min-peak-gap NMS only de-dups SAME-direction peaks, so a backswing
  // and the forward drive that follows it ~300ms later BOTH survive — "one stroke =
  // one backward + one forward move" instead of relying on the gap to pick one.
  const dirSign = smooth(rawWristDx(frames, handedness, xScale, intervalMs, opts.minScore), smoothFrames)
    .map(d => (d > 0 ? 1 : d < 0 ? -1 : 0))
  const peaks = findPeaks(
    speed,
    framesFor(opts.peakWindowRadiusMs, intervalMs),
    framesFor(opts.minPeakGapMs, intervalMs),
    opts.minPeakSpeed,
    dirSign,
  )
  const strokes: Stroke2D[] = peaks.map((p, idx) => {
    const floor = speed[p] * opts.boundaryFraction
    let start = p
    while (start > 0 && speed[start - 1] > floor) start--
    let end = p
    while (end < speed.length - 1 && speed[end + 1] > floor) end++
    return { strokeIndex: idx, startFrame: start, peakFrame: p, endFrame: end, peakSpeed: speed[p] }
  })
  // Valley-clamp: ensure adjacent strokes never overlap.
  for (let i = 0; i < strokes.length - 1; i++) {
    const a = strokes[i]
    const b = strokes[i + 1]
    if (a.endFrame >= b.startFrame) {
      let valley = a.peakFrame + 1
      for (let j = a.peakFrame + 1; j <= b.peakFrame; j++) {
        if (speed[j] < speed[valley]) valley = j
      }
      strokes[i] = { ...a, endFrame: valley }
      strokes[i + 1] = { ...b, startFrame: valley }
    }
  }
  return strokes
}

/** ms → frame count at the given interval, never below 1. */
function framesFor(ms: number, intervalMs: number): number {
  return Math.max(1, Math.floor(ms / intervalMs))
}

function rawWristSpeeds(
  frames: PoseFrame2D[],
  handedness: Handedness,
  xScale: number,
  torsoLen: number,
  intervalMs: number,
  minScore: number,
): number[] {
  const wristIdx = Coco17.wrist(handedness)
  const dtSec = intervalMs / 1000
  const raw = new Array<number>(frames.length).fill(0)
  for (let i = 1; i < frames.length; i++) {
    const prev = scored(frames[i - 1].keypoints, wristIdx, minScore)
    const curr = scored(frames[i].keypoints, wristIdx, minScore)
    raw[i] =
      prev === null || curr === null
        ? 0
        : Math.hypot((curr.x - prev.x) * xScale, curr.y - prev.y) / torsoLen / dtSec
  }
  return raw
}

/** Signed horizontal wrist displacement per frame (xScale-corrected); + and − mark
 *  the two swing directions. 0 when the wrist is gated at either end. */
function rawWristDx(
  frames: PoseFrame2D[],
  handedness: Handedness,
  xScale: number,
  intervalMs: number,
  minScore: number,
): number[] {
  const wristIdx = Coco17.wrist(handedness)
  const dtSec = intervalMs / 1000
  const raw = new Array<number>(frames.length).fill(0)
  for (let i = 1; i < frames.length; i++) {
    const prev = scored(frames[i - 1].keypoints, wristIdx, minScore)
    const curr = scored(frames[i].keypoints, wristIdx, minScore)
    raw[i] = prev === null || curr === null ? 0 : ((curr.x - prev.x) * xScale) / dtSec
  }
  return raw
}

/** Median xScale-corrected shoulder-mid→hip-mid distance; null if never measurable. */
function medianTorsoLength(frames: PoseFrame2D[], xScale: number, minScore: number): number | null {
  const lens: number[] = []
  for (const f of frames) {
    const kp = f.keypoints
    const ls = scored(kp, Coco17.LEFT_SHOULDER, minScore)
    const rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore)
    const lh = scored(kp, Coco17.LEFT_HIP, minScore)
    const rh = scored(kp, Coco17.RIGHT_HIP, minScore)
    if (ls === null || rs === null || lh === null || rh === null) continue
    const len = Math.hypot(
      ((ls.x + rs.x - (lh.x + rh.x)) / 2) * xScale,
      (ls.y + rs.y - (lh.y + rh.y)) / 2,
    )
    if (len >= MIN_TORSO_LEN) lens.push(len)
  }
  if (lens.length === 0) return null
  const sorted = lens.sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}

/** Centered box-average; an even window widens by one (window=2 behaves as 3). */
function smooth(raw: number[], window: number): number[] {
  if (window <= 1) return raw
  const half = Math.floor(window / 2)
  const out = new Array<number>(raw.length)
  for (let i = 0; i < raw.length; i++) {
    const lo = Math.max(0, i - half)
    const hi = Math.min(raw.length - 1, i + half)
    let sum = 0
    for (let j = lo; j <= hi; j++) sum += raw[j]
    out[i] = sum / (hi - lo + 1)
  }
  return out
}

/**
 * Local-maximum peak finding with keep-max NMS refractory: a candidate within
 * minGap of the previously admitted peak REPLACES it when taller, so a small
 * early bump cannot block a taller stroke peak.
 *
 * When `dirSign` is supplied, the refractory de-dup is DIRECTION-AWARE: it only
 * merges two peaks of the SAME swing direction. An opposite-direction peak within
 * minGap is always admitted, so a backswing and the forward drive ~300ms later both
 * survive (one stroke = one backward + one forward move) instead of the gap picking
 * the marginally-faster one (the L-27 shadow-play failure).
 */
function findPeaks(
  speed: number[],
  radius: number,
  minGap: number,
  minPeakSpeed: number,
  dirSign?: number[],
): number[] {
  const peaks: number[] = []
  for (let i = 0; i < speed.length; i++) {
    if (speed[i] < minPeakSpeed) continue
    const lo = Math.max(0, i - radius)
    const hi = Math.min(speed.length - 1, i + radius)
    let isPeak = true
    for (let j = lo; j <= hi; j++) {
      // strictly greater than earlier frames → first index of a plateau wins
      if (j < i && speed[j] >= speed[i]) { isPeak = false; break }
      if (j > i && speed[j] > speed[i]) { isPeak = false; break }
    }
    if (!isPeak) continue
    const last = peaks[peaks.length - 1]
    // Same-direction neighbours de-dup; opposite-direction ones never suppress each other.
    const sameDir = dirSign === undefined || dirSign[i] === dirSign[last]
    if (peaks.length === 0 || i - last >= minGap || !sameDir) {
      peaks.push(i)
    } else if (speed[i] > speed[last]) {
      peaks[peaks.length - 1] = i
    }
  }
  return peaks
}
