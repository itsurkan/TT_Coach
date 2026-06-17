/**
 * Tests for extractPerPhase — per-phase metric extraction over a StrokeCycle2D.
 *
 * Fixture strategy mirrors drillMetrics.test.ts: synthetic PoseFrame2D arrays
 * where the arm geometry is varied between frames to produce distinct metric
 * values at each phase anchor frame, letting us assert that each phase reads
 * from the correct anchor.
 *
 * Frame layout for the paired-cycle suite (intervalMs = 100):
 *   backswing: startFrame=0, peak=2, endFrame=4
 *   drive:     startFrame=5, peak=8, endFrame=12
 *
 *   frame 0  (backswing.startFrame — NOT the backswing phase anchor): elbow ~90°  (WRIST_90)
 *   frame 5  (drive.startFrame    — the backswing PHASE anchor):      elbow ~60°  (WRIST_60)
 *   frame 8  (drive.peakFrame     — contact anchor):                  elbow ~120° (WRIST_120)
 *   frame 12 (drive.endFrame      — followthrough anchor):            default WRIST_90
 *
 * Critically, drive.startFrame (5) != backswing.startFrame (0), and the wrist
 * pose at frame 5 (WRIST_60) differs from the pose at frame 0 (WRIST_90), so
 * the backswing-phase assertion pins the anchor to drive.startFrame, not to
 * backswing.startFrame or cycle.startFrame.
 *
 * We use a wide enough frame range (13 frames) so that the ±70ms window
 * (radius = trunc(70/100) = 0) degrades to a SINGLE frame — making it trivial
 * to assert which anchor frame was read for each phase.
 */
import { describe, expect, it } from 'vitest'
import { extractPerPhase, METRIC, METRIC_PHASES, Phase } from '../drillMetrics'
import { Coco17, Keypoint2D, PoseFrame2D, Stroke2D, StrokeCycle2D, makeCycle } from '../types'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const K = (x: number, y: number, score = 1): Keypoint2D => ({ x, y, score })

/**
 * Build a frame with a right elbow at a given wrist position.
 * Shoulder at (0.50, 0.22), elbow at (0.50, 0.42); varying the wrist
 * position changes the computed elbow angle.
 * All other keypoints default to (0.5, 0.5, score=1).
 */
function armFrame(idx: number, wrist: Keypoint2D): PoseFrame2D {
  const kp: Keypoint2D[] = Array.from({ length: 17 }, () => K(0.5, 0.5))
  kp[Coco17.RIGHT_SHOULDER] = K(0.50, 0.22)
  kp[Coco17.RIGHT_ELBOW]    = K(0.50, 0.42)
  kp[Coco17.RIGHT_WRIST]    = wrist
  return { frameIndex: idx, timestampMs: idx * 100, keypoints: kp }
}

/** ~90° elbow: wrist to the right of the elbow. */
const WRIST_90 = K(0.70, 0.42)

/** ~60° elbow: wrist upper-right of the elbow (more acute). */
const WRIST_60 = K(0.64, 0.28)

/** ~120° elbow: wrist lower-right of the elbow. */
const WRIST_120 = K(0.64, 0.56)

const stroke = (
  i: number, start: number, peak: number, end: number, speed = 2.0,
): Stroke2D => ({ strokeIndex: i, startFrame: start, peakFrame: peak, endFrame: end, peakSpeed: speed })

/**
 * Build 13 identical frames, then swap specific indices to distinct wrist poses.
 * Interval = 100 ms → radius = trunc(70/100) = 0, so each anchor reads exactly
 * one frame — no blending between phases.
 */
function buildFrames(
  overrides: Record<number, Keypoint2D> = {},
): PoseFrame2D[] {
  return Array.from({ length: 13 }, (_, i) =>
    armFrame(i, overrides[i] ?? WRIST_90),
  )
}

// ---------------------------------------------------------------------------
// Paired cycle
// ---------------------------------------------------------------------------

describe('extractPerPhase — paired cycle', () => {
  // backswing: startFrame=0, peak=2, endFrame=4
  // drive:     startFrame=5, peak=8, endFrame=12
  // backswing phase anchor = drive.startFrame = 5  (NOT backswing.startFrame = 0)
  // Frame 0: wrist WRIST_90  → elbow ~90°  (backswing.startFrame — NOT the phase anchor)
  // Frame 5: wrist WRIST_60  → elbow ~60°  (drive.startFrame = backswing phase anchor)
  // Frame 8: wrist WRIST_120 → elbow ~120° (contact anchor)
  // Frame 12: default WRIST_90 → elbow ~90° (followthrough anchor)
  const frames = buildFrames({ 5: WRIST_60, 8: WRIST_120 })
  const backswing = stroke(0, 0, 2, 4)      // raw dropped peak
  const drive     = stroke(1, 5, 8, 12)     // forward drive: start=5, peak=8, end=12
  const cycle: StrokeCycle2D = makeCycle(backswing, drive)

  const result = extractPerPhase(cycle, frames, 'right', 1, 100)

  it('knee_bend has backswing and contact keys', () => {
    const r = result[METRIC.KNEE_BEND]
    expect(r).toBeDefined()
    expect('backswing' in r).toBe(true)
    expect('contact' in r).toBe(true)
    expect('followthrough' in r).toBe(false)
  })

  it('hip_flexion has backswing and contact keys', () => {
    const r = result[METRIC.HIP_FLEXION]
    expect(r).toBeDefined()
    expect('backswing' in r).toBe(true)
    expect('contact' in r).toBe(true)
    expect('followthrough' in r).toBe(false)
  })

  it('elbow_angle has backswing and contact keys', () => {
    const r = result[METRIC.ELBOW_ANGLE]
    expect(r).toBeDefined()
    expect('backswing' in r).toBe(true)
    expect('contact' in r).toBe(true)
    expect('followthrough' in r).toBe(false)
  })

  it('shoulder_angle has contact and followthrough keys only', () => {
    const r = result[METRIC.SHOULDER_ANGLE]
    expect(r).toBeDefined()
    expect('backswing' in r).toBe(false)
    expect('contact' in r).toBe(true)
    expect('followthrough' in r).toBe(true)
  })

  it('shoulder_angle followthrough reads from drive.endFrame and differs from contact when elbow differs between anchors', () => {
    // shoulderAngle = hip–shoulder–elbow angle. We need the hip off the shoulder–elbow
    // axis and a sane result (not 180°). Build dedicated frames with hip at (0.30, 0.42)
    // so the hip–shoulder–elbow triangle is non-collinear.
    //
    // Frame layout (same cycle: drive startFrame=5, peakFrame=8, endFrame=12):
    //   frame 8  (contact):      elbow at (0.50, 0.42) → hip–shoulder–elbow ≈ one angle
    //   frame 12 (followthrough): elbow at (0.50, 0.62) → hip–shoulder–elbow ≈ different angle
    // shoulderAngle = hip–shoulder–elbow (vertex at shoulder).
    // To get distinct angles at the two anchors, vary the elbow position laterally
    // so the hip–shoulder–elbow included angle changes noticeably.
    //   Frame 8  (contact):      elbow at (0.30, 0.42) — elbow to the left → acute angle
    //   Frame 12 (followthrough): elbow at (0.70, 0.42) — elbow to the right → obtuse angle
    function shoulderFrameAt(idx: number, elbowX: number): PoseFrame2D {
      const kp: Keypoint2D[] = Array.from({ length: 17 }, () => K(0.5, 0.5))
      kp[Coco17.RIGHT_HIP]      = K(0.70, 0.62)  // hip off-axis to the right
      kp[Coco17.RIGHT_SHOULDER] = K(0.50, 0.22)
      kp[Coco17.RIGHT_ELBOW]    = K(elbowX, 0.42)
      kp[Coco17.RIGHT_WRIST]    = K(elbowX + 0.10, 0.55)
      return { frameIndex: idx, timestampMs: idx * 100, keypoints: kp }
    }
    const shoulderFrames = Array.from({ length: 13 }, (_, i) => {
      if (i === 8)  return shoulderFrameAt(i, 0.30)   // elbow left → one angle
      if (i === 12) return shoulderFrameAt(i, 0.70)   // elbow right → different angle
      return shoulderFrameAt(i, 0.50)
    })
    const bsDrive = stroke(0, 0, 2, 4)
    const fwDrive = stroke(1, 5, 8, 12)
    const shlCycle: StrokeCycle2D = makeCycle(bsDrive, fwDrive)
    const sr = extractPerPhase(shlCycle, shoulderFrames, 'right', 1, 100)

    const shlResult = sr[METRIC.SHOULDER_ANGLE]
    const ctVal = shlResult['contact']
    const ftVal = shlResult['followthrough']

    // Both phases must yield a valid (non-null) sane shoulder angle.
    expect(ctVal).not.toBeNull()
    expect(ftVal).not.toBeNull()
    // The two anchor frames have different elbow positions → different hip–shoulder–elbow angles.
    expect(ftVal).not.toBeCloseTo(ctVal as number, 1)
  })

  it('torso_lean has contact key only', () => {
    const r = result[METRIC.TORSO_LEAN]
    expect(r).toBeDefined()
    expect('backswing' in r).toBe(false)
    expect('contact' in r).toBe(true)
    expect('followthrough' in r).toBe(false)
  })

  it('shoulder_tilt is NOT in perPhase result (excluded from METRIC_PHASES)', () => {
    expect(METRIC.SHOULDER_TILT in result).toBe(false)
  })

  it('elbow_angle backswing and contact differ when poses differ between anchors', () => {
    // Frame 5 (backswing anchor = drive.startFrame) has WRIST_60 → ~60°
    // Frame 8 (contact anchor  = drive.peakFrame)   has WRIST_120 → ~120°
    // With single-frame radius (100ms interval, 70ms radius → radius=0) these must differ.
    const r = result[METRIC.ELBOW_ANGLE]
    const bsVal = r['backswing']
    const ctVal = r['contact']
    expect(bsVal).not.toBeNull()
    expect(ctVal).not.toBeNull()
    // The two anchor frames have different wrist positions → different angles.
    expect(bsVal).not.toBeCloseTo(ctVal as number, 1)
  })

  it('elbow_angle backswing reads from drive.startFrame (frame 5, WRIST_60), NOT backswing.startFrame (frame 0, WRIST_90)', () => {
    // Frame 5 (drive.startFrame = backswing phase anchor) has WRIST_60 → ~60°.
    // Frame 0 (backswing.startFrame — NOT the anchor)    has WRIST_90 → ~90°.
    // WRIST_60 is more acute than WRIST_90, so bsVal < 90° and is distinctly
    // different from the WRIST_90 value at frame 0.
    // Compute a reference angle at frame 0 (WRIST_90) by querying a standalone
    // single-frame cycle anchored at frame 0 so we can compare numerically.
    const frame0Only = buildFrames()   // all frames default WRIST_90
    const refDrive   = stroke(99, 0, 0, 0)
    const refCycle   = makeCycle(stroke(98, 0, 0, 0), refDrive)
    const refResult  = extractPerPhase(refCycle, frame0Only, 'right', 1, 100)
    const angleAtFrame0 = refResult[METRIC.ELBOW_ANGLE]['backswing'] as number

    const r = result[METRIC.ELBOW_ANGLE]
    const bsVal = r['backswing'] as number

    // The backswing phase value should come from frame 5 (WRIST_60), not frame 0 (WRIST_90).
    expect(bsVal).not.toBeCloseTo(angleAtFrame0, 1)
    // WRIST_60 is more acute → smaller angle than WRIST_90.
    expect(bsVal).toBeLessThan(angleAtFrame0)
  })

  it('elbow_angle contact reads from drive.peakFrame (frame 8, WRIST_120)', () => {
    // Frame 8 has WRIST_120 → larger angle than the backswing anchor (frame 5, WRIST_60).
    const r = result[METRIC.ELBOW_ANGLE]
    expect(r['contact']).toBeGreaterThan(r['backswing'] as number)
  })
})

// ---------------------------------------------------------------------------
// Unpaired cycle (backswing == null)
// ---------------------------------------------------------------------------

describe('extractPerPhase — unpaired cycle', () => {
  const frames = buildFrames({ 5: WRIST_120 })
  const drive = stroke(0, 0, 5, 9)
  const cycle: StrokeCycle2D = makeCycle(null, drive)   // backswing = null

  const result = extractPerPhase(cycle, frames, 'right', 1, 100)

  it('no metric has a backswing key when cycle is unpaired', () => {
    for (const metricKey of Object.keys(result)) {
      expect('backswing' in result[metricKey]).toBe(false)
    }
  })

  it('knee_bend still has contact key', () => {
    expect('contact' in result[METRIC.KNEE_BEND]).toBe(true)
  })

  it('shoulder_angle still has contact and followthrough keys', () => {
    const r = result[METRIC.SHOULDER_ANGLE]
    expect('contact' in r).toBe(true)
    expect('followthrough' in r).toBe(true)
  })

  it('elbow_angle still has contact key with a valid value', () => {
    const r = result[METRIC.ELBOW_ANGLE]
    expect('contact' in r).toBe(true)
    // contact anchor = frame 5 (WRIST_120) → angle present and sane
    expect(r['contact']).not.toBeNull()
    expect(r['contact']).toBeGreaterThan(20)
    expect(r['contact']).toBeLessThan(170)
  })
})

// ---------------------------------------------------------------------------
// METRIC_PHASES config sanity
// ---------------------------------------------------------------------------

describe('METRIC_PHASES config', () => {
  it('is a curated subset: exactly {knee_bend, hip_flexion, elbow_angle, shoulder_angle, torso_lean}', () => {
    const configuredKeys = Object.keys(METRIC_PHASES).sort()
    const expectedKeys = [
      METRIC.ELBOW_ANGLE, METRIC.SHOULDER_ANGLE, METRIC.KNEE_BEND,
      METRIC.TORSO_LEAN, METRIC.HIP_FLEXION,
    ].sort()
    expect(configuredKeys).toEqual(expectedKeys)
  })

  it('shoulder_tilt is NOT a key of METRIC_PHASES (dropped from per-phase, rendered as single-instant colored cell)', () => {
    expect(METRIC.SHOULDER_TILT in METRIC_PHASES).toBe(false)
  })

  it('only contains valid Phase values', () => {
    const validPhases: Phase[] = ['backswing', 'contact', 'followthrough']
    for (const phases of Object.values(METRIC_PHASES)) {
      for (const p of phases) {
        expect(validPhases).toContain(p)
      }
    }
  })
})
