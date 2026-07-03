/**
 * Trajectory Pipeline V2 — improved prediction with bounce simulation.
 *
 * Improvements over V1:
 * 1. Table surface model: learns table Y from observed bounces (perspective-aware via linear fit)
 * 2. Bounce simulation: predicted trajectory reflects off estimated table surface
 * 3. Horizontal drag in prediction: re-enables cx·dt² term that V1 drops
 */

import type { Frame } from '../types'
import { fitTrajectory, evaluateFit, type ParabolicFit, type FittedPosition } from './trajectoryPipeline'

// ── Types ──────────────────────────────────────────────────────────────────────

export interface TableSurfaceModel {
  /** Observed bounce points (x, y) in normalized coordinates */
  bouncePoints: { x: number; y: number; frameIndex: number }[]
  /** Linear model: tableY(x) = slope * x + intercept. null if no bounces observed */
  slope: number
  intercept: number
  /** Whether the model has enough data to be useful */
  isValid: boolean
  /** Estimated table X extent (from observed bounces + margin) */
  tableXMin: number
  tableXMax: number
}

export interface PredictiveTrajectoryV2 {
  /** Fitted positions from segment start to current frame */
  pastPositions: FittedPosition[]
  /** Extrapolated positions from current frame forward (with bounce simulation) */
  predictedPositions: FittedPosition[]
  /** The parabolic fit used */
  fit: ParabolicFit
  /** Frame where the current arc started */
  segmentStartFrame: number
  /** Number of detections used for the fit */
  detectionCount: number
  /** Table surface model (learned from observed bounces) */
  tableSurface: TableSurfaceModel
  /** Predicted bounce points during extrapolation */
  predictedBounces: { x: number; y: number; dt: number }[]
}

// ── Table Surface Model ────────────────────────────────────────────────────────

/** Minimum speed for a Y reversal to count as a real bounce */
const BOUNCE_VY_THRESHOLD = 0.003

/** Coefficient of restitution for bounce simulation (0.5 = realistic table tennis) */
const RESTITUTION = 0.5

/** Horizontal speed retention after bounce (friction with table surface) */
const BOUNCE_VX_DAMPING = 0.85

/** Maximum bounces to simulate in prediction window */
const MAX_PREDICTION_BOUNCES = 3

/** Minimum gravity for prediction (0.005 prevents upward-drifting predictions at 10fps) */
const MIN_GRAVITY = 0.005

interface DetectedBall {
  x: number; y: number; frameIndex: number; timestampMs: number
}

// ── Arc Analysis ──────────────────────────────────────────────────────────────

export type ArcSplitReason =
  | 'position_jump'
  | 'time_gap'
  | 'table_contact'    // Y reversal (bottom→top)
  | 'racket_contact'   // X reversal
  | 'end_reversal'     // 2-pt reversal at sequence end
  | 'residual'         // post-fit deviation > 0.02

export interface ArcInfo {
  startFrame: number
  endFrame: number
  detectionCount: number
  /** Why this arc ended (null for the last/ongoing arc) */
  splitReasonAfter: ArcSplitReason | null
  detections: { x: number; y: number; frameIndex: number }[]
}

/**
 * Analyze all arcs in a detection sequence using V2's splitting strategies.
 * Returns every arc with its frame boundaries and the reason each split occurred.
 */
export function analyzeArcsV2(frames: Frame[]): ArcInfo[] {
  const pastDetected = frames
    .filter(f => f.ball && f.ball.confidence >= 0.1)
    .map(f => ({ x: f.ball!.x, y: f.ball!.y, frameIndex: f.frameIndex }))

  if (pastDetected.length < 2) return []

  const splitEvents: { index: number; reason: ArcSplitReason }[] = []

  // ── Step 1: Time gaps (always split on 3+ frame gaps) ──
  for (let i = 1; i < pastDetected.length; i++) {
    if (pastDetected[i].frameIndex - pastDetected[i - 1].frameIndex >= 3) {
      splitEvents.push({ index: i, reason: 'time_gap' })
    }
  }

  // ── Step 2: 3-point reversals (run BEFORE position jumps) ──
  // Split at i+1: the reversal point belongs to the ENDING arc.
  if (pastDetected.length >= 3) {
    for (let i = 1; i < pastDetected.length - 1; i++) {
      // Don't span across time gaps
      const gapBefore = pastDetected[i].frameIndex - pastDetected[i - 1].frameIndex
      const gapAfter = pastDetected[i + 1].frameIndex - pastDetected[i].frameIndex
      if (gapBefore >= 3 || gapAfter >= 3) continue

      const prev = pastDetected[i - 1]
      const curr = pastDetected[i]
      const next = pastDetected[i + 1]
      const vyIn = curr.y - prev.y
      const vyOut = next.y - curr.y
      const vxIn = curr.x - prev.x
      const vxOut = next.x - curr.x

      const bottomToTop = vyIn > 0.003 && vyOut < -0.003
      const xReversal = (vxIn > 0.01 && vxOut < -0.01) || (vxIn < -0.01 && vxOut > 0.01)

      if (bottomToTop) {
        // Bounce-back strength ratio determines split point:
        // Strong bounce (ratio >= 0.5): split at i+1, ball clearly leaves table
        // Weak bounce (0.25-0.5): contact zone — ball lingers near table, split at i+2
        // Very weak (< 0.25): noise, skip
        const ratio = Math.abs(vyOut) / Math.abs(vyIn)
        if (ratio >= 0.5) {
          if (!splitEvents.some(s => s.index === i + 1)) {
            splitEvents.push({ index: i + 1, reason: 'table_contact' })
          }
        } else if (ratio >= 0.25) {
          const splitIdx = Math.min(i + 2, pastDetected.length)
          if (!splitEvents.some(s => s.index === splitIdx)) {
            splitEvents.push({ index: splitIdx, reason: 'table_contact' })
          }
        }
      } else if (xReversal) {
        // Racket contact: contact point (i) AND departure frame (i+1) stay in the arc
        const splitIdx = Math.min(i + 2, pastDetected.length)
        if (!splitEvents.some(s => s.index === splitIdx)) {
          splitEvents.push({ index: splitIdx, reason: 'racket_contact' })
        }
      }
    }

    // 2-point reversal at end
    const n = pastDetected.length
    if (n >= 3) {
      const gapBefore = pastDetected[n - 2].frameIndex - pastDetected[n - 3].frameIndex
      const gapAfter = pastDetected[n - 1].frameIndex - pastDetected[n - 2].frameIndex
      if (gapBefore < 3 && gapAfter < 3) {
        const vxPrev = pastDetected[n - 2].x - pastDetected[n - 3].x
        const vxCurr = pastDetected[n - 1].x - pastDetected[n - 2].x
        const vyPrev = pastDetected[n - 2].y - pastDetected[n - 3].y
        const vyCurr = pastDetected[n - 1].y - pastDetected[n - 2].y

        const yReversalAtEnd = vyPrev > 0.003 && vyCurr < -0.003
          && Math.abs(vyCurr) > Math.abs(vyPrev) * 0.5
        const xReversalAtEnd =
          (vxPrev > 0.01 && vxCurr < -0.01) || (vxPrev < -0.01 && vxCurr > 0.01)

        if (yReversalAtEnd && !splitEvents.some(s => s.index === n - 1)) {
          splitEvents.push({ index: n - 1, reason: 'end_reversal' })
        } else if (xReversalAtEnd && !splitEvents.some(s => s.index === n)) {
          splitEvents.push({ index: n, reason: 'end_reversal' })
        }
      }
    }
  }

  // ── Step 3: Velocity-aware position jumps ──
  // Compare distance to recent average speed. Only split if 3x faster than
  // recent motion AND absolute distance > 0.08. Prevents fragmenting fast
  // but continuous ball movement after contacts.
  for (let i = 1; i < pastDetected.length; i++) {
    if (splitEvents.some(s => s.index === i)) continue

    const prev = pastDetected[i - 1]
    const curr = pastDetected[i]
    const frameGap = curr.frameIndex - prev.frameIndex
    if (frameGap >= 3) continue  // already handled as time_gap

    const dx = curr.x - prev.x
    const dy = curr.y - prev.y
    const dist = Math.sqrt(dx * dx + dy * dy)
    const distPerFrame = dist / Math.max(1, frameGap)

    // Lookback up to 3 pairs, stopping at any previous split
    let speedSum = 0
    let speedCount = 0
    for (let j = i - 1; j >= 1 && j >= i - 3; j--) {
      if (splitEvents.some(s => s.index === j)) break
      const p = pastDetected[j - 1]
      const c = pastDetected[j]
      const g = c.frameIndex - p.frameIndex
      if (g >= 3) break
      speedSum += Math.sqrt((c.x - p.x) ** 2 + (c.y - p.y) ** 2) / g
      speedCount++
    }

    if (speedCount > 0) {
      const avgSpeed = speedSum / speedCount
      if (distPerFrame > avgSpeed * 3 && dist > 0.08) {
        splitEvents.push({ index: i, reason: 'position_jump' })
      }
    } else {
      // No recent history (after a split): generous absolute threshold
      if (dist > 0.15 * Math.max(1, frameGap)) {
        splitEvents.push({ index: i, reason: 'position_jump' })
      }
    }
  }

  // Sort by index
  splitEvents.sort((a, b) => a.index - b.index)

  // Build arc list from split events
  const arcs: ArcInfo[] = []
  let arcStart = 0
  for (const event of splitEvents) {
    const dets = pastDetected.slice(arcStart, event.index)
    if (dets.length > 0) {
      arcs.push({
        startFrame: dets[0].frameIndex,
        endFrame: dets[dets.length - 1].frameIndex,
        detectionCount: dets.length,
        splitReasonAfter: event.reason,
        detections: dets,
      })
    }
    arcStart = event.index
  }

  // Last arc (ongoing)
  const lastDets = pastDetected.slice(arcStart)
  if (lastDets.length > 0) {
    arcs.push({
      startFrame: lastDets[0].frameIndex,
      endFrame: lastDets[lastDets.length - 1].frameIndex,
      detectionCount: lastDets.length,
      splitReasonAfter: null,
      detections: lastDets,
    })
  }

  // Residual check on each arc with 4+ detections
  const result: ArcInfo[] = []
  for (const arc of arcs) {
    if (arc.detectionCount >= 4) {
      const fit = fitTrajectory(arc.detections)
      if (fit) {
        const arcT0 = arc.detections[0].frameIndex
        const checkFrom = Math.max(1, arc.detections.length - 3)
        let splitAt = -1
        for (let i = checkFrom; i < arc.detections.length; i++) {
          const d = arc.detections[i]
          const t = d.frameIndex - arcT0
          const xPred = fit.ax + fit.bx * t + fit.cx * t * t
          const yPred = fit.ay + fit.by * t + fit.cy * t * t
          const residual = Math.sqrt((d.x - xPred) ** 2 + (d.y - yPred) ** 2)
          if (residual > 0.02) {
            splitAt = i
            break
          }
        }
        if (splitAt > 0) {
          const firstPart = arc.detections.slice(0, splitAt)
          const secondPart = arc.detections.slice(splitAt)
          result.push({
            startFrame: firstPart[0].frameIndex,
            endFrame: firstPart[firstPart.length - 1].frameIndex,
            detectionCount: firstPart.length,
            splitReasonAfter: 'residual',
            detections: firstPart,
          })
          result.push({
            startFrame: secondPart[0].frameIndex,
            endFrame: secondPart[secondPart.length - 1].frameIndex,
            detectionCount: secondPart.length,
            splitReasonAfter: arc.splitReasonAfter,
            detections: secondPart,
          })
          continue
        }
      }
    }
    result.push(arc)
  }

  return result
}

/**
 * Build a table surface model from observed bottom→top Y reversals.
 * Each reversal gives a (x, y) point where the ball bounced — that Y is the table surface at that X.
 * With perspective, the near side of the table has higher Y than the far side.
 * A linear model tableY(x) = slope * x + intercept captures this.
 */
function buildTableSurfaceModel(detections: DetectedBall[]): TableSurfaceModel {
  const bouncePoints: { x: number; y: number; frameIndex: number }[] = []

  if (detections.length >= 3) {
    for (let i = 1; i < detections.length - 1; i++) {
      const prev = detections[i - 1]
      const curr = detections[i]
      const next = detections[i + 1]
      const vyIn = curr.y - prev.y
      const vyOut = next.y - curr.y

      // Bottom→top reversal: ball was going down (vy > 0), now going up (vy < 0)
      if (vyIn > BOUNCE_VY_THRESHOLD && vyOut < -BOUNCE_VY_THRESHOLD) {
        // Use parabolic vertex interpolation to find the actual bounce Y.
        // At 10fps, curr.y is already above the table (ball started rising).
        // Fit parabola through (prev, curr, next) and find its vertex (maximum Y = table contact).
        // For 3 equally-spaced points at t=-1,0,1:  a = (prev.y - 2*curr.y + next.y)/2
        const a = (prev.y - 2 * curr.y + next.y) / 2
        const b = (next.y - prev.y) / 2
        // Vertex at t_v = -b/(2a), vertex_y = curr.y - b²/(4a)
        let bounceY = curr.y
        if (Math.abs(a) > 1e-8 && a < 0) {
          // a < 0 means concave down = bottom of arc (max Y in screen coords)
          const tVertex = -b / (2 * a)
          if (tVertex >= -1.5 && tVertex <= 1.5) {
            bounceY = curr.y + a * tVertex * tVertex + b * tVertex
          }
        } else {
          // Fallback: use max Y among the three points (closest to table)
          bounceY = Math.max(prev.y, curr.y, next.y)
        }
        // Interpolate X at the vertex too
        const bounceX = curr.x + (next.x - prev.x) / 2 * (Math.abs(a) > 1e-8 && a < 0 ? -b / (2 * a) : 0)
        bouncePoints.push({ x: bounceX, y: bounceY, frameIndex: curr.frameIndex })
      }
    }
  }

  // Estimate table X bounds from bounce points (with margin for extrapolation)
  const TABLE_X_MARGIN = 0.05
  const computeXBounds = (pts: { x: number }[]) => {
    if (pts.length === 0) return { tableXMin: 0, tableXMax: 1 }
    const xs = pts.map(p => p.x)
    const xSpan = Math.max(0.1, Math.max(...xs) - Math.min(...xs))  // at least 0.1 span
    return {
      tableXMin: Math.min(...xs) - TABLE_X_MARGIN - xSpan * 0.3,
      tableXMax: Math.max(...xs) + TABLE_X_MARGIN + xSpan * 0.3,
    }
  }

  if (bouncePoints.length === 0) {
    return { bouncePoints, slope: 0, intercept: 0.9, isValid: false, tableXMin: 0, tableXMax: 1 }
  }

  const xBounds = computeXBounds(bouncePoints)

  if (bouncePoints.length === 1) {
    // Single bounce — use flat model at that Y
    return {
      bouncePoints,
      slope: 0,
      intercept: bouncePoints[0].y,
      isValid: true,
      ...xBounds,
    }
  }

  // 2+ bounces — fit linear model: tableY(x) = slope * x + intercept
  const n = bouncePoints.length
  const sumX = bouncePoints.reduce((s, p) => s + p.x, 0)
  const sumY = bouncePoints.reduce((s, p) => s + p.y, 0)
  const sumXY = bouncePoints.reduce((s, p) => s + p.x * p.y, 0)
  const sumX2 = bouncePoints.reduce((s, p) => s + p.x * p.x, 0)

  const denom = n * sumX2 - sumX * sumX
  if (Math.abs(denom) < 1e-12) {
    const ys = bouncePoints.map(p => p.y).sort((a, b) => a - b)
    const medianY = ys[Math.floor(ys.length / 2)]
    return { bouncePoints, slope: 0, intercept: medianY, isValid: true, ...xBounds }
  }

  const slope = (n * sumXY - sumX * sumY) / denom
  const intercept = (sumY * sumX2 - sumX * sumXY) / denom

  return { bouncePoints, slope, intercept, isValid: true, ...xBounds }
}

/** Get estimated table Y at a given X coordinate */
function getTableY(model: TableSurfaceModel, x: number): number {
  return model.slope * x + model.intercept
}

// ── Predict Trajectory V2 ──────────────────────────────────────────────────────

/**
 * Predict ball trajectory with bounce simulation.
 * Same signature as V1, but prediction reflects off estimated table surface.
 */
export function predictTrajectoryV2(
  frames: Frame[],
  currentFrame: number,
  intervalMs: number,
  predictionFrames: number = 10,
): PredictiveTrajectoryV2 | null {
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
      frameIndex: f.frameIndex,
      timestampMs: f.timestampMs,
    }))

  if (pastDetected.length < 2) return null

  // Build table surface model from ALL past detections (not just current arc)
  const tableSurface = buildTableSurfaceModel(pastDetected)

  // Find direction reversals, position jumps, and time gaps to split into sub-arcs
  const bounceIndices: number[] = []

  // 2-point checks for ALL consecutive pairs: position jump + time gap
  for (let i = 1; i < pastDetected.length; i++) {
    const prev = pastDetected[i - 1]
    const curr = pastDetected[i]
    const dx = curr.x - prev.x
    const dy = curr.y - prev.y
    const dist = Math.sqrt(dx * dx + dy * dy)
    const frameGap = curr.frameIndex - prev.frameIndex

    // Position jump: ball teleported (player hit it, or different ball)
    // Scale threshold by frame gap — 1-frame gap allows less distance than 3-frame gap
    const maxDist = 0.08 * Math.max(1, frameGap)
    if (dist > maxDist) {
      bounceIndices.push(i)
      continue
    }

    // Time gap: 3+ missing frames between detections = ball was out of view
    if (frameGap >= 3) {
      bounceIndices.push(i)
      continue
    }
  }

  if (pastDetected.length >= 3) {
    // 3-point checks for interior points (direction reversals)
    for (let i = 1; i < pastDetected.length - 1; i++) {
      const prev = pastDetected[i - 1]
      const curr = pastDetected[i]
      const next = pastDetected[i + 1]
      const vyIn = curr.y - prev.y
      const vyOut = next.y - curr.y
      const vxIn = curr.x - prev.x
      const vxOut = next.x - curr.x

      const bottomToTop = vyIn > 0.003 && vyOut < -0.003
      const xReversal = (vxIn > 0.01 && vxOut < -0.01) || (vxIn < -0.01 && vxOut > 0.01)
      if (bottomToTop || xReversal) {
        if (!bounceIndices.includes(i)) bounceIndices.push(i)
      }
    }

    // 2-point reversal checks at the LAST detection (no "next" frame needed).
    const n = pastDetected.length
    if (n >= 3) {
      const vxPrev = pastDetected[n - 2].x - pastDetected[n - 3].x
      const vxCurr = pastDetected[n - 1].x - pastDetected[n - 2].x
      const vyPrev = pastDetected[n - 2].y - pastDetected[n - 3].y
      const vyCurr = pastDetected[n - 1].y - pastDetected[n - 2].y

      // X reversal (paddle contact)
      const xReversalAtEnd =
        (vxPrev > 0.01 && vxCurr < -0.01) || (vxPrev < -0.01 && vxCurr > 0.01)
      // Y reversal (table bounce) — ball was going down, now going up
      const yReversalAtEnd = vyPrev > 0.003 && vyCurr < -0.003

      if (xReversalAtEnd || yReversalAtEnd) {
        const lastBounce = bounceIndices.length > 0 ? bounceIndices[bounceIndices.length - 1] : -1
        if (n - 2 !== lastBounce) {
          bounceIndices.push(n - 2)
        }
      }
    }
  }

  // Sort and deduplicate
  bounceIndices.sort((a, b) => a - b)

  // Use only the latest sub-arc for fitting
  let lastBounceIdx = bounceIndices.length > 0 ? bounceIndices[bounceIndices.length - 1] : 0
  let currentArc = pastDetected.slice(lastBounceIdx)
  if (currentArc.length < 2) return null

  let fit = fitTrajectory(currentArc)
  if (!fit) return null

  // ── Residual-based arc split ──────────────────────────────────────────────
  // If the last few detections deviate significantly from the fit, the ball
  // was likely contacted (paddle hit in same X direction). Split at the
  // point of maximum deviation and refit.
  if (currentArc.length >= 4) {
    const arcT0 = currentArc[0].frameIndex
    // Check last 3 detections for large residual
    const checkFrom = Math.max(1, currentArc.length - 3)
    let splitAt = -1
    for (let i = checkFrom; i < currentArc.length; i++) {
      const d = currentArc[i]
      const t = d.frameIndex - arcT0
      const xPred = fit.ax + fit.bx * t + fit.cx * t * t
      const yPred = fit.ay + fit.by * t + fit.cy * t * t
      const residual = Math.sqrt((d.x - xPred) ** 2 + (d.y - yPred) ** 2)
      if (residual > 0.02) {  // significant deviation = likely contact
        splitAt = i
        break
      }
    }
    if (splitAt > 0) {
      currentArc = currentArc.slice(splitAt)
      if (currentArc.length >= 2) {
        const newFit = fitTrajectory(currentArc)
        if (newFit) fit = newFit
      }
    }
  }

  const t0 = currentArc[0].frameIndex
  const detByFrame = new Map(currentArc.map(d => [d.frameIndex, d]))

  // Past positions: only the current sub-arc
  const pastPositions: FittedPosition[] = []
  for (let fi = t0; fi <= currentFrame; fi++) {
    const det = detByFrame.get(fi)
    if (det) {
      pastPositions.push({ frameIndex: fi, x: det.x, y: det.y, source: 'DETECTED' })
    } else {
      const t = fi - t0
      const pos = evaluateFit(fit, t)
      pastPositions.push({
        frameIndex: fi,
        x: Math.max(0, Math.min(1, pos.x)),
        y: Math.max(0, Math.min(1, pos.y)),
        source: 'INTERPOLATED',
      })
    }
  }

  // ── V2 Prediction: average velocity + gravity + bounce simulation ────────────
  //
  // Key decision: use AVERAGE VELOCITY from recent detections, not fit derivative.
  // Fit derivative (bx + 2*cx*t) approaches zero as cx captures perspective
  // deceleration — a ball moving away from camera appears to slow down in 2D.
  // Average velocity from the last few detections is more robust.

  const tCurr = currentFrame - t0
  let xNow = fit.ax + fit.bx * tCurr + fit.cx * tCurr * tCurr
  let yNow = fit.ay + fit.by * tCurr + fit.cy * tCurr * tCurr

  // Compute average velocity from the last few detections (more robust than fit derivative)
  const recentCount = Math.min(currentArc.length, 4)
  const recentDets = currentArc.slice(-recentCount)
  let vxNow: number, vyNow: number
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

  // Skip prediction if ball is barely moving (stationary on floor, held, etc.)
  const speed = Math.sqrt(vxNow * vxNow + vyNow * vyNow)
  if (speed < 0.005) return null

  const gravity = Math.max(fit.cy, MIN_GRAVITY)

  const predictedPositions: FittedPosition[] = []
  const predictedBounces: { x: number; y: number; dt: number }[] = []
  let bounceCount = 0

  for (let fi = currentFrame + 1; fi <= currentFrame + predictionFrames; fi++) {
    const dt = 1  // single frame step for bounce detection accuracy

    // Advance physics by one frame
    const xNext = xNow + vxNow * dt              // LINEAR X — no cx amplification
    let yNext = yNow + vyNow * dt + gravity * dt * dt  // quadratic Y — gravity
    const vxNext = vxNow                          // constant vx (no drag accumulation)
    let vyNext = vyNow + 2 * gravity * dt         // vy accelerates under gravity

    // Bounce check: only when ball is within estimated table X extent
    if (tableSurface.isValid && bounceCount < MAX_PREDICTION_BOUNCES) {
      const isOverTable = xNext >= tableSurface.tableXMin && xNext <= tableSurface.tableXMax
      if (isOverTable) {
        const tableY = getTableY(tableSurface, xNext)
        if (yNext >= tableY && vyNext > 0) {
          // Ball crossed the table — simulate bounce
          yNext = tableY - (yNext - tableY) * RESTITUTION  // reflect above table
          vyNext = -Math.abs(vyNext) * RESTITUTION          // reverse and dampen vy
          vxNow = vxNow * BOUNCE_VX_DAMPING                 // slow down vx on bounce
          bounceCount++
          predictedBounces.push({ x: xNext, y: tableY, dt: fi - currentFrame })
        }
      }
    }

    // Bounds check
    if (xNext < -0.1 || xNext > 1.1 || yNext < -0.1 || yNext > 1.1) break

    predictedPositions.push({
      frameIndex: fi,
      x: Math.max(0, Math.min(1, xNext)),
      y: Math.max(0, Math.min(1, yNext)),
      source: 'INTERPOLATED',
    })

    // Update state for next frame
    xNow = xNext
    yNow = yNext
    vxNow = vxNext
    vyNow = vyNext
  }

  return {
    pastPositions,
    predictedPositions,
    fit,
    segmentStartFrame: t0,
    detectionCount: currentArc.length,
    tableSurface,
    predictedBounces,
  }
}
