/**
 * Trajectory Pipeline V3 — enhanced prediction with cubic Y, spin classification,
 * damped drag, and bounce simulation.
 *
 * Improvements over V2:
 * 1. Cubic Y fit (5+ points) — captures topspin/backspin arc asymmetry via dy·t³
 * 2. Damped horizontal drag — re-enables cx with loop-back guard
 * 3. Spin classification from cubic dy — adjusts bounce restitution per spin type
 * 4. Table surface model using radiusPx as depth proxy (closer ball = larger radius = higher table Y)
 * 5. Bounce simulation with spin-dependent physics
 */

import type { Frame } from '../types'
import { fitLinear, fitQuadratic, fitCubic } from './trajectoryPipeline'

// ── Types ──────────────────────────────────────────────────────────────────────

export interface ParabolicFitV3 {
  ax: number; bx: number; cx: number   // x(t) = ax + bx·t + cx·t²
  ay: number; by: number; cy: number   // y(t) = ay + by·t + cy·t² + dy·t³
  dy: number                            // cubic Y term (0 when ≤4 points)
}

export type SpinClass = 'topspin' | 'backspin' | 'flat'

export interface FittedPositionV3 {
  frameIndex: number
  x: number
  y: number
  source: 'DETECTED' | 'INTERPOLATED'
}

export interface TableSurfaceModel {
  /** Observed bounce points in screen coordinates */
  bouncePoints: { x: number; y: number; radiusPx: number; frameIndex: number }[]
  /** Linear model: tableY(x) = slope·x + intercept.
   *  Fitted through screen (x,y) of bounce points — captures camera perspective angle. */
  slope: number
  intercept: number
  /** Estimated table X boundaries (from observed bounces + margin) */
  xMin: number
  xMax: number
  /** Whether the model has enough data (≥1 bounce observed) */
  isValid: boolean
}

export interface PredictedBounce {
  x: number
  y: number
  /** Frames from current frame to this bounce */
  dt: number
}

export interface PredictiveTrajectoryV3 {
  pastPositions: FittedPositionV3[]
  /** Past positions split by table bounce segments — each segment is a separate curve.
   *  Rendering should draw each segment independently for sharp V-shapes at bounces. */
  pastSegments: FittedPositionV3[][]
  predictedPositions: FittedPositionV3[]
  fit: ParabolicFitV3
  segmentStartFrame: number
  detectionCount: number
  tableSurface: TableSurfaceModel
  predictedBounces: PredictedBounce[]
  spinClass: SpinClass
}

// ── Constants ──────────────────────────────────────────────────────────────────

/** Minimum vy for a Y reversal to count as a real bounce */
const BOUNCE_VY_THRESHOLD = 0.003

/** Minimum vx delta for an X reversal (paddle contact) */
const X_REVERSAL_THRESHOLD = 0.01

/** Minimum angle (degrees) between consecutive velocity vectors to trigger a reversal.
 *  Catches oblique direction changes that pure X/Y component checks miss. */
const REVERSAL_ANGLE_THRESHOLD = 45

/** Minimum speed for reversal detection — filters noise at near-zero velocity */
const MIN_REVERSAL_SPEED = 0.005

/** Maximum frame gap between consecutive detections before splitting arc.
 *  If the ball isn't seen for this many frames, something happened — new trajectory. */
const MAX_FRAME_GAP = 3

/** Maximum detections per arc — trim old points beyond this (sliding window) */
const MAX_ARC_LENGTH = 15

/** Floor for prediction gravity.
 *  At 10fps, fitted cy underestimates real gravity (too few samples for curvature).
 *  Real table tennis: bounce rises ~3-4% of screen. With MAX_BOUNCE_VY=0.03 and
 *  gravity=0.015, apex at ~1 frame, rise ~0.03 (3%). */
const MIN_GRAVITY = 0.015

/** Maximum upward velocity after a bounce.
 *  Table tennis bounce is low — ball rises 20-30cm above table (76cm high).
 *  In normalized coords: ~0.03-0.04 screen height rise. */
const MAX_BOUNCE_VY = 0.03

/** Minimum ball speed (per frame, normalized) to generate a trajectory.
 *  Below this the ball is essentially stationary — no trajectory shown. */
const MIN_BALL_SPEED = 0.004

/** Maximum bounces to simulate in prediction (1 = stop after first table contact) */
const MAX_PREDICTION_BOUNCES = 1

/** Minimum per-frame X velocity change (normalized) to classify as racket contact.
 *  Table bounces barely change vx (~0.01-0.02). Racket hits produce vx jumps of 0.04+. */
const RACKET_X_ACCEL_THRESHOLD = 0.035

/** Minimum |dy| to classify as topspin/backspin (needs tuning with real data) */
const SPIN_DY_THRESHOLD = 0.00005

/** Bounce physics per spin class */
const BOUNCE_PARAMS: Record<SpinClass, { restitution: number; frictionFactor: number }> = {
  topspin:  { restitution: 0.75, frictionFactor: 1.05 },  // lower bounce, faster forward
  backspin: { restitution: 0.90, frictionFactor: 0.80 },  // higher bounce, slower forward
  flat:     { restitution: 0.85, frictionFactor: 0.95 },  // neutral
}

// ── Fitting (cubic Y for 5+ points) ────────────────────────────────────────────

/**
 * Fit trajectory with cubic Y for 5+ points.
 * - X: quadratic (captures drag / side spin)
 * - Y: cubic when 5+ points (captures spin-induced arc asymmetry), quadratic otherwise
 */
export function fitTrajectoryV3(
  detections: { x: number; y: number; frameIndex: number }[],
): ParabolicFitV3 | null {
  if (detections.length < 2) return null

  const t0 = detections[0].frameIndex
  const ts = detections.map(d => d.frameIndex - t0)
  const xs = detections.map(d => d.x)
  const ys = detections.map(d => d.y)

  // X: always quadratic
  let ax: number, bx: number, cx: number
  if (detections.length <= 2) {
    [ax, bx] = fitLinear(ts, xs)
    cx = 0
  } else {
    [ax, bx, cx] = fitQuadratic(ts, xs)
  }

  // Y: cubic for 5+ points (dy captures topspin/backspin asymmetry)
  let ay: number, by: number, cy: number, dy: number
  if (detections.length <= 2) {
    [ay, by] = fitLinear(ts, ys)
    cy = 0; dy = 0
  } else if (detections.length <= 4) {
    [ay, by, cy] = fitQuadratic(ts, ys)
    dy = 0
  } else {
    [ay, by, cy, dy] = fitCubic(ts, ys)
  }

  return { ax, bx, cx, ay, by, cy, dy }
}

/** Evaluate the fitted model at frame offset t (includes cubic dy term). */
export function evaluateFitV3(fit: ParabolicFitV3, t: number): { x: number; y: number } {
  return {
    x: fit.ax + fit.bx * t + fit.cx * t * t,
    y: fit.ay + fit.by * t + fit.cy * t * t + fit.dy * t * t * t,
  }
}

/** RMS deviation of detections from fitted model (cubic-aware). */
export function rmsErrorV3(
  fit: ParabolicFitV3,
  detections: { x: number; y: number; frameIndex: number }[],
): number {
  if (detections.length === 0) return 0
  const t0 = detections[0].frameIndex
  let sumSq = 0
  for (const d of detections) {
    const t = d.frameIndex - t0
    const pos = evaluateFitV3(fit, t)
    const dx = d.x - pos.x
    const dy = d.y - pos.y
    sumSq += dx * dx + dy * dy
  }
  return Math.sqrt(sumSq / detections.length)
}

// ── Spin Classification ────────────────────────────────────────────────────────

/**
 * Classify spin from the cubic Y coefficient.
 * dy > 0: descent steeper than ascent → topspin (ball dips faster)
 * dy < 0: ascent steeper than descent → backspin (ball floats longer)
 */
export function classifySpin(dy: number): SpinClass {
  if (dy > SPIN_DY_THRESHOLD) return 'topspin'
  if (dy < -SPIN_DY_THRESHOLD) return 'backspin'
  return 'flat'
}

// ── Damped Horizontal Drag ─────────────────────────────────────────────────────

// ── Table Surface Model ────────────────────────────────────────────────────────

interface DetectedBall {
  x: number; y: number; radiusPx: number; frameIndex: number; timestampMs: number
}

/**
 * Build a table surface model from observed bottom→top Y reversals.
 * Fits a line through screen (x, y) of bounce points: tableY(x) = slope·x + intercept.
 * This directly captures the camera viewing angle — the table line will be angled
 * if the camera is at an angle (e.g. 45°), not strictly horizontal.
 */
function buildTableSurfaceModel(detections: DetectedBall[]): TableSurfaceModel {
  const bouncePoints: { x: number; y: number; radiusPx: number; frameIndex: number }[] = []

  if (detections.length >= 3) {
    for (let i = 1; i < detections.length - 1; i++) {
      const prev = detections[i - 1]
      const curr = detections[i]
      const next = detections[i + 1]
      const vyIn = curr.y - prev.y
      const vyOut = next.y - curr.y

      // Bottom→top reversal: ball going down (vy > 0), then up (vy < 0)
      if (vyIn > BOUNCE_VY_THRESHOLD && vyOut < -BOUNCE_VY_THRESHOLD) {
        bouncePoints.push({ x: curr.x, y: curr.y, radiusPx: curr.radiusPx, frameIndex: curr.frameIndex })
      }
    }
  }

  if (bouncePoints.length === 0) {
    return { bouncePoints, slope: 0, intercept: 0.9, xMin: 0, xMax: 1, isValid: false }
  }

  // Estimate table X boundaries from bounce positions.
  // Bounces happen on the table, so the table extends at least from min to max bounce X.
  // Add margin: table is roughly twice the span of observed bounces (bounces cluster in middle).
  const xs = bouncePoints.map(p => p.x)
  const rawMin = Math.min(...xs)
  const rawMax = Math.max(...xs)
  const span = rawMax - rawMin
  const margin = Math.max(span * 0.3, 0.05)  // at least 5% of screen width
  const xMin = Math.max(0, rawMin - margin)
  const xMax = Math.min(1, rawMax + margin)

  if (bouncePoints.length === 1) {
    // Single bounce — flat model at that Y, estimate table width ~30% of screen
    return { bouncePoints, slope: 0, intercept: bouncePoints[0].y,
      xMin: Math.max(0, bouncePoints[0].x - 0.15),
      xMax: Math.min(1, bouncePoints[0].x + 0.15),
      isValid: true }
  }

  // 2+ bounces: fit tableY(x) = slope·x + intercept through screen coordinates.
  // The slope captures the camera perspective angle.
  const n = bouncePoints.length
  const sumX = bouncePoints.reduce((s, p) => s + p.x, 0)
  const sumY = bouncePoints.reduce((s, p) => s + p.y, 0)
  const sumXY = bouncePoints.reduce((s, p) => s + p.x * p.y, 0)
  const sumX2 = bouncePoints.reduce((s, p) => s + p.x * p.x, 0)

  const denom = n * sumX2 - sumX * sumX
  if (Math.abs(denom) < 1e-12) {
    const ys = bouncePoints.map(p => p.y).sort((a, b) => a - b)
    return { bouncePoints, slope: 0, intercept: ys[Math.floor(ys.length / 2)], xMin, xMax, isValid: true }
  }

  const slope = (n * sumXY - sumX * sumY) / denom
  const intercept = (sumY * sumX2 - sumX * sumXY) / denom
  return { bouncePoints, slope, intercept, xMin, xMax, isValid: true }
}

/** Estimated table Y at a given screen X coordinate (perspective-aware) */
function getTableY(model: TableSurfaceModel, x: number): number {
  return model.slope * x + model.intercept
}

// ── Predict Trajectory V3 ──────────────────────────────────────────────────────

/**
 * Predict ball trajectory at a given frame using only past data.
 *
 * V3 differences from V1/V2:
 * - Cubic Y fit captures spin asymmetry (dy·t³) in past arc
 * - Prediction uses current velocity (including cubic derivative) but extrapolates
 *   with quadratic Y only (cubic diverges too fast for extrapolation)
 * - Horizontal drag (cx) is damped to prevent loop-back, not dropped entirely
 * - Bounce simulation uses spin-dependent restitution
 */
export function predictTrajectoryV3(
  frames: Frame[],
  currentFrame: number,
  intervalMs: number,
  predictionFrames: number = 10,
  contacts?: { frameIndex: number; type: string }[],
  videoSize?: { width: number; height: number },
): PredictiveTrajectoryV3 | null {
  // Extract detected balls up to current frame
  const pastDetected: DetectedBall[] = frames
    .filter(f => {
      if (f.frameIndex > currentFrame) return false
      if (!f.ball) return false
      if (f.ball.confidence < 0.1) return false
      return true
    })
    .map(f => ({
      x: f.ball!.x,
      y: f.ball!.y,
      radiusPx: f.ball!.radiusPx,
      frameIndex: f.frameIndex,
      timestampMs: f.timestampMs,
    }))

  if (pastDetected.length < 2) return null

  // Build table surface model from ALL past detections (not just current arc)
  const tableSurface = buildTableSurfaceModel(pastDetected)

  // ── Arc splitting ────────────────────────────────────────────────────────
  // Track events with type: 'split' = hard break, 'table' = table bounce (1 allowed)

  const events: { index: number; type: 'split' | 'table' }[] = []

  // Frame gap detection: hard split
  for (let i = 0; i < pastDetected.length - 1; i++) {
    const gap = pastDetected[i + 1].frameIndex - pastDetected[i].frameIndex
    if (gap > MAX_FRAME_GAP) {
      events.push({ index: i + 1, type: 'split' })
    }
  }

  // Direction reversal detection (angle-based + component-based)
  if (pastDetected.length >= 3) {
    for (let i = 1; i < pastDetected.length - 1; i++) {
      const prev = pastDetected[i - 1]
      const curr = pastDetected[i]
      const next = pastDetected[i + 1]
      const vxIn = curr.x - prev.x
      const vyIn = curr.y - prev.y
      const vxOut = next.x - curr.x
      const vyOut = next.y - curr.y

      const speedIn = Math.sqrt(vxIn * vxIn + vyIn * vyIn)
      const speedOut = Math.sqrt(vxOut * vxOut + vyOut * vyOut)

      if (speedIn < MIN_REVERSAL_SPEED || speedOut < MIN_REVERSAL_SPEED) continue

      const dot = vxIn * vxOut + vyIn * vyOut
      const cosAngle = Math.max(-1, Math.min(1, dot / (speedIn * speedOut)))
      const angleDeg = Math.acos(cosAngle) * 180 / Math.PI

      const isGravityApex = vyIn < 0 && vyOut > 0 &&
        (Math.sign(vxIn) === Math.sign(vxOut) || Math.max(Math.abs(vxIn), Math.abs(vxOut)) < X_REVERSAL_THRESHOLD)

      // Detect table bounces: ball going down → up near table surface.
      // Critical: table bounce NEVER reverses X direction. If X reverses,
      // it's a racket hit regardless of Y behavior or proximity to table.
      const isNearTable = tableSurface.isValid &&
        Math.abs(curr.y - getTableY(tableSurface, curr.x)) < 0.05
      const xReversed = (vxIn > X_REVERSAL_THRESHOLD && vxOut < -X_REVERSAL_THRESHOLD) ||
        (vxIn < -X_REVERSAL_THRESHOLD && vxOut > X_REVERSAL_THRESHOLD)
      const isAngleTableBounce = vyIn > 0 && vyOut < 0 && isNearTable && !xReversed

      if (angleDeg > REVERSAL_ANGLE_THRESHOLD && !isGravityApex) {
        // For splits (racket contacts): new arc starts at i+1, the first post-contact frame.
        // Detection at i is still in the contact zone. i+1 is the clean outgoing ball.
        events.push({ index: isAngleTableBounce ? i : i + 1, type: isAngleTableBounce ? 'table' : 'split' })
        continue
      }

      // Rapid X velocity change = racket contact (catches hits where angle < 45°
      // but ball speed changes dramatically, e.g. acceleration in same direction)
      const xAccel = Math.abs(vxOut - vxIn)
      if (xAccel > RACKET_X_ACCEL_THRESHOLD && !isAngleTableBounce) {
        events.push({ index: i + 1, type: 'split' })
        continue
      }

      const xReversal =
        (vxIn > X_REVERSAL_THRESHOLD && vxOut < -X_REVERSAL_THRESHOLD) ||
        (vxIn < -X_REVERSAL_THRESHOLD && vxOut > X_REVERSAL_THRESHOLD)

      const bottomToTop = vyIn > BOUNCE_VY_THRESHOLD && vyOut < -BOUNCE_VY_THRESHOLD
      const isTableBounce = bottomToTop && isNearTable && !xReversed

      if (isTableBounce) {
        events.push({ index: i, type: 'table' })
      } else if (xReversal || bottomToTop) {
        events.push({ index: i + 1, type: 'split' })  // start after contact zone
      }
    }
  }

  // Contact-based splitting: racket contacts = hard split
  if (contacts && contacts.length > 0) {
    for (const c of contacts) {
      if (c.frameIndex > currentFrame) break
      const detIdx = pastDetected.findIndex(d => d.frameIndex >= c.frameIndex)
      if (detIdx >= 0) {
        events.push({ index: detIdx, type: c.type === 'racket' ? 'split' : 'table' })
      }
    }
  }

  // Sort events by index, deduplicate
  events.sort((a, b) => a.index - b.index)
  const seen = new Set<number>()
  const uniqueEvents = events.filter(e => {
    if (seen.has(e.index)) return false
    seen.add(e.index)
    return true
  })

  // Walk backward: allow max 1 table bounce, hard splits always break
  let arcStartIdx = 0
  let tableBouncesSeen = 0
  for (let j = uniqueEvents.length - 1; j >= 0; j--) {
    const ev = uniqueEvents[j]
    if (ev.type === 'split') {
      arcStartIdx = ev.index
      break
    }
    if (ev.type === 'table') {
      tableBouncesSeen++
      if (tableBouncesSeen > 1) {
        arcStartIdx = ev.index
        break
      }
    }
  }

  let currentArc = pastDetected.slice(arcStartIdx)
  if (currentArc.length < 2) return null

  // Sliding window: trim old detections if arc is too long (prevents fit degradation)
  if (currentArc.length > MAX_ARC_LENGTH) {
    currentArc = currentArc.slice(currentArc.length - MAX_ARC_LENGTH)
  }

  // ── Ball-diameter split rule ────────────────────────────────────────────
  // After 3+ detections: fit through all-but-last, predict where ball should
  // be. If actual position is more than 1 ball diameter away → contact
  // assumed → new arc. Table contacts are excluded (ball continues same
  // horizontal direction after hitting table).
  let fit: ReturnType<typeof fitTrajectoryV3> = null
  const imgW = videoSize?.width ?? 1920
  const imgH = videoSize?.height ?? 1080

  if (currentArc.length >= 3) {
    const preArc = currentArc.slice(0, -1)
    const preFit = fitTrajectoryV3(preArc)
    if (preFit) {
      const last = currentArc[currentArc.length - 1]
      const t = last.frameIndex - preArc[0].frameIndex
      const pred = evaluateFitV3(preFit, t)
      // Distance in normalized coords (account for aspect ratio)
      const dx = (last.x - pred.x) * imgW
      const dy = (last.y - pred.y) * imgH
      const distPx = Math.sqrt(dx * dx + dy * dy)
      const ballDiameter = 2 * last.radiusPx

      if (distPx > ballDiameter) {
        // Check if this is a table contact (same horizontal direction = table bounce)
        const prev = preArc[preArc.length - 1]
        const vxBefore = prev.x - preArc[Math.max(0, preArc.length - 2)].x
        const vxAfter = last.x - prev.x
        const sameXDirection = Math.sign(vxBefore) === Math.sign(vxAfter) || Math.abs(vxAfter) < 0.005

        if (!sameXDirection) {
          // Direction changed — racket contact, start new arc
          currentArc = currentArc.slice(-2)
        }
        // If same direction — table contact, keep in arc
      }
    }
  }

  // ── Split arc into segments at table bounces ──────────────────────────────
  // Each segment gets its own fit. This keeps bounces sharp (V-shape)
  // instead of smoothed by a single curve through everything.

  // Find table bounce points within currentArc.
  // Any down→up Y reversal is a table bounce — no table model dependency.
  // The Y reversal pattern (vyIn > 0, vyOut < 0) is sufficient: gravity apex
  // is up→down (opposite signs), so false positives are structurally impossible.
  const tableBounceLocalIndices: number[] = []
  if (currentArc.length >= 3) {
    for (let i = 1; i < currentArc.length - 1; i++) {
      const prev = currentArc[i - 1]
      const curr = currentArc[i]
      const next = currentArc[i + 1]
      const vyIn = curr.y - prev.y
      const vyOut = next.y - curr.y
      if (vyIn > BOUNCE_VY_THRESHOLD && vyOut < -BOUNCE_VY_THRESHOLD) {
        // Confirm X direction doesn't reverse (table bounce preserves X direction)
        const vxIn = curr.x - prev.x
        const vxOut = next.x - curr.x
        const xFlipped = (vxIn > X_REVERSAL_THRESHOLD && vxOut < -X_REVERSAL_THRESHOLD) ||
          (vxIn < -X_REVERSAL_THRESHOLD && vxOut > X_REVERSAL_THRESHOLD)
        if (!xFlipped) {
          tableBounceLocalIndices.push(i)
        }
      }
    }
  }

  // Build segments: split at table bounces (bounce point shared between segments)
  const segments: DetectedBall[][] = []
  let segStart = 0
  for (const bi of tableBounceLocalIndices) {
    segments.push(currentArc.slice(segStart, bi + 1))  // pre-bounce includes bounce point
    segStart = bi  // post-bounce starts at bounce point
  }
  segments.push(currentArc.slice(segStart))  // last segment

  // Fit each segment, use last segment for prediction
  const segmentFits: { seg: DetectedBall[]; fit: ParabolicFitV3 }[] = []
  for (const seg of segments) {
    if (seg.length >= 2) {
      const segFit = fitTrajectoryV3(seg)
      if (segFit) segmentFits.push({ seg, fit: segFit })
    }
  }

  if (segmentFits.length === 0) return null
  const lastSegment = segmentFits[segmentFits.length - 1]
  fit = lastSegment.fit

  // Classify spin from last segment's cubic coefficient
  const spinClass = classifySpin(fit.dy)
  const bounceParams = BOUNCE_PARAMS[spinClass]

  const t0 = currentArc[0].frameIndex

  // ── Past positions: each segment uses its own fit ─────────────────────────
  // Build both flat pastPositions and segmented pastSegments for rendering.

  const pastPositions: FittedPositionV3[] = []
  const pastSegments: FittedPositionV3[][] = []

  for (const { seg, fit: segFit } of segmentFits) {
    const segT0 = seg[0].frameIndex
    const segTEnd = seg[seg.length - 1].frameIndex
    const segDetByFrame = new Map(seg.map(d => [d.frameIndex, d]))
    const segPositions: FittedPositionV3[] = []

    // Only draw up to current frame
    const endFrame = Math.min(segTEnd, currentFrame)
    for (let fi = segT0; fi <= endFrame; fi++) {
      const det = segDetByFrame.get(fi)
      let pos: FittedPositionV3
      if (det) {
        pos = { frameIndex: fi, x: det.x, y: det.y, source: 'DETECTED' }
      } else {
        const t = fi - segT0
        const p = evaluateFitV3(segFit, t)
        pos = {
          frameIndex: fi,
          x: Math.max(0, Math.min(1, p.x)),
          y: Math.max(0, Math.min(1, p.y)),
          source: 'INTERPOLATED',
        }
      }
      segPositions.push(pos)

      // Flat array: skip duplicates at segment boundaries
      if (pastPositions.length === 0 || pastPositions[pastPositions.length - 1].frameIndex < fi) {
        pastPositions.push(pos)
      }
    }

    if (segPositions.length > 0) {
      pastSegments.push(segPositions)
    }
  }

  // ── Prediction: average velocity from last segment + gravity + bounce ─────

  const lastSeg = lastSegment.seg
  const lastSegT0 = lastSeg[0].frameIndex
  const tCurr = currentFrame - lastSegT0
  const posCurr = evaluateFitV3(fit, tCurr)
  let xNow = posCurr.x
  let yNow = posCurr.y

  // Compute prediction velocity.
  // When last segment is short (< 3 pts after bounce), inherit velocity from
  // the pre-bounce segment: reflect vy, attenuate by bounce physics.
  // This gives a physically meaningful prediction right after a table bounce.
  let vxNow: number, vyNow: number

  if (lastSeg.length < 3 && segmentFits.length >= 2) {
    // Short post-bounce segment — use pre-bounce velocity (reflected + attenuated)
    const preBounce = segmentFits[segmentFits.length - 2]
    const preSeg = preBounce.seg
    const preCount = Math.min(preSeg.length, 4)
    const preDets = preSeg.slice(-preCount)
    if (preDets.length >= 2) {
      const first = preDets[0]
      const last = preDets[preDets.length - 1]
      const fd = last.frameIndex - first.frameIndex
      if (fd > 0) {
        vxNow = ((last.x - first.x) / fd) * bounceParams.frictionFactor
        // Reflect vy: pre-bounce was going down (positive), post-bounce goes up (negative)
        vyNow = -Math.abs((last.y - first.y) / fd) * bounceParams.restitution
        // Cap upward velocity
        if (vyNow < -MAX_BOUNCE_VY) vyNow = -MAX_BOUNCE_VY
      } else {
        vxNow = fit.bx + 2 * fit.cx * tCurr
        vyNow = fit.by + 2 * fit.cy * tCurr
      }
    } else {
      vxNow = fit.bx + 2 * fit.cx * tCurr
      vyNow = fit.by + 2 * fit.cy * tCurr
    }
  } else {
    // Normal case: average velocity from last segment's detections
    const recentCount = Math.min(lastSeg.length, 4)
    const recentDets = lastSeg.slice(-recentCount)
    if (recentDets.length >= 2) {
      const first = recentDets[0]
      const last = recentDets[recentDets.length - 1]
      const frameDiff = last.frameIndex - first.frameIndex
      if (frameDiff > 0) {
        vxNow = (last.x - first.x) / frameDiff
        vyNow = (last.y - first.y) / frameDiff
      } else {
        vxNow = fit.bx + 2 * fit.cx * tCurr
        vyNow = fit.by + 2 * fit.cy * tCurr
      }
    } else {
      vxNow = fit.bx + 2 * fit.cx * tCurr
      vyNow = fit.by + 2 * fit.cy * tCurr
    }
  }

  // Skip trajectory if ball is essentially stationary
  const speed = Math.sqrt(vxNow * vxNow + vyNow * vyNow)
  if (speed < MIN_BALL_SPEED) return null

  const gravity = Math.max(fit.cy, MIN_GRAVITY)

  const predictedPositions: FittedPositionV3[] = []
  const predictedBounces: PredictedBounce[] = []
  let bounceCount = 0

  for (let fi = currentFrame + 1; fi <= currentFrame + predictionFrames; fi++) {
    const dt = 1  // single frame step for accurate bounce detection

    // Advance physics: LINEAR X (no drag), quadratic Y (gravity only)
    const xNext = xNow + vxNow * dt
    let yNext = yNow + vyNow * dt + gravity * dt * dt
    let vxNext = vxNow                             // constant vx (unless bounce)
    let vyNext = vyNow + 2 * gravity * dt         // vy accelerates under gravity

    // Bounce simulation: only within table X boundaries
    if (tableSurface.isValid && bounceCount < MAX_PREDICTION_BOUNCES) {
      const onTable = xNext >= tableSurface.xMin && xNext <= tableSurface.xMax
      if (onTable) {
        const tableY = getTableY(tableSurface, xNext)
        if (yNext >= tableY && vyNext > 0) {
          // Reflect off table with spin-dependent physics
          yNext = tableY - (yNext - tableY) * bounceParams.restitution
          vyNext = -Math.abs(vyNext) * bounceParams.restitution
          // Cap upward velocity — prevents unrealistically high bounces
          if (vyNext < -MAX_BOUNCE_VY) vyNext = -MAX_BOUNCE_VY
          vxNext = vxNext * bounceParams.frictionFactor
          bounceCount++
          predictedBounces.push({ x: xNext, y: tableY, dt: fi - currentFrame })
          // Stop prediction after the bounce contact point
          predictedPositions.push({
            frameIndex: fi,
            x: Math.max(0, Math.min(1, xNext)),
            y: Math.max(0, Math.min(1, tableY)),
            source: 'INTERPOLATED',
          })
          break
        }
      }
    }

    // Stop if ball leaves reasonable bounds
    if (xNext < -0.1 || xNext > 1.1 || yNext < -0.1 || yNext > 1.1) break

    predictedPositions.push({
      frameIndex: fi,
      x: Math.max(0, Math.min(1, xNext)),
      y: Math.max(0, Math.min(1, yNext)),
      source: 'INTERPOLATED',
    })

    // Carry state forward
    xNow = xNext
    yNow = yNext
    vxNow = vxNext
    vyNow = vyNext
  }

  return {
    pastPositions,
    pastSegments,
    predictedPositions,
    fit,
    segmentStartFrame: t0,
    detectionCount: currentArc.length,
    tableSurface,
    predictedBounces,
    spinClass,
  }
}
