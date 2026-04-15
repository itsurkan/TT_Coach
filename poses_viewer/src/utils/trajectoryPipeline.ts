/**
 * Ball trajectory pipeline — TypeScript port of TrajectoryFilter + TrajectorySegmenter
 * from shared KMP module (TT_Coach_AI).
 *
 * Fits parabolic curves to ball detections, detects contacts (bounces, paddle hits),
 * splits into trajectory segments, and fills detection gaps with interpolated positions.
 */

import type { Frame, BallDetection } from '../types'

// ── Types ──────────────────────────────────────────────────────────────────────

export interface ParabolicFit {
  ax: number; bx: number; cx: number   // x(t) = ax + bx*t + cx*t²  (drag / side spin)
  ay: number; by: number; cy: number   // y(t) = ay + by*t + cy*t²  (gravity)
}

export type ContactType = 'BOUNCE' | 'PADDLE_CONTACT' | 'NET_CLIP' | 'UNKNOWN_CONTACT'

export interface ContactEvent {
  type: ContactType
  frameIndex: number
  timestampMs: number
  position: { x: number; y: number }
  velocityBefore: number
  velocityAfter: number
  confidence: number
}

export interface FittedPosition {
  frameIndex: number
  x: number
  y: number
  source: 'DETECTED' | 'INTERPOLATED'
}

export interface TrajectorySegment {
  segmentIndex: number
  startFrameIndex: number
  endFrameIndex: number
  fittedPositions: FittedPosition[]
  fitCoefficients: ParabolicFit
  fitRmsError: number
  segmentDurationMs: number
  contactBefore: ContactEvent | null
  contactAfter: ContactEvent | null
}

// ── Thresholds ─────────────────────────────────────────────────────────────────
// Base values tuned for 30fps (33ms). Adjusted at runtime for slower frame rates.

const SPEED_RATIO_THRESHOLD = 2.5
const DIRECTION_ANGLE_THRESHOLD = 60  // degrees — raised from 30 to reduce false contacts
const BOUNCE_ANGLE_THRESHOLD = 60     // degrees
const MAX_FIT_RMS_ERROR = 0.008
const MAX_RECURSION_DEPTH = 2
const MIN_SPEED = 0.015  // Minimum meaningful velocity (normalized units/frame)
const MIN_GROUP_SIZE = 3  // Minimum detections per segment

// ── TrajectoryFilter — parabolic fitting ───────────────────────────────────────

/** 3×3 determinant (row-major) */
function det3(
  a00: number, a01: number, a02: number,
  a10: number, a11: number, a12: number,
  a20: number, a21: number, a22: number,
): number {
  return (
    a00 * (a11 * a22 - a12 * a21) -
    a01 * (a10 * a22 - a12 * a20) +
    a02 * (a10 * a21 - a11 * a20)
  )
}

/** Least-squares linear fit: y = a + b*t */
export function fitLinear(ts: number[], ys: number[]): [number, number] {
  const n = ts.length
  const st = ts.reduce((s, t) => s + t, 0)
  const st2 = ts.reduce((s, t) => s + t * t, 0)
  const sy = ys.reduce((s, y) => s + y, 0)
  const sty = ts.reduce((s, t, i) => s + t * ys[i], 0)

  const d = n * st2 - st * st
  if (d === 0) return [ys.reduce((s, y) => s + y, 0) / n, 0]

  const a = (sy * st2 - sty * st) / d
  const b = (n * sty - st * sy) / d
  return [a, b]
}

/** Least-squares quadratic fit: y = a + b*t + c*t² (Cramer's rule) */
export function fitQuadratic(ts: number[], ys: number[]): [number, number, number] {
  const n = ts.length
  const st = ts.reduce((s, t) => s + t, 0)
  const st2 = ts.reduce((s, t) => s + t * t, 0)
  const st3 = ts.reduce((s, t) => s + t * t * t, 0)
  const st4 = ts.reduce((s, t) => s + t * t * t * t, 0)
  const sy = ys.reduce((s, y) => s + y, 0)
  const sty = ts.reduce((s, t, i) => s + t * ys[i], 0)
  const st2y = ts.reduce((s, t, i) => s + t * t * ys[i], 0)

  const d = det3(n, st, st2, st, st2, st3, st2, st3, st4)
  if (d === 0) {
    const [a, b] = fitLinear(ts, ys)
    return [a, b, 0]
  }

  const a = det3(sy, st, st2, sty, st2, st3, st2y, st3, st4) / d
  const b = det3(n, sy, st2, st, sty, st3, st2, st2y, st4) / d
  const c = det3(n, st, sy, st, st2, sty, st2, st3, st2y) / d
  return [a, b, c]
}

/**
 * Least-squares cubic fit: y = a + b*t + c*t² + d*t³
 * Solves 4×4 normal equations via Gaussian elimination.
 */
export function fitCubic(ts: number[], ys: number[]): [number, number, number, number] {
  const n = ts.length
  // Build normal equations: M * [a,b,c,d]^T = rhs
  // M[i][j] = sum(t^(i+j)), rhs[i] = sum(t^i * y)
  const sums: number[] = new Array(7).fill(0)  // t^0 .. t^6
  const rhs: number[] = new Array(4).fill(0)
  for (let k = 0; k < n; k++) {
    const t = ts[k]
    let tp = 1
    for (let p = 0; p <= 6; p++) {
      sums[p] += tp
      if (p <= 3) rhs[p] += tp * ys[k]
      tp *= t
    }
  }

  // 4×4 augmented matrix
  const M: number[][] = []
  for (let i = 0; i < 4; i++) {
    const row: number[] = []
    for (let j = 0; j < 4; j++) row.push(sums[i + j])
    row.push(rhs[i])
    M.push(row)
  }

  // Gaussian elimination with partial pivoting
  for (let col = 0; col < 4; col++) {
    let maxRow = col
    for (let row = col + 1; row < 4; row++) {
      if (Math.abs(M[row][col]) > Math.abs(M[maxRow][col])) maxRow = row
    }
    [M[col], M[maxRow]] = [M[maxRow], M[col]]
    if (Math.abs(M[col][col]) < 1e-12) {
      // Singular — fall back to quadratic
      const [a, b, c] = fitQuadratic(ts, ys)
      return [a, b, c, 0]
    }
    for (let row = col + 1; row < 4; row++) {
      const factor = M[row][col] / M[col][col]
      for (let j = col; j <= 4; j++) M[row][j] -= factor * M[col][j]
    }
  }

  // Back substitution
  const result = [0, 0, 0, 0]
  for (let i = 3; i >= 0; i--) {
    let sum = M[i][4]
    for (let j = i + 1; j < 4; j++) sum -= M[i][j] * result[j]
    result[i] = sum / M[i][i]
  }
  return result as [number, number, number, number]
}

/**
 * Fit trajectory model to detected ball positions.
 * - X: quadratic (captures drag / side spin deceleration)
 * - Y: cubic (captures gravity + topspin/backspin effect)
 *
 * Falls back to simpler fits with fewer points:
 * - 2 points: linear X, linear Y
 * - 3 points: quadratic X, quadratic Y
 * - 4+ points: quadratic X, cubic Y
 */
export function fitTrajectory(detections: { x: number; y: number; frameIndex: number }[]): ParabolicFit | null {
  if (detections.length < 2) return null

  const t0 = detections[0].frameIndex
  const ts = detections.map(d => d.frameIndex - t0)
  const xs = detections.map(d => d.x)
  const ys = detections.map(d => d.y)

  // X fit: quadratic (captures drag / side spin)
  let ax: number, bx: number, cx: number
  if (detections.length <= 2) {
    const [a, b] = fitLinear(ts, xs)
    ax = a; bx = b; cx = 0
  } else {
    [ax, bx, cx] = fitQuadratic(ts, xs)
  }

  // Y fit: quadratic (gravity)
  let ay: number, by: number, cy: number
  if (detections.length <= 2) {
    const [a, b] = fitLinear(ts, ys)
    ay = a; by = b; cy = 0
  } else {
    [ay, by, cy] = fitQuadratic(ts, ys)
  }

  return { ax, bx, cx, ay, by, cy }
}

/** Evaluate the fitted model at frame-index offset t (relative to segment start). */
export function evaluateFit(fit: ParabolicFit, t: number): { x: number; y: number } {
  return {
    x: fit.ax + fit.bx * t + fit.cx * t * t,
    y: fit.ay + fit.by * t + fit.cy * t * t,
  }
}

/** RMS deviation of detections from fitted model. */
export function rmsError(fit: ParabolicFit, detections: { x: number; y: number; frameIndex: number }[]): number {
  if (detections.length === 0) return 0
  const t0 = detections[0].frameIndex
  let sumSq = 0
  for (const d of detections) {
    const t = d.frameIndex - t0
    const xPred = fit.ax + fit.bx * t + fit.cx * t * t
    const yPred = fit.ay + fit.by * t + fit.cy * t * t
    const dx = d.x - xPred
    const dy = d.y - yPred
    sumSq += dx * dx + dy * dy
  }
  return Math.sqrt(sumSq / detections.length)
}

/** Fill gaps — return a FittedPosition for every frame in [startFrame, endFrame]. */
export function fillGaps(
  fit: ParabolicFit,
  detections: { x: number; y: number; frameIndex: number }[],
  startFrame: number,
  endFrame: number,
): FittedPosition[] {
  const detByFrame = new Map(detections.map(d => [d.frameIndex, d]))
  const t0 = startFrame
  const result: FittedPosition[] = []

  for (let fi = startFrame; fi <= endFrame; fi++) {
    const det = detByFrame.get(fi)
    if (det) {
      result.push({ frameIndex: fi, x: det.x, y: det.y, source: 'DETECTED' })
    } else {
      const t = fi - t0
      const pos = evaluateFit(fit, t)
      const x = Math.max(0, Math.min(1, pos.x))
      const y = Math.max(0, Math.min(1, pos.y))
      result.push({ frameIndex: fi, x, y, source: 'INTERPOLATED' })
    }
  }
  return result
}

// ── Contact detection (three-signal detector) ──────────────────────────────────

interface DetectedBall {
  x: number; y: number; frameIndex: number; timestampMs: number
}

export function detectContacts(detected: DetectedBall[]): ContactEvent[] {
  if (detected.length < 3) return []
  const contacts: ContactEvent[] = []

  for (let i = 1; i < detected.length - 1; i++) {
    const prev = detected[i - 1]
    const curr = detected[i]
    const next = detected[i + 1]

    const vxIn = curr.x - prev.x
    const vyIn = curr.y - prev.y
    const vxOut = next.x - curr.x
    const vyOut = next.y - curr.y

    const speedIn = Math.sqrt(vxIn * vxIn + vyIn * vyIn)
    const speedOut = Math.sqrt(vxOut * vxOut + vyOut * vyOut)

    // Signal 1: vertical velocity reversal → BOUNCE
    const vySignChange = (vyIn > 0 && vyOut < 0) || (vyIn < 0 && vyOut > 0)
    const vySignificant = Math.abs(vyIn) > MIN_SPEED && Math.abs(vyOut) > MIN_SPEED

    if (vySignChange && vySignificant) {
      contacts.push({
        type: 'BOUNCE',
        frameIndex: curr.frameIndex,
        timestampMs: curr.timestampMs,
        position: { x: curr.x, y: curr.y },
        velocityBefore: speedIn,
        velocityAfter: speedOut,
        confidence: 0.9,
      })
      continue
    }

    // Signal 2: speed ratio spike → PADDLE_CONTACT
    if (speedIn > MIN_SPEED && speedOut > MIN_SPEED) {
      const ratio = speedOut / speedIn
      const inverseRatio = speedIn / speedOut
      if (ratio > SPEED_RATIO_THRESHOLD || inverseRatio > SPEED_RATIO_THRESHOLD) {
        contacts.push({
          type: 'PADDLE_CONTACT',
          frameIndex: curr.frameIndex,
          timestampMs: curr.timestampMs,
          position: { x: curr.x, y: curr.y },
          velocityBefore: speedIn,
          velocityAfter: speedOut,
          confidence: 0.85,
        })
        continue
      }
    }

    // Signal 3: direction angle change → NET_CLIP or UNKNOWN_CONTACT
    if (speedIn > MIN_SPEED && speedOut > MIN_SPEED) {
      const dot = vxIn * vxOut + vyIn * vyOut
      const cosAngle = Math.max(-1, Math.min(1, dot / (speedIn * speedOut)))
      const angleDeg = Math.acos(cosAngle) * 180 / Math.PI

      if (angleDeg > DIRECTION_ANGLE_THRESHOLD) {
        const type: ContactType =
          (angleDeg > BOUNCE_ANGLE_THRESHOLD && angleDeg < 150)
            ? 'NET_CLIP'
            : 'UNKNOWN_CONTACT'
        contacts.push({
          type,
          frameIndex: curr.frameIndex,
          timestampMs: curr.timestampMs,
          position: { x: curr.x, y: curr.y },
          velocityBefore: speedIn,
          velocityAfter: speedOut,
          confidence: 0.7,
        })
      }
    }
  }
  return contacts
}

// ── Segmenter ──────────────────────────────────────────────────────────────────

function fitGroup(
  group: DetectedBall[],
  segmentIndex: number,
  contactBefore: ContactEvent | null,
  contactAfter: ContactEvent | null,
  frameDurationMs: number,
  allDetected: DetectedBall[],
  recursionDepth: number,
): TrajectorySegment[] {
  const fit = fitTrajectory(group)
  if (!fit) return []

  const rms = rmsError(fit, group)
  const startFrame = group[0].frameIndex
  const endFrame = group[group.length - 1].frameIndex
  const filled = fillGaps(fit, group, startFrame, endFrame)
  const durationMs = (endFrame - startFrame) * frameDurationMs

  const segment: TrajectorySegment = {
    segmentIndex,
    startFrameIndex: startFrame,
    endFrameIndex: endFrame,
    fittedPositions: filled,
    fitCoefficients: fit,
    fitRmsError: rms,
    segmentDurationMs: durationMs,
    contactBefore,
    contactAfter,
  }

  // Gravity constraint: a real ball arc has cy > 0 (curves downward in screen coords).
  // cy <= 0 means two arcs with opposite curvature got merged — force a split.
  const badGravity = fit.cy <= 0 && group.length >= 4

  // Recursive sub-split on high RMS or unphysical curvature (max 1 level)
  if ((rms > MAX_FIT_RMS_ERROR || badGravity) && recursionDepth < MAX_RECURSION_DEPTH && group.length >= 4) {
    return trySplit(group, segmentIndex, contactBefore, contactAfter, frameDurationMs, allDetected, recursionDepth, segment)
  }

  return [segment]
}

function trySplit(
  group: DetectedBall[],
  segmentIndex: number,
  contactBefore: ContactEvent | null,
  contactAfter: ContactEvent | null,
  frameDurationMs: number,
  allDetected: DetectedBall[],
  recursionDepth: number,
  fallback: TrajectorySegment,
): TrajectorySegment[] {
  const fit = fitTrajectory(group)
  if (!fit) return [fallback]

  const t0 = group[0].frameIndex

  // Find detection with highest residual
  let maxIdx = 0
  let maxResidual = -1
  for (let i = 0; i < group.length; i++) {
    const d = group[i]
    const t = d.frameIndex - t0
    const xPred = fit.ax + fit.bx * t + fit.cx * t * t
    const yPred = fit.ay + fit.by * t + fit.cy * t * t
    const dx = d.x - xPred
    const dy = d.y - yPred
    const r = dx * dx + dy * dy
    if (r > maxResidual) { maxResidual = r; maxIdx = i }
  }

  if (maxIdx <= 0 || maxIdx >= group.length - 1) return [fallback]

  const left = group.slice(0, maxIdx + 1)
  const right = group.slice(maxIdx)

  const splitContact: ContactEvent = {
    type: 'UNKNOWN_CONTACT',
    frameIndex: group[maxIdx].frameIndex,
    timestampMs: group[maxIdx].timestampMs,
    position: { x: group[maxIdx].x, y: group[maxIdx].y },
    velocityBefore: 0,
    velocityAfter: 0,
    confidence: 0.5,
  }

  const leftSegs = fitGroup(left, segmentIndex, contactBefore, splitContact, frameDurationMs, allDetected, recursionDepth + 1)
  const rightSegs = fitGroup(right, segmentIndex + leftSegs.length, splitContact, contactAfter, frameDurationMs, allDetected, recursionDepth + 1)

  if (leftSegs.length === 0 || rightSegs.length === 0) return [fallback]

  const reindexed = rightSegs.map((seg, idx) => ({
    ...seg,
    segmentIndex: segmentIndex + leftSegs.length + idx,
  }))
  return [...leftSegs, ...reindexed]
}

/**
 * Main entry point: segment ball trajectory from frame data.
 * Uses kinematic contact detection (velocity reversals, speed spikes, direction changes)
 * to split into segments, fits parabolas to each, and fills gaps.
 */
export function segmentTrajectory(
  frames: Frame[],
  intervalMs: number,
): TrajectorySegment[] {
  // Extract detected balls — filter out NOT_DETECTED placeholders and weak hits
  const detected: DetectedBall[] = frames
    .filter(f => {
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

  if (detected.length === 0) return []

  const contacts = detectContacts(detected)
  const splitAfter = new Set(contacts.map(c => c.frameIndex))

  // Partition into groups at contact points
  const groups: DetectedBall[][] = []
  let current: DetectedBall[] = []

  for (const d of detected) {
    current.push(d)
    if (splitAfter.has(d.frameIndex)) {
      groups.push(current)
      current = [d]  // next segment starts with contact point for continuity
    }
  }
  if (current.length > 0) groups.push(current)

  // Fit each group — skip groups with fewer than 3 detections (noise, not real arcs)
  const segments: TrajectorySegment[] = []
  for (let gi = 0; gi < groups.length; gi++) {
    const group = groups[gi]
    if (group.length < 3) continue

    const contactAtEnd = contacts.find(c => c.frameIndex === group[group.length - 1].frameIndex) ?? null
    const contactAtStart = contacts.find(c => c.frameIndex === group[0].frameIndex) ?? null

    const built = fitGroup(
      group,
      segments.length,
      gi > 0 ? contactAtStart : null,
      contactAtEnd,
      intervalMs,
      detected,
      0,
    )
    segments.push(...built)
  }

  return segments
}

// ── Predictive trajectory (causal — only uses past data) ───────────────────────

export interface PredictiveTrajectory {
  /** Fitted positions from segment start to current frame */
  pastPositions: FittedPosition[]
  /** Extrapolated positions from current frame forward */
  predictedPositions: FittedPosition[]
  /** The parabolic fit used */
  fit: ParabolicFit
  /** Frame where the current arc started (last contact before current frame) */
  segmentStartFrame: number
  /** Number of detections used for the fit */
  detectionCount: number
}

/**
 * Predict ball trajectory at a given frame using only past data.
 * Finds the most recent audio contact, fits a parabola from detections since that contact,
 * then extrapolates forward.
 */
export function predictTrajectory(
  frames: Frame[],
  currentFrame: number,
  intervalMs: number,
  predictionFrames: number = 10,
): PredictiveTrajectory | null {
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

  // Use all past detections (direction reversals will split into sub-arcs below)
  const segmentStart = 0

  // Collect detections from start to current frame
  const arcDetections = pastDetected.filter(d => d.frameIndex >= segmentStart)
  if (arcDetections.length < 2) return null

  // Find direction reversals within the arc:
  // - Y reversal = table bounce (ball goes down then up)
  // - X reversal = paddle contact (ball changes horizontal direction)
  // These split the arc into sub-arcs but we keep the full trajectory for display.
  const bounceIndices: number[] = []
  if (arcDetections.length >= 3) {
    for (let i = 1; i < arcDetections.length - 1; i++) {
      const prev = arcDetections[i - 1]
      const curr = arcDetections[i]
      const next = arcDetections[i + 1]
      const vyIn = curr.y - prev.y
      const vyOut = next.y - curr.y
      const vxIn = curr.x - prev.x
      const vxOut = next.x - curr.x
      // Bottom→top Y reversal only (ball was falling, now rising = table bounce)
      // Top→bottom is just gravity — same trajectory, not a new arc
      const bottomToTop = vyIn > 0.003 && vyOut < -0.003
      // X reversal: ball changes horizontal direction (left↔right = paddle contact)
      const xReversal = (vxIn > 0.01 && vxOut < -0.01) || (vxIn < -0.01 && vxOut > 0.01)
      if (bottomToTop || xReversal) bounceIndices.push(i)
    }
  }

  // Use only the latest sub-arc (after last reversal) for both display and prediction
  const lastBounceIdx = bounceIndices.length > 0 ? bounceIndices[bounceIndices.length - 1] : 0
  const currentArc = arcDetections.slice(lastBounceIdx)
  if (currentArc.length < 2) return null

  const fit = fitTrajectory(currentArc)
  if (!fit) return null

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

  // Prediction: current position + current velocity + gravity
  // No rebasing tricks — just simple physics from current state
  const tCurr = currentFrame - t0
  const xNow = fit.ax + fit.bx * tCurr + fit.cx * tCurr * tCurr
  const yNow = fit.ay + fit.by * tCurr + fit.cy * tCurr * tCurr
  const vxNow = fit.bx + 2 * fit.cx * tCurr
  const vyNow = fit.by + 2 * fit.cy * tCurr
  const gravity = Math.max(fit.cy, 0.002)  // minimum downward acceleration

  const predictedPositions: FittedPosition[] = []
  for (let fi = currentFrame + 1; fi <= currentFrame + predictionFrames; fi++) {
    const dt = fi - currentFrame
    const x = xNow + vxNow * dt
    const y = yNow + vyNow * dt + gravity * dt * dt
    if (x < -0.1 || x > 1.1 || y < -0.1 || y > 1.1) break
    predictedPositions.push({
      frameIndex: fi,
      x: Math.max(0, Math.min(1, x)),
      y: Math.max(0, Math.min(1, y)),
      source: 'INTERPOLATED',
    })
  }

  return {
    pastPositions,
    predictedPositions,
    fit,
    segmentStartFrame: t0,
    detectionCount: arcDetections.length,
  }
}
