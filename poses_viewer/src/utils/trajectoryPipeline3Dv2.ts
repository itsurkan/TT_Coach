/**
 * Trajectory Pipeline 3D v2 — physics-aware trajectory fitting.
 *
 * Unlike v1 (which projects raw detections independently), v2:
 * 1. Segments detections into arcs at contacts (bounces + racket hits)
 * 2. Fits a smooth parabola per arc in screen space
 * 3. Projects the FITTED curve through homography → (x_cm, y_cm)
 * 4. Computes z_cm from gravity parabola between bounce contacts
 * 5. Flags raw detections that deviate from the fitted arc (detection errors)
 */

import type { BallDetection, BallPosition3D, Bounce3D } from '../types'
import { screenToTable, type TableHomography } from './tableHomography'
import { fitTrajectoryV3, evaluateFitV3, type ParabolicFitV3 } from './trajectoryPipelineV3'

// ── Constants ─────────────────────────────────────────────────────────────────

const BOUNCE_VY_THRESHOLD = 0.003
const X_REVERSAL_THRESHOLD = 0.01
const REVERSAL_ANGLE_THRESHOLD = 45
const MIN_REVERSAL_SPEED = 0.005
const MAX_FRAME_GAP = 3
const OUTLIER_THRESHOLD = 0.015  // normalized screen distance
const GRAVITY_CM = 981

// ── Types ─────────────────────────────────────────────────────────────────────

interface BallFrame {
  frameIndex: number
  x: number
  y: number
  confidence: number
}

type ContactType = 'bounce' | 'racket' | 'gap' | 'start' | 'end'

interface Arc {
  detections: BallFrame[]
  startContact: ContactType
  endContact: ContactType
  fit: ParabolicFitV3 | null
}

export interface Trajectory3Dv2Result {
  positions: BallPosition3D[]
  bounces: Bounce3D[]
  arcs: { startFrame: number; endFrame: number; startContact: string; endContact: string }[]
  outlierFrames: number[]
}

// ── Arc Segmentation ──────────────────────────────────────────────────────────

/**
 * Split ball detections into arcs at contact points.
 * Contact types: frame gap, X reversal (racket), angle reversal (racket),
 * Y reversal down→up without X flip (table bounce).
 */
function segmentArcs(balls: BallFrame[]): Arc[] {
  if (balls.length < 2) {
    return balls.length === 1
      ? [{ detections: balls, startContact: 'start', endContact: 'end', fit: null }]
      : []
  }

  const arcs: Arc[] = []
  let currentArc: BallFrame[] = [balls[0]]
  let currentStart: ContactType = 'start'

  for (let i = 1; i < balls.length; i++) {
    const prev = balls[i - 1]
    const curr = balls[i]

    // 1. Frame gap → hard split
    if (curr.frameIndex - prev.frameIndex > MAX_FRAME_GAP) {
      arcs.push({ detections: currentArc, startContact: currentStart, endContact: 'gap', fit: null })
      currentArc = [curr]
      currentStart = 'gap'
      continue
    }

    // Need at least 3 points for velocity-based detection
    if (i < 2 || !balls[i - 2]) {
      currentArc.push(curr)
      continue
    }

    const prevPrev = balls[i - 2]
    // Skip if there's a frame gap in the triplet
    if (prev.frameIndex - prevPrev.frameIndex > 2 || curr.frameIndex - prev.frameIndex > 2) {
      currentArc.push(curr)
      continue
    }

    const vxIn = prev.x - prevPrev.x
    const vyIn = prev.y - prevPrev.y
    const vxOut = curr.x - prev.x
    const vyOut = curr.y - prev.y
    const speedIn = Math.sqrt(vxIn * vxIn + vyIn * vyIn)
    const speedOut = Math.sqrt(vxOut * vxOut + vyOut * vyOut)

    // Skip reversal checks if speed is too low (noise)
    if (speedIn < MIN_REVERSAL_SPEED || speedOut < MIN_REVERSAL_SPEED) {
      currentArc.push(curr)
      continue
    }

    // 2. X reversal → racket hit
    const xReversed = Math.abs(vxIn) > X_REVERSAL_THRESHOLD &&
                      Math.abs(vxOut) > X_REVERSAL_THRESHOLD &&
                      Math.sign(vxIn) !== Math.sign(vxOut)

    if (xReversed) {
      arcs.push({ detections: currentArc, startContact: currentStart, endContact: 'racket', fit: null })
      currentArc = [prev, curr]  // racket contact at prev
      currentStart = 'racket'
      continue
    }

    // 3. Angle reversal > 45° (excluding gravity apex)
    const dot = vxIn * vxOut + vyIn * vyOut
    const cosAngle = dot / (speedIn * speedOut)
    const angleDeg = Math.acos(Math.min(1, Math.max(-1, cosAngle))) * 180 / Math.PI
    const isGravityApex = vyIn < 0 && vyOut > 0 && Math.abs(vxIn - vxOut) < X_REVERSAL_THRESHOLD

    if (angleDeg > REVERSAL_ANGLE_THRESHOLD && !isGravityApex) {
      arcs.push({ detections: currentArc, startContact: currentStart, endContact: 'racket', fit: null })
      currentArc = [prev, curr]
      currentStart = 'racket'
      continue
    }

    // 4. Y reversal down→up without X reversal → table bounce
    if (vyIn > BOUNCE_VY_THRESHOLD && vyOut < -BOUNCE_VY_THRESHOLD && !xReversed) {
      // Bounce at prev — shared between arcs
      arcs.push({ detections: [...currentArc], startContact: currentStart, endContact: 'bounce', fit: null })
      currentArc = [prev, curr]  // bounce point shared
      currentStart = 'bounce'
      continue
    }

    currentArc.push(curr)
  }

  // Close final arc
  if (currentArc.length > 0) {
    arcs.push({ detections: currentArc, startContact: currentStart, endContact: 'end', fit: null })
  }

  return arcs
}

// ── Fit and Evaluate ──────────────────────────────────────────────────────────

/** Fit each arc and evaluate fitted positions. Returns fitted screen positions + outlier flags. */
function fitArc(arc: Arc): { fitted: { frameIndex: number; x: number; y: number }[]; outliers: Set<number> } {
  const dets = arc.detections
  const outliers = new Set<number>()

  if (dets.length < 2) {
    return { fitted: dets.map(d => ({ frameIndex: d.frameIndex, x: d.x, y: d.y })), outliers }
  }

  const fit = fitTrajectoryV3(dets)
  if (!fit) {
    return { fitted: dets.map(d => ({ frameIndex: d.frameIndex, x: d.x, y: d.y })), outliers }
  }

  arc.fit = fit
  const t0 = dets[0].frameIndex
  const fitted: { frameIndex: number; x: number; y: number }[] = []

  for (const d of dets) {
    const t = d.frameIndex - t0
    const f = evaluateFitV3(fit, t)
    const dist = Math.sqrt((f.x - d.x) ** 2 + (f.y - d.y) ** 2)
    if (dist > OUTLIER_THRESHOLD) {
      outliers.add(d.frameIndex)
    }
    // Always use fitted position (smooth, physically plausible)
    fitted.push({ frameIndex: d.frameIndex, x: f.x, y: f.y })
  }

  return { fitted, outliers }
}

// ── Z Estimation ──────────────────────────────────────────────────────────────

/** Compute z_cm for a frame given its position within bounce-delimited arcs. */
function estimateZFromArcs(
  frameIndex: number,
  arcs: Arc[],
  intervalMs: number,
): number {
  const frameSec = intervalMs / 1000

  for (const arc of arcs) {
    const first = arc.detections[0]
    const last = arc.detections[arc.detections.length - 1]
    if (frameIndex < first.frameIndex || frameIndex > last.frameIndex) continue

    const isBounceStart = arc.startContact === 'bounce'
    const isBounceEnd = arc.endContact === 'bounce'

    if (isBounceStart && isBounceEnd) {
      // Bounce-to-bounce: z(t) = ½g·t·(T-t), exact free flight
      const T = (last.frameIndex - first.frameIndex) * frameSec
      const t = (frameIndex - first.frameIndex) * frameSec
      return T > 0 ? Math.max(0, 0.5 * GRAVITY_CM * t * (T - t)) : 0
    }

    if (isBounceStart) {
      // Bounce-to-racket: ascending from bounce, z(t) = ½g·t²
      const t = (frameIndex - first.frameIndex) * frameSec
      return Math.max(0, 0.5 * GRAVITY_CM * t * t)
    }

    if (isBounceEnd) {
      // Racket-to-bounce: descending to bounce, z(t) = ½g·(T-t)²  (mirror)
      const T = (last.frameIndex - first.frameIndex) * frameSec
      const t = (frameIndex - first.frameIndex) * frameSec
      return T > 0 ? Math.max(0, 0.5 * GRAVITY_CM * (T - t) * (T - t)) : 0
    }

    // Racket-to-racket or unknown: no bounce reference, z unknown
    return 0
  }

  return 0
}

// ── Main Pipeline ─────────────────────────────────────────────────────────────

export function computeTrajectory3Dv2(
  ballFrames: { frameIndex: number; ball: BallDetection }[],
  homography: TableHomography,
  videoWidth: number,
  videoHeight: number,
  intervalMs: number,
): Trajectory3Dv2Result {
  const balls: BallFrame[] = ballFrames.map(f => ({
    frameIndex: f.frameIndex,
    x: f.ball.x,
    y: f.ball.y,
    confidence: f.ball.confidence,
  }))

  // Step 1: Segment into arcs
  const arcs = segmentArcs(balls)

  // Step 2: Fit each arc + detect outliers
  const allFitted: { frameIndex: number; x: number; y: number }[] = []
  const allOutliers = new Set<number>()
  const confidenceMap = new Map<number, number>()
  balls.forEach(b => confidenceMap.set(b.frameIndex, b.confidence))

  for (const arc of arcs) {
    const { fitted, outliers } = fitArc(arc)
    allFitted.push(...fitted)
    outliers.forEach(f => allOutliers.add(f))
  }

  // Step 3: Identify bounces from arc boundaries
  const bounces: Bounce3D[] = []
  for (const arc of arcs) {
    if (arc.startContact === 'bounce') {
      const bp = arc.detections[0]
      const tablePt = screenToTable(homography, bp.x, bp.y, videoWidth, videoHeight)
      if (tablePt) {
        // Avoid duplicate bounces (shared between arcs)
        if (!bounces.some(b => b.frameIndex === bp.frameIndex)) {
          bounces.push({ frameIndex: bp.frameIndex, x_cm: tablePt.x_cm, y_cm: tablePt.y_cm, z_cm: 0 })
        }
      }
    }
  }

  // Step 4: Project fitted positions → (x_cm, y_cm) + compute z_cm
  const positions: BallPosition3D[] = []
  for (const f of allFitted) {
    const tablePt = screenToTable(homography, f.x, f.y, videoWidth, videoHeight)
    const z_cm = estimateZFromArcs(f.frameIndex, arcs, intervalMs)
    positions.push({
      frameIndex: f.frameIndex,
      x_cm: tablePt?.x_cm ?? 0,
      y_cm: tablePt?.y_cm ?? 0,
      z_cm,
      screenX: f.x,
      screenY: f.y,
      confidence: confidenceMap.get(f.frameIndex) ?? 0,
    })
  }

  // Step 5: Assemble arc info
  const arcInfo = arcs.map(a => ({
    startFrame: a.detections[0]?.frameIndex ?? 0,
    endFrame: a.detections[a.detections.length - 1]?.frameIndex ?? 0,
    startContact: a.startContact,
    endContact: a.endContact,
  }))

  return {
    positions,
    bounces,
    arcs: arcInfo,
    outlierFrames: [...allOutliers],
  }
}
