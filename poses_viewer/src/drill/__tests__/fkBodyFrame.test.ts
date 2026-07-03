import { describe, it, expect } from 'vitest'
import { STANDING_POSE, NEUTRAL_POSE } from '../neutralPose'
import { resolveBones, buildBodyFrame, buildHeadFace, GROUND_ANCHOR_Y } from '../fkBodyFrame'
import type { PoseAnchor } from '../PoseAnchor'
import type { Landmark } from '../../types'
import { LANDMARK_COUNT } from '../SkeletonModel'

// ── Helpers ────────────────────────────────────────────────────────────────────

function approx(a: number, b: number, eps = 1e-6): boolean {
  return Math.abs(a - b) < eps
}

function vecApprox(a: [number, number, number], b: [number, number, number], eps = 1e-6): boolean {
  return approx(a[0], b[0], eps) && approx(a[1], b[1], eps) && approx(a[2], b[2], eps)
}

function makeAnchor(overrides: Partial<PoseAnchor>): PoseAnchor {
  return { ...STANDING_POSE, ...overrides }
}

// ── resolveBones ───────────────────────────────────────────────────────────────

describe('resolveBones', () => {
  it('returns canonical BONES when no override supplied', () => {
    const B = resolveBones()
    expect(B.torso).toBe(0.32)
    expect(B.shoulderWidth).toBe(0.30)
    expect(B.thigh).toBe(0.28)
  })

  it('applies override fields and leaves others as canonical', () => {
    const B = resolveBones({ torso: 0.40, thigh: 0.30 })
    expect(B.torso).toBe(0.40)
    expect(B.thigh).toBe(0.30)
    expect(B.shin).toBe(0.26)   // canonical
    expect(B.upperArm).toBe(0.22)
  })
})

// ── GROUND_ANCHOR_Y ────────────────────────────────────────────────────────────

describe('GROUND_ANCHOR_Y', () => {
  it('equals 0.92', () => {
    expect(GROUND_ANCHOR_Y).toBe(0.92)
  })
})

// ── buildBodyFrame: basic axes ─────────────────────────────────────────────────

describe('buildBodyFrame — axes', () => {
  it('at figureYawDeg=0: legForward ≈ [0,0,-1]', () => {
    const B = resolveBones()
    const frame = buildBodyFrame(STANDING_POSE, B)
    expect(frame.legForward[0]).toBeCloseTo(0, 6)
    expect(frame.legForward[1]).toBeCloseTo(0, 6)
    expect(frame.legForward[2]).toBeCloseTo(-1, 6)
  })

  it('at figureYawDeg=0: legAcross ≈ [1,0,0]', () => {
    const B = resolveBones()
    const frame = buildBodyFrame(STANDING_POSE, B)
    expect(frame.legAcross[0]).toBeCloseTo(1, 6)
    expect(frame.legAcross[1]).toBeCloseTo(0, 6)
    expect(frame.legAcross[2]).toBeCloseTo(0, 6)
  })

  it('at pelvicRollDeg=0: across equals acrossLevel (same vector)', () => {
    const B = resolveBones()
    const frame = buildBodyFrame(STANDING_POSE, B)
    // pelvicRollDeg is 0 in STANDING_POSE → fast-path: across = acrossLevel
    expect(frame.across[0]).toBeCloseTo(frame.acrossLevel[0], 10)
    expect(frame.across[1]).toBeCloseTo(frame.acrossLevel[1], 10)
    expect(frame.across[2]).toBeCloseTo(frame.acrossLevel[2], 10)
  })

  it('non-zero pelvicRoll produces across ≠ acrossLevel', () => {
    const B = resolveBones()
    const anchor = makeAnchor({ pelvicRollDeg: 15 })
    const frame = buildBodyFrame(anchor, B)
    const same = vecApprox(frame.across, frame.acrossLevel, 1e-4)
    expect(same).toBe(false)
  })

  it('torsoSideBendDeg=0 → torsoUp is purely in sagittal plane (x ≈ 0 at yaw=0)', () => {
    const B = resolveBones()
    // At figureYawDeg=0, bodyRotationDeg=0, torsoSideBendDeg=0: acrossLevel = [1,0,0],
    // forward = [0,0,-1]. torsoUp is rotated in the sagittal (XZ is coronal, YZ sagittal)
    // plane. For yaw=0, shoulderRotation=0, torsoUp.x should be ≈ 0.
    const anchor = makeAnchor({ torsoTiltDeg: 20, torsoSideBendDeg: 0 })
    const frame = buildBodyFrame(anchor, B)
    expect(frame.torsoUp[0]).toBeCloseTo(0, 5)
  })

  it('torsoSideBendDeg≠0 gives torsoUp out of sagittal plane', () => {
    const B = resolveBones()
    const anchor = makeAnchor({ torsoTiltDeg: 10, torsoSideBendDeg: 20 })
    const frame = buildBodyFrame(anchor, B)
    // torsoUp.x should no longer be ≈ 0
    expect(Math.abs(frame.torsoUp[0])).toBeGreaterThan(0.01)
  })
})

// ── buildBodyFrame: symmetry ───────────────────────────────────────────────────

describe('buildBodyFrame — symmetry', () => {
  it('hips are symmetric around hipMid.x (at yaw=0)', () => {
    const B = resolveBones()
    const frame = buildBodyFrame(STANDING_POSE, B)
    const midX = frame.hipMid[0]
    // lHip should be offset +hipWidth/2 from mid, rHip offset -hipWidth/2
    expect(frame.lHip[0] - midX).toBeCloseTo(-(frame.rHip[0] - midX), 5)
  })

  it('shoulders are symmetric around shoulder midpoint x (at yaw=0)', () => {
    const B = resolveBones()
    const frame = buildBodyFrame(STANDING_POSE, B)
    const midX = frame.shoulderMid[0]
    expect(frame.lShoulder[0] - midX).toBeCloseTo(-(frame.rShoulder[0] - midX), 5)
  })

  it('shoulderMid y equals lShoulder y (shoulders on same horizontal line)', () => {
    const B = resolveBones()
    const frame = buildBodyFrame(STANDING_POSE, B)
    expect(frame.lShoulder[1]).toBeCloseTo(frame.rShoulder[1], 6)
  })
})

// ── buildBodyFrame: torsoDown ──────────────────────────────────────────────────

describe('buildBodyFrame — torsoDown', () => {
  it('torsoDown = -torsoUp', () => {
    const B = resolveBones()
    const frame = buildBodyFrame(NEUTRAL_POSE, B)
    expect(frame.torsoDown[0]).toBeCloseTo(-frame.torsoUp[0], 10)
    expect(frame.torsoDown[1]).toBeCloseTo(-frame.torsoUp[1], 10)
    expect(frame.torsoDown[2]).toBeCloseTo(-frame.torsoUp[2], 10)
  })
})

// ── buildBodyFrame: knee clamping ──────────────────────────────────────────────

describe('buildBodyFrame — knee clamping', () => {
  it('knee angle < 30 is clamped to 30', () => {
    const B = resolveBones()
    const anchor = makeAnchor({ leftKneeAngleDeg: 10, rightKneeAngleDeg: 20 })
    const frame = buildBodyFrame(anchor, B)
    expect(frame.effLeftKnee).toBe(30)
    expect(frame.effRightKnee).toBe(30)
  })

  it('knee angle ≥ 30 passes through unchanged (at TILT_TO_KNEE_BEND=0)', () => {
    const B = resolveBones()
    const anchor = makeAnchor({ leftKneeAngleDeg: 90, rightKneeAngleDeg: 130 })
    const frame = buildBodyFrame(anchor, B)
    expect(frame.effLeftKnee).toBe(90)
    expect(frame.effRightKnee).toBe(130)
  })

  it('knee angle 180 (straight) passes through as 180', () => {
    const B = resolveBones()
    const frame = buildBodyFrame(STANDING_POSE, B)
    expect(frame.effLeftKnee).toBe(180)
    expect(frame.effRightKnee).toBe(180)
  })
})

// ── buildBodyFrame: hipMid with TILT_TO_HIP_BACK=0 ───────────────────────────

describe('buildBodyFrame — hipMid', () => {
  it('hipMid matches anchor hipMidX/Y at z=0 (TILT_TO_HIP_BACK=0)', () => {
    const B = resolveBones()
    const anchor = makeAnchor({ torsoTiltDeg: 40, hipMidX: 0.55, hipMidY: 0.44 })
    const frame = buildBodyFrame(anchor, B)
    // TILT_TO_HIP_BACK=0 means hipBackShift=0 → hipMid = [hipMidX, hipMidY, 0]
    expect(frame.hipMid[0]).toBeCloseTo(0.55, 8)
    expect(frame.hipMid[1]).toBeCloseTo(0.44, 8)
    expect(frame.hipMid[2]).toBeCloseTo(0, 8)
  })
})

// ── buildHeadFace ──────────────────────────────────────────────────────────────

describe('buildHeadFace', () => {
  function runHeadFace(anchor: PoseAnchor) {
    const B = resolveBones()
    const frame = buildBodyFrame(anchor, B)
    const out: Landmark[] = new Array(LANDMARK_COUNT)
    buildHeadFace(
      frame.shoulderMid,
      frame.torsoUp,
      frame.torsoDown,
      frame.shoulderAcross,
      B.headToShoulder,
      out,
    )
    return { out, frame, B }
  }

  it('NOSE is above shoulderMid (lower y = higher in screen = closer to head)', () => {
    const { out, frame } = runHeadFace(STANDING_POSE)
    // y-down convention: nose.y < shoulderMid.y means nose is above shoulderMid
    expect(out[0].y).toBeLessThan(frame.shoulderMid[1])
  })

  it('NOSE is offset from shoulderMid by headToShoulder * 0.75 along torsoUp', () => {
    const B = resolveBones()
    const frame = buildBodyFrame(STANDING_POSE, B)
    const out: Landmark[] = new Array(LANDMARK_COUNT)
    buildHeadFace(frame.shoulderMid, frame.torsoUp, frame.torsoDown, frame.shoulderAcross, B.headToShoulder, out)
    const nose = out[0]
    const expectedY = frame.shoulderMid[1] + frame.torsoUp[1] * B.headToShoulder * 0.75
    expect(nose.y).toBeCloseTo(expectedY, 8)
  })

  it('all 11 face landmarks (0–10) are written into out', () => {
    const { out } = runHeadFace(STANDING_POSE)
    for (let i = 0; i <= 10; i++) {
      expect(out[i]).toBeDefined()
      expect(out[i].index).toBe(i)
    }
  })

  it('eyes are symmetric around nose.x at yaw=0', () => {
    const { out } = runHeadFace(STANDING_POSE)
    const nose = out[0]
    // L_EYE_INNER (1) and R_EYE_INNER (4) should be ±equal distance from nose.x
    expect(out[1].x - nose.x).toBeCloseTo(-(out[4].x - nose.x), 6)
  })

  it('mouth landmarks are below nose (higher y in y-down convention)', () => {
    const { out } = runHeadFace(STANDING_POSE)
    const nose = out[0]
    expect(out[9].y).toBeGreaterThan(nose.y)   // MOUTH_L
    expect(out[10].y).toBeGreaterThan(nose.y)  // MOUTH_R
  })

  it('ears are further from nose.x than outer eyes', () => {
    const { out } = runHeadFace(STANDING_POSE)
    const nose = out[0]
    const lEarDist = Math.abs(out[7].x - nose.x)   // L_EAR
    const lEyeOuterDist = Math.abs(out[3].x - nose.x) // L_EYE_OUTER
    expect(lEarDist).toBeGreaterThan(lEyeOuterDist)
  })
})
