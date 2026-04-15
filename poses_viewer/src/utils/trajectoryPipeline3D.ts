/**
 * Trajectory Pipeline 3D — projects ball positions onto table surface via homography.
 *
 * Step 1: Detect bounces in 2D screen space (reuses V3's 3-point Y reversal)
 * Step 2: Project bounce frames through homography → accurate (x_cm, y_cm, z_cm=0)
 * Step 3: Project all frames → shadow positions + parabolic z interpolation
 */

import type { BallDetection, BallPosition3D, Bounce3D, Trajectory3DResult } from '../types'
import { screenToTable, type TableHomography } from './tableHomography'

// ── Constants ─────────────────────────────────────────────────────────────────

/** Minimum vy for a Y reversal to count as a real bounce (same as V3) */
const BOUNCE_VY_THRESHOLD = 0.003

/** Gravity in cm/s² */
const GRAVITY_CM = 981

// ── Types ─────────────────────────────────────────────────────────────────────

interface BallFrame {
  frameIndex: number
  x: number       // normalized screen x (0-1)
  y: number       // normalized screen y (0-1)
  confidence: number
}

interface ScreenBounce {
  frameIndex: number
  x: number       // normalized screen x
  y: number       // normalized screen y
}

// ── Bounce Detection (screen space) ───────────────────────────────────────────

/**
 * Detect table bounces using 3-point Y reversal in screen coordinates.
 *
 * A bounce is a bottom→top reversal: the ball moves downward (vy > 0 in screen space,
 * since Y increases downward), then reverses upward (vy < 0).
 *
 * This must happen in screen space, not cm space, because airborne ball positions
 * are distorted by parallax when projected through the homography.
 */
export function detectBouncesScreen(balls: BallFrame[]): ScreenBounce[] {
  if (balls.length < 3) return []

  const bounces: ScreenBounce[] = []

  for (let i = 1; i < balls.length - 1; i++) {
    const prev = balls[i - 1]
    const curr = balls[i]
    const next = balls[i + 1]

    // Skip non-consecutive frames (gap > 2 frames means missing data)
    if (curr.frameIndex - prev.frameIndex > 2 || next.frameIndex - curr.frameIndex > 2) {
      continue
    }

    const vyIn = curr.y - prev.y    // positive = moving down on screen
    const vyOut = next.y - curr.y   // negative = moving up on screen

    // Bottom→top reversal: going down then up, both above threshold
    if (vyIn > BOUNCE_VY_THRESHOLD && vyOut < -BOUNCE_VY_THRESHOLD) {
      bounces.push({
        frameIndex: curr.frameIndex,
        x: curr.x,
        y: curr.y,
      })
    }
  }

  return bounces
}

// ── Parabolic Z Estimation ────────────────────────────────────────────────────

/**
 * Estimate ball height (z_cm) at a given frame using parabolic interpolation
 * between consecutive bounces.
 *
 * Between two bounces at frames f0 and f1:
 *   z(t) = -½g·t² + v0·t  where t is time since f0, T = time(f1) - time(f0)
 *   z(0) = 0, z(T) = 0  →  v0 = ½g·T  →  z(t) = ½g·t·(T - t)
 *   Peak at t=T/2: z_peak = g·T²/8
 *
 * Before first bounce / after last bounce: extrapolate from nearest bounce pair,
 * clamped to 0 (ball can't go below table).
 */
function estimateZ(
  frameIndex: number,
  bounces: Bounce3D[],
  intervalMs: number,
): number {
  if (bounces.length === 0) return 0

  const frameSec = intervalMs / 1000

  // Find which bounce interval this frame falls in
  for (let i = 0; i < bounces.length - 1; i++) {
    const b0 = bounces[i]
    const b1 = bounces[i + 1]
    if (frameIndex >= b0.frameIndex && frameIndex <= b1.frameIndex) {
      const T = (b1.frameIndex - b0.frameIndex) * frameSec
      const t = (frameIndex - b0.frameIndex) * frameSec
      if (T <= 0) return 0
      // z(t) = ½g·t·(T - t)
      return Math.max(0, 0.5 * GRAVITY_CM * t * (T - t))
    }
  }

  // Before first bounce: mirror from first interval
  if (frameIndex < bounces[0].frameIndex) {
    if (bounces.length >= 2) {
      const T = (bounces[1].frameIndex - bounces[0].frameIndex) * frameSec
      const t = (bounces[0].frameIndex - frameIndex) * frameSec  // time before bounce
      if (T > 0) return Math.max(0, 0.5 * GRAVITY_CM * t * (T - t))
    }
    // Single bounce: assume ball is descending — simple free-fall to bounce
    const t = (bounces[0].frameIndex - frameIndex) * frameSec
    return Math.max(0, 0.5 * GRAVITY_CM * t * t)
  }

  // After last bounce: mirror from last interval
  const last = bounces[bounces.length - 1]
  if (bounces.length >= 2) {
    const prev = bounces[bounces.length - 2]
    const T = (last.frameIndex - prev.frameIndex) * frameSec
    const t = (frameIndex - last.frameIndex) * frameSec
    if (T > 0) return Math.max(0, 0.5 * GRAVITY_CM * t * (T - t))
  }
  // Single bounce: ball ascending after bounce
  const t = (frameIndex - last.frameIndex) * frameSec
  return Math.max(0, 0.5 * GRAVITY_CM * t * t)
}

// ── Main Pipeline ─────────────────────────────────────────────────────────────

/**
 * Compute 3D trajectory from ball detections + table homography.
 *
 * @param ballFrames - All ball detections up to current frame
 * @param homography - Table homography from marked corners
 * @param videoWidth - Video pixel width
 * @param videoHeight - Video pixel height
 */
export function computeTrajectory3D(
  ballFrames: { frameIndex: number; ball: BallDetection }[],
  homography: TableHomography,
  videoWidth: number,
  videoHeight: number,
  intervalMs: number,
): Trajectory3DResult {
  // Convert to BallFrame format
  const balls: BallFrame[] = ballFrames.map(f => ({
    frameIndex: f.frameIndex,
    x: f.ball.x,
    y: f.ball.y,
    confidence: f.ball.confidence,
  }))

  // Step 1: Detect bounces in screen space
  const screenBounces = detectBouncesScreen(balls)

  // Step 2: Project bounce frames through homography → accurate cm positions
  const bounces: Bounce3D[] = []
  for (const sb of screenBounces) {
    const tablePt = screenToTable(homography, sb.x, sb.y, videoWidth, videoHeight)
    if (tablePt) {
      bounces.push({
        frameIndex: sb.frameIndex,
        x_cm: tablePt.x_cm,
        y_cm: tablePt.y_cm,
        z_cm: 0,  // on table surface
      })
    }
  }

  // Step 3: Project all frames → shadow (x_cm, y_cm) + parabolic z_cm
  const bounceFrameSet = new Set(bounces.map(b => b.frameIndex))
  const positions: BallPosition3D[] = []

  for (const b of balls) {
    const tablePt = screenToTable(homography, b.x, b.y, videoWidth, videoHeight)
    const z_cm = estimateZ(b.frameIndex, bounces, intervalMs)
    positions.push({
      frameIndex: b.frameIndex,
      x_cm: tablePt?.x_cm ?? 0,
      y_cm: tablePt?.y_cm ?? 0,
      z_cm,
      screenX: b.x,
      screenY: b.y,
      confidence: bounceFrameSet.has(b.frameIndex) ? b.confidence : b.confidence * 0.7,
    })
  }

  return { positions, bounces }
}
