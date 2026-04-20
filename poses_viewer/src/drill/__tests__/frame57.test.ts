/**
 * End-to-end test: extract anchor from andrii_1 frame 57, reconstruct via FK,
 * and assert the rendered pose is biomechanically sensible.
 *
 * Frame 57 is a deep forehand-drive stance (wide legs, heavy forward bend,
 * right arm holding racket). Past bugs: legs folded backward, torso snapped
 * to horizontal, head disconnected from body.
 */

import { describe, it, expect } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import { extractAnchorFromLandmarks, extractBoneLengths, extractLimbDirections, parsePoseFixture } from '../anchorExtractor'
import { reconstructFromAnchor, GROUND_ANCHOR_Y } from '../skeletonReconstructor'
import { LM } from '../SkeletonModel'
import type { Landmark } from '../../types'

const FIXTURE_PATH = path.resolve(
  __dirname, '..', '..', '..', '..', 'Videos', 'andrii_1', 'andrii_1_poses.json'
)

const hasFixture = fs.existsSync(FIXTURE_PATH)

function loadFrame(idx: number): Landmark[] {
  const raw = JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf-8'))
  const fixture = parsePoseFixture(raw)
  return fixture.frames[idx].landmarks
}

describe('frame 57 (andrii_1 forehand-drive stance)', () => {
  if (!hasFixture) {
    it.skip('fixture missing — skipping', () => {})
    return
  }

  it('extracts a forward-bent squat anchor', () => {
    const lms = loadFrame(57)
    const a = extractAnchorFromLandmarks(lms)

    // Player's body is turned; sign depends on our FK convention (positive =
    // turned so L side comes toward camera). For frame 57 extracted ~+64°.
    expect(Math.abs(a.bodyRotationDeg)).toBeGreaterThan(30)
    expect(Math.abs(a.bodyRotationDeg)).toBeLessThan(90)

    // Heavy forward bend is the defining feature of this frame.
    expect(a.torsoTiltDeg).toBeGreaterThan(40)

    // Knees clearly bent — both well below 120°.
    expect(a.leftKneeAngleDeg).toBeLessThan(120)
    expect(a.rightKneeAngleDeg).toBeLessThan(120)

    // Wide stance.
    expect(a.stanceWidthNorm).toBeGreaterThan(0.2)

    // New params: with deep knee bend, thighs MUST be tilted forward
    // (hip flexion) otherwise the shin has to fold backward to reach ground.
    expect(a.leftThighForwardDeg + a.rightThighForwardDeg).toBeGreaterThan(20)
  })

  it('reconstructs with ankles on the ground', () => {
    const a = extractAnchorFromLandmarks(loadFrame(57))
    const frame = reconstructFromAnchor(a)

    const ankleLowest = Math.max(frame[LM.L_ANKLE].y, frame[LM.R_ANKLE].y)
    expect(ankleLowest).toBeCloseTo(GROUND_ANCHOR_Y, 3)
  })

  it('reconstructs with the hip above the knees (no upside-down legs)', () => {
    const a = extractAnchorFromLandmarks(loadFrame(57))
    console.log('[frame57] anchor', JSON.stringify(a, null, 2))
    const frame = reconstructFromAnchor(a)
    const hipY = { l: frame[LM.L_HIP].y, r: frame[LM.R_HIP].y }
    const kneeY = { l: frame[LM.L_KNEE].y, r: frame[LM.R_KNEE].y }
    const ankleY = { l: frame[LM.L_ANKLE].y, r: frame[LM.R_ANKLE].y }
    console.log('[frame57] L hip/knee/ankle Y:', hipY.l, kneeY.l, ankleY.l)
    console.log('[frame57] R hip/knee/ankle Y:', hipY.r, kneeY.r, ankleY.r)
    // Each leg individually must have knee below hip and ankle below knee.
    expect(kneeY.l).toBeGreaterThan(hipY.l)
    expect(ankleY.l).toBeGreaterThan(kneeY.l)
    expect(kneeY.r).toBeGreaterThan(hipY.r)
    expect(ankleY.r).toBeGreaterThan(kneeY.r)
  })

  it('reconstructs with head above shoulders and shoulders above hips', () => {
    const a = extractAnchorFromLandmarks(loadFrame(57))
    const frame = reconstructFromAnchor(a)
    const headY = frame[LM.NOSE].y
    const shMidY = (frame[LM.L_SHOULDER].y + frame[LM.R_SHOULDER].y) / 2
    const hipMidY = (frame[LM.L_HIP].y + frame[LM.R_HIP].y) / 2
    // For a deep forward bend the shoulders may be only slightly above hips
    // (possibly even at the same height) in 2D projection — but the head must
    // still be above the shoulders along the torso axis.
    expect(headY).toBeLessThan(shMidY + 0.01)
    expect(shMidY).toBeLessThan(hipMidY + 0.05)
  })

  it('KEY structural points match reference (feet, ankles, knees, hips, shoulders, elbows)', () => {
    const original = loadFrame(57)
    const anchor = extractAnchorFromLandmarks(original)
    // Attach unit direction vectors extracted straight from the source pose.
    // FK uses these when present instead of recomputing from angles — this
    // removes decomposition loss and makes faithful replay possible.
    anchor.dirOverrides = extractLimbDirections(original)
    const bones = extractBoneLengths(original)
    const reconstructed = reconstructFromAnchor(anchor, bones, { skipFootIK: true })

    // Just the structural points that define the pose SHAPE. Hands / fingers
    // are derivative and too noisy to test.
    const KEY: Array<[number, string]> = [
      [LM.L_SHOULDER, 'L shoulder (torso top)'],
      [LM.R_SHOULDER, 'R shoulder (torso top)'],
      [LM.L_ELBOW, 'L elbow'],
      [LM.R_ELBOW, 'R elbow'],
      [LM.L_HIP, 'L hip (torso bottom)'],
      [LM.R_HIP, 'R hip (torso bottom)'],
      [LM.L_KNEE, 'L knee'],
      [LM.R_KNEE, 'R knee'],
      [LM.L_ANKLE, 'L ankle'],
      [LM.R_ANKLE, 'R ankle'],
      [LM.L_FOOT, 'L foot tip'],
      [LM.R_FOOT, 'R foot tip'],
      [LM.L_HEEL, 'L heel'],
      [LM.R_HEEL, 'R heel'],
    ]

    const frame = (lms: Landmark[]) => {
      const cx = (lms[LM.L_HIP].x + lms[LM.R_HIP].x) / 2
      const cy = (lms[LM.L_HIP].y + lms[LM.R_HIP].y) / 2
      const sx = (lms[LM.L_SHOULDER].x + lms[LM.R_SHOULDER].x) / 2
      const sy = (lms[LM.L_SHOULDER].y + lms[LM.R_SHOULDER].y) / 2
      return { cx, cy, torsoLen: Math.hypot(sx - cx, sy - cy) || 1e-6 }
    }
    const oF = frame(original); const rF = frame(reconstructed)
    const scale = oF.torsoLen / rF.torsoLen
    const dist = (idx: number) => {
      const oDx = original[idx].x - oF.cx
      const oDy = original[idx].y - oF.cy
      const rDx = (reconstructed[idx].x - rF.cx) * scale
      const rDy = (reconstructed[idx].y - rF.cy) * scale
      return Math.hypot(oDx - rDx, oDy - rDy)
    }

    const THRESHOLD = 0.10 // ≈20cm in image coords; ≈ one hand span

    const rows: string[] = []
    let worst = { name: '', d: 0 }
    for (const [idx, name] of KEY) {
      const d = dist(idx)
      rows.push(`${name.padEnd(24)} ${d.toFixed(3)} (~${Math.round(d * 200)}cm)`)
      if (d > worst.d) worst = { name, d }
    }
    console.log('[frame57 keypoints]\n  ' + rows.join('\n  '))
    console.log(`[frame57 worst] ${worst.name} = ${worst.d.toFixed(3)} (~${Math.round(worst.d * 200)}cm)`)

    for (const [idx, name] of KEY) {
      const d = dist(idx)
      expect(d, `${name} off by ${d.toFixed(3)} (~${Math.round(d * 200)}cm)`).toBeLessThan(THRESHOLD)
    }
  })

  it('every reconstructed landmark is within 10cm (~0.05 normalized) of the source', () => {
    const original = loadFrame(57)
    const anchor = extractAnchorFromLandmarks(original)
    const reconstructed = reconstructFromAnchor(anchor)

    const KEY_LMS: Array<[number, string]> = [
      [LM.NOSE, 'nose'],
      [LM.L_SHOULDER, 'L shoulder'], [LM.R_SHOULDER, 'R shoulder'],
      [LM.L_ELBOW, 'L elbow'],       [LM.R_ELBOW, 'R elbow'],
      [LM.L_WRIST, 'L wrist'],       [LM.R_WRIST, 'R wrist'],
      [LM.L_HIP, 'L hip'],           [LM.R_HIP, 'R hip'],
      [LM.L_KNEE, 'L knee'],         [LM.R_KNEE, 'R knee'],
      [LM.L_ANKLE, 'L ankle'],       [LM.R_ANKLE, 'R ankle'],
    ]

    // Normalize by hip-mid AND torso length so we compare pose shape
    // independent of bone-length mismatch (canonical skeleton vs player's).
    // This tests whether the angle extraction + FK round-trip preserves body
    // structure. Any residual error is pure angular/placement drift.
    const normBy = (lms: Landmark[]) => {
      const cx = (lms[LM.L_HIP].x + lms[LM.R_HIP].x) / 2
      const cy = (lms[LM.L_HIP].y + lms[LM.R_HIP].y) / 2
      const sx = (lms[LM.L_SHOULDER].x + lms[LM.R_SHOULDER].x) / 2
      const sy = (lms[LM.L_SHOULDER].y + lms[LM.R_SHOULDER].y) / 2
      const torsoLen = Math.hypot(sx - cx, sy - cy) || 1e-6
      return { cx, cy, torsoLen }
    }
    const oRef = normBy(original)
    const rRef = normBy(reconstructed)
    // Express reconstruction in the original's torso-length units.
    const scale = oRef.torsoLen / rRef.torsoLen

    const THRESHOLD = 0.05 // ≈10cm in original-image normalized coords

    const distance = (idx: number) => {
      const oDx = original[idx].x - oRef.cx
      const oDy = original[idx].y - oRef.cy
      const rDx = (reconstructed[idx].x - rRef.cx) * scale
      const rDy = (reconstructed[idx].y - rRef.cy) * scale
      return Math.hypot(oDx - rDx, oDy - rDy)
    }

    const report: string[] = []
    let worst = { name: '', dist: 0 }
    for (const [idx, name] of KEY_LMS) {
      const dist = distance(idx)
      report.push(`${name}=${dist.toFixed(3)}`)
      if (dist > worst.dist) worst = { name, dist }
    }
    console.log('[frame57 distances, hip+torso-normalized]\n  ' + report.join('  '))
    console.log(`[frame57 worst] ${worst.name} off by ${worst.dist.toFixed(3)} (~${(worst.dist * 200).toFixed(0)}cm)`)

    for (const [idx, name] of KEY_LMS) {
      const dist = distance(idx)
      expect(dist, `${name} off by ${dist.toFixed(3)} (~${(dist * 200).toFixed(0)}cm)`).toBeLessThan(THRESHOLD)
    }
  })

  it('reconstructs a connected skeleton (bones within plausible lengths)', () => {
    const a = extractAnchorFromLandmarks(loadFrame(57))
    const frame = reconstructFromAnchor(a)
    const dist = (i: number, j: number) => {
      const a = frame[i]; const b = frame[j]
      return Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z)
    }
    // All bone lengths should be within [0.05, 0.5] in normalized space —
    // nothing should explode or collapse.
    for (const [p, q] of [
      [LM.L_HIP, LM.L_KNEE], [LM.R_HIP, LM.R_KNEE],
      [LM.L_KNEE, LM.L_ANKLE], [LM.R_KNEE, LM.R_ANKLE],
      [LM.L_SHOULDER, LM.L_ELBOW], [LM.R_SHOULDER, LM.R_ELBOW],
      [LM.L_ELBOW, LM.L_WRIST], [LM.R_ELBOW, LM.R_WRIST],
      [LM.L_SHOULDER, LM.R_SHOULDER], [LM.L_HIP, LM.R_HIP],
    ]) {
      const d = dist(p, q)
      expect(d).toBeGreaterThan(0.05)
      expect(d).toBeLessThan(0.5)
    }
  })
})
