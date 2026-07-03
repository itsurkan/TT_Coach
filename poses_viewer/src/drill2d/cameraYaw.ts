/**
 * Side-view yaw from 2D foreshortening — 1:1 TS mirror of Kotlin
 * CameraAngleEstimator. In a true profile the shoulders overlap; the wider they
 * appear relative to torso length, the further the camera is from perpendicular.
 * Returns |yaw| only (foreshortening can't recover the sign; the 1/cos correction
 * is sign-independent). Takes raw aspectRatio — runs BEFORE any yaw is known.
 *
 * Kotlin is the source of truth (binding fix-flow rule). NOTE: on Videos/ footage
 * (not shot to the camera-placement protocol) this saturates toward 90° (L-25) —
 * that is expected, not a port bug; the UI offers a manual override.
 */
import { Coco17, Keypoint2D, PoseFrame2D, Stroke2D } from './types'
import { DEFAULT_MIN_SCORE, scored } from './facing'
import { median } from './median'

/** Biacromial width ≈ 0.9 × shoulder–hip torso length (Drillis & Contini 1966). */
export const SHOULDER_TO_TORSO_RATIO = 0.9
export const DEFAULT_SAMPLE_FRAMES = 30
/** Pre-stroke ready-stance window in ms — fps-independent (L-02). */
export const DEFAULT_LOOKBACK_MS = 1000

const RAD_TO_DEG = 180 / Math.PI
const MIN_TORSO_LEN = 1e-4

function frameYawDeg(kp: Keypoint2D[], aspectRatio: number, minScore: number): number | null {
  const ls = scored(kp, Coco17.LEFT_SHOULDER, minScore)
  const rs = scored(kp, Coco17.RIGHT_SHOULDER, minScore)
  const lh = scored(kp, Coco17.LEFT_HIP, minScore)
  const rh = scored(kp, Coco17.RIGHT_HIP, minScore)
  if (ls === null || rs === null || lh === null || rh === null) return null

  const torsoLen = Math.hypot(
    ((ls.x + rs.x) / 2 - (lh.x + rh.x) / 2) * aspectRatio,
    (ls.y + rs.y) / 2 - (lh.y + rh.y) / 2,
  )
  if (torsoLen < MIN_TORSO_LEN) return null

  const shoulderSepX = Math.abs(rs.x - ls.x) * aspectRatio
  const sinYaw = Math.min(1, Math.max(0, shoulderSepX / (SHOULDER_TO_TORSO_RATIO * torsoLen)))
  return Math.asin(sinYaw) * RAD_TO_DEG
}

/** Median per-frame yaw over the first sampleFrames frames with a person. Null if none qualify. */
export function estimateSideViewYawDeg(
  frames: PoseFrame2D[],
  aspectRatio: number,
  minScore = DEFAULT_MIN_SCORE,
  sampleFrames = DEFAULT_SAMPLE_FRAMES,
): number | null {
  const perFrame: number[] = []
  for (const f of frames) {
    if (f.keypoints.length === 0) continue
    if (perFrame.length >= sampleFrames) break
    const y = frameYawDeg(f.keypoints, aspectRatio, minScore)
    if (y !== null) perFrame.push(y)
  }
  if (perFrame.length === 0) return null
  return median(perFrame)
}

/**
 * Per-rep yaw from the lookback window immediately BEFORE the stroke (estimating
 * during the swing confounds the player's own rotation with camera placement).
 * Falls back to the stroke's own window when there is no lookback.
 */
export function estimateYawForStroke(
  frames: PoseFrame2D[],
  stroke: Stroke2D,
  aspectRatio: number,
  intervalMs: number,
  minScore = DEFAULT_MIN_SCORE,
  lookbackMs = DEFAULT_LOOKBACK_MS,
): number | null {
  if (intervalMs <= 0) throw new Error(`intervalMs must be > 0, got ${intervalMs}`)
  const lookbackFrames = Math.max(1, Math.trunc(lookbackMs / intervalMs))
  const until = Math.min(Math.max(stroke.startFrame, 0), frames.length)
  const from = Math.max(until - lookbackFrames, 0)
  const pre = estimateSideViewYawDeg(frames.slice(from, until), aspectRatio, minScore)
  if (pre !== null) return pre
  const strokeEnd = Math.min(Math.max(stroke.endFrame + 1, 0), frames.length)
  if (until >= strokeEnd) return null
  return estimateSideViewYawDeg(frames.slice(until, strokeEnd), aspectRatio, minScore)
}
