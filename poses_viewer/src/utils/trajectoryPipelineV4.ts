/**
 * Trajectory pipeline V4 — based on V1 with targeted improvements:
 *
 * Fix #1: No contact-frame sharing — next arc starts AFTER the contact frame.
 * Fix #4: Minimum group size lowered from 3 → 2 so short arcs aren't dropped.
 * Rule: Frame gap — 2+ missed frames = previous trajectory complete.
 * Rule: Ball-diameter — if ball is above predicted position by > 1 diameter → table contact.
 * Prediction: Linear X + Quadratic Y (no quadratic X overfitting).
 */

import type { Frame } from '../types'
import {
  fitTrajectory,
  fitLinear,
  fitQuadratic,
  evaluateFit,
  rmsError,
  fillGaps,
  detectContacts,
  type ParabolicFit,
  type ContactEvent,
  type FittedPosition,
  type TrajectorySegment,
  type PredictiveTrajectory,
} from './trajectoryPipeline'

// ── Constants (same as V1) ──────────────────────────────────────────────────────

const MAX_FIT_RMS_ERROR = 0.008
const MAX_RECURSION_DEPTH = 2

// ── Constants ───────────────────────────────────────────────────────────────────

const BOUNCE_VY_THRESHOLD = 0.003
const X_REVERSAL_THRESHOLD = 0.01
const MAX_FRAME_GAP = 2          // allow 1 missed frame; 2+ missed = hard split
const MIN_GRAVITY = 0.002

// ── Private types ───────────────────────────────────────────────────────────────

interface DetectedBall {
  x: number; y: number; frameIndex: number; timestampMs: number; radiusPx?: number
}

// ── Segmenter internals (copied from V1 — fitGroup / trySplit are not exported) ─

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

  const badGravity = fit.cy <= 0 && group.length >= 4

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

// ── Main entry point ────────────────────────────────────────────────────────────

export function segmentTrajectoryV4(
  frames: Frame[],
  intervalMs: number,
): TrajectorySegment[] {
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
      // FIX #1: next arc starts AFTER the contact frame (no sharing)
      current = []
    }
  }
  if (current.length > 0) groups.push(current)

  // FIX #4: allow groups with 2+ detections (was 3 in V1)
  const segments: TrajectorySegment[] = []
  for (let gi = 0; gi < groups.length; gi++) {
    const group = groups[gi]
    if (group.length < 2) continue

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

// ── Predictive trajectory V4 ────────────────────────────────────────────────────

/**
 * Predict ball trajectory using only past data.
 * Improvements over V1:
 * - Frame gap rule: 2+ missed frames = trajectory complete, start fresh
 * - Fix #1: arc starts AFTER reversal (no contact-frame sharing)
 * - Ball-diameter rule: if ball above predicted by > 1 diameter → table contact
 * - Linear X + Quadratic Y prediction (avoids quadratic X overfitting)
 */
export function predictTrajectoryV4(
  frames: Frame[],
  currentFrame: number,
  intervalMs: number,
  predictionFrames: number = 10,
  videoSize?: { width: number; height: number },
): PredictiveTrajectory | null {
  const imgH = videoSize?.height ?? 1920

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
      radiusPx: f.ball!.radiusPx ?? 10,
    }))

  if (pastDetected.length < 2) return null

  // Find split points: direction reversals AND frame gaps
  const splitIndices: number[] = []
  for (let i = 1; i < pastDetected.length; i++) {
    const prev = pastDetected[i - 1]
    const curr = pastDetected[i]

    // Frame gap rule: 2+ missed frames → hard split
    if (curr.frameIndex - prev.frameIndex > MAX_FRAME_GAP) {
      splitIndices.push(i)
      continue
    }

    // Direction reversals (need triplet: prev, curr, next)
    if (i < pastDetected.length - 1) {
      const next = pastDetected[i + 1]
      const vyIn = curr.y - prev.y
      const vyOut = next.y - curr.y
      const vxIn = curr.x - prev.x
      const vxOut = next.x - curr.x
      const bottomToTop = vyIn > BOUNCE_VY_THRESHOLD && vyOut < -BOUNCE_VY_THRESHOLD
      const xReversal = (vxIn > X_REVERSAL_THRESHOLD && vxOut < -X_REVERSAL_THRESHOLD) ||
                         (vxIn < -X_REVERSAL_THRESHOLD && vxOut > X_REVERSAL_THRESHOLD)
      if (bottomToTop || xReversal) splitIndices.push(i)
    }
  }

  // Arc starts AT the last split point (reversal point is valid start of new arc)
  let arcStart: number
  if (splitIndices.length > 0) {
    arcStart = splitIndices[splitIndices.length - 1]
  } else {
    arcStart = 0
  }

  let currentArc = pastDetected.slice(arcStart)
  if (currentArc.length < 2) return null

  // Ball-diameter rule: check if the latest detection fits the trajectory
  if (currentArc.length >= 3) {
    const preArc = currentArc.slice(0, -1)
    const lastDet = currentArc[currentArc.length - 1]

    const t0 = preArc[0].frameIndex
    const ts = preArc.map(d => d.frameIndex - t0)
    const ys = preArc.map(d => d.y)

    const [ay, by, cy] = ts.length >= 3 ? fitQuadratic(ts, ys) : [...fitLinear(ts, ys), 0]
    const vyFit = by + 2 * cy * (preArc[preArc.length - 1].frameIndex - t0)

    const tNew = lastDet.frameIndex - t0
    const predY = ay + by * tNew + cy * tNew * tNew
    const ballAbovePred = lastDet.y < predY
    const devPx = (predY - lastDet.y) * imgH
    const ballDiameter = 2 * (lastDet.radiusPx ?? 10)

    // Ball is above prediction while trajectory says descending → table contact
    if (vyFit > 0 && ballAbovePred && devPx > ballDiameter) {
      currentArc = [lastDet]
    }
  }

  if (currentArc.length < 2) return null

  // Fit: quadratic Y (gravity), linear X (no overfitting)
  const fit = fitTrajectory(currentArc)
  if (!fit) return null

  const t0 = currentArc[0].frameIndex
  const detByFrame = new Map(currentArc.map(d => [d.frameIndex, d]))

  // Past positions
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

  // Prediction: Linear X + Quadratic Y
  const tCurr = currentFrame - t0
  const xNow = fit.ax + fit.bx * tCurr + fit.cx * tCurr * tCurr
  const yNow = fit.ay + fit.by * tCurr + fit.cy * tCurr * tCurr
  // Linear X velocity (average from fit, no quadratic acceleration)
  const vxNow = fit.bx  // use linear term only — no 2*cx*t drag accumulation
  const vyNow = fit.by + 2 * fit.cy * tCurr
  const gravity = Math.max(fit.cy, MIN_GRAVITY)

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
    detectionCount: currentArc.length,
  }
}
