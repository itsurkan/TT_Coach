import { describe, it, expect } from 'vitest'
import { polarToFlexAbd, flexAbdToPolar } from '../polarShoulder'
import { STANDING_POSE } from '../neutralPose'
import { reconstructFromAnchor } from '../skeletonReconstructor'
import { LM } from '../SkeletonModel'
import { ANCHOR_PARAM_GROUPS } from '../PoseAnchor'

describe('polarToFlexAbd', () => {
  it('elevation=0 → arm straight down (flex=0, abd=0), plane irrelevant', () => {
    const out = polarToFlexAbd({ elevation: 0, plane: 0 })
    expect(out.flex).toBeCloseTo(0, 6)
    expect(out.abd).toBeCloseTo(0, 6)
    // plane is arbitrary at the pole — any value must still give flex=0, abd=0
    const out2 = polarToFlexAbd({ elevation: 0, plane: 137 })
    expect(out2.flex).toBeCloseTo(0, 6)
    expect(out2.abd).toBeCloseTo(0, 6)
  })
  it('elevation=180 → arm straight up (flex=180, abd=0)', () => {
    const out = polarToFlexAbd({ elevation: 180, plane: 0 })
    expect(out.flex).toBeCloseTo(180, 6)
    expect(out.abd).toBeCloseTo(0, 6)
  })
  it('elevation=90, plane=0 → pure sagittal forward (flex=90, abd=0)', () => {
    const out = polarToFlexAbd({ elevation: 90, plane: 0 })
    expect(out.flex).toBeCloseTo(90, 6)
    expect(out.abd).toBeCloseTo(0, 6)
  })
  it('elevation=90, plane=90 → pure lateral (flex=0, abd=90)', () => {
    const out = polarToFlexAbd({ elevation: 90, plane: 90 })
    expect(out.flex).toBeCloseTo(0, 6)
    expect(out.abd).toBeCloseTo(90, 6)
  })
  it('elevation=90, plane=-90 → cross-body (flex=0, abd=-90)', () => {
    const out = polarToFlexAbd({ elevation: 90, plane: -90 })
    expect(out.flex).toBeCloseTo(0, 6)
    expect(out.abd).toBeCloseTo(-90, 6)
  })
  it('elevation=90, plane=180 → backward reach (flex=-90, abd=0)', () => {
    // cos(plane)=cos(180)=-1, sin(elevation)=1, cos(elevation)=0
    // flex = atan2(-1, 0) = -90; sin(plane)=0 so abd=0
    const out = polarToFlexAbd({ elevation: 90, plane: 180 })
    expect(out.flex).toBeCloseTo(-90, 6)
    expect(out.abd).toBeCloseTo(0, 6)
  })
  it('elevation=45, plane=45 → symmetric diagonal', () => {
    // flex = atan2(cos45·sin45, cos45) = atan2(0.5, √2/2) ≈ 35.2644°
    // abd  = asin(sin45·sin45) = asin(0.5) = 30°
    const out = polarToFlexAbd({ elevation: 45, plane: 45 })
    expect(out.flex).toBeCloseTo(35.2644, 3)
    expect(out.abd).toBeCloseTo(30, 3)
  })
})

describe('flexAbdToPolar', () => {
  it('flex=0, abd=0 → elevation=0 (pole), plane=0', () => {
    const out = flexAbdToPolar({ flex: 0, abd: 0 })
    expect(out.elevation).toBeCloseTo(0, 6)
    expect(out.plane).toBeCloseTo(0, 6)
  })
  it('flex=180, abd=0 → elevation=180 (pole), plane=0', () => {
    const out = flexAbdToPolar({ flex: 180, abd: 0 })
    expect(out.elevation).toBeCloseTo(180, 6)
    expect(out.plane).toBeCloseTo(0, 6)
  })
  it('flex=90, abd=0 → elevation=90, plane=0 (sagittal forward)', () => {
    const out = flexAbdToPolar({ flex: 90, abd: 0 })
    expect(out.elevation).toBeCloseTo(90, 6)
    expect(out.plane).toBeCloseTo(0, 6)
  })
  it('flex=0, abd=90 → elevation=90, plane=90 (pure lateral)', () => {
    const out = flexAbdToPolar({ flex: 0, abd: 90 })
    expect(out.elevation).toBeCloseTo(90, 6)
    expect(out.plane).toBeCloseTo(90, 6)
  })
  it('flex=0, abd=-90 → elevation=90, plane=-90 (cross-body)', () => {
    const out = flexAbdToPolar({ flex: 0, abd: -90 })
    expect(out.elevation).toBeCloseTo(90, 6)
    expect(out.plane).toBeCloseTo(-90, 6)
  })
})

describe('round-trip: polar → rect → polar', () => {
  it('identity on 7x7 grid excluding the two poles', () => {
    const elevations = [10, 30, 60, 90, 120, 150, 170]
    const planes = [-60, -30, 0, 45, 90, 135, 170]
    for (const elevation of elevations) {
      for (const plane of planes) {
        const rect = polarToFlexAbd({ elevation, plane })
        const back = flexAbdToPolar(rect)
        expect(back.elevation).toBeCloseTo(elevation, 4)
        expect(back.plane).toBeCloseTo(plane, 4)
      }
    }
  })
  it('rect → polar → rect identity on 5x5 grid within the polar-reachable region', () => {
    // abd is bounded to (-90, 90) because ±90 is the lateral pole where flex
    // becomes indeterminate. flex can span the full rect-slider range.
    const flexes = [-30, 0, 45, 90, 135, 180]
    const abds = [-40, -20, 0, 30, 60, 85]
    for (const flex of flexes) {
      for (const abd of abds) {
        const polar = flexAbdToPolar({ flex, abd })
        const back = polarToFlexAbd(polar)
        expect(back.flex).toBeCloseTo(flex, 4)
        expect(back.abd).toBeCloseTo(abd, 4)
      }
    }
  })
})

describe('side-agnosticism: polar values mirror across midline when applied equally', () => {
  it('polar(elevation=60, plane=45) on both shoulders → elbows mirrored in x around hipMidX', () => {
    const rect = polarToFlexAbd({ elevation: 60, plane: 45 })
    // Apply the same polar (= same rect) to both sides. Figure faces camera
    // (figureYawDeg=0), torso upright. FK's internal abSign flip should do
    // the visual mirroring.
    const anchor = {
      ...STANDING_POSE,
      rightShoulderAngleDeg: rect.flex,
      rightShoulderAbductionDeg: rect.abd,
      leftShoulderAngleDeg: rect.flex,
      leftShoulderAbductionDeg: rect.abd,
    }
    const lms = reconstructFromAnchor(anchor)
    const rElbow = lms[LM.R_ELBOW]
    const lElbow = lms[LM.L_ELBOW]
    const rShoulder = lms[LM.R_SHOULDER]
    const lShoulder = lms[LM.L_SHOULDER]
    // Midline x is the midpoint of the two shoulders (figure faces camera,
    // hips are symmetric, so shoulder midline ≈ hip midline in x).
    const midX = (rShoulder.x + lShoulder.x) / 2
    // Distance from the midline should match on both sides (mirrored).
    expect(Math.abs(rElbow.x - midX)).toBeCloseTo(Math.abs(lElbow.x - midX), 4)
    // And the y/z components should be identical (symmetric plane).
    expect(rElbow.y).toBeCloseTo(lElbow.y, 4)
    expect(rElbow.z).toBeCloseTo(lElbow.z, 4)
  })
})

describe('ANCHOR_PARAM_GROUPS: polar shoulder entries', () => {
  const all = ANCHOR_PARAM_GROUPS.flatMap(g => g.params)
  const findById = (id: string) =>
    all.find(p => p.kind === 'computed' && p.id === id)

  it('adds four computed entries (2 per arm)', () => {
    const computed = all.filter(p => p.kind === 'computed')
    expect(computed).toHaveLength(4)
    expect(computed.map(p => (p as any).id).sort()).toEqual([
      'leftShoulderElevationDeg',
      'leftShoulderPlaneDeg',
      'rightShoulderElevationDeg',
      'rightShoulderPlaneDeg',
    ])
  })

  it('right-arm polar specs reference both right-shoulder anchor keys', () => {
    const e = findById('rightShoulderElevationDeg')
    const p = findById('rightShoulderPlaneDeg')
    expect(e).toBeDefined()
    expect(p).toBeDefined()
    expect((e as any).keys).toEqual(['rightShoulderAngleDeg', 'rightShoulderAbductionDeg'])
    expect((p as any).keys).toEqual(['rightShoulderAngleDeg', 'rightShoulderAbductionDeg'])
  })

  it('read/write round-trips through a sample anchor', () => {
    const e = findById('rightShoulderElevationDeg') as any
    const anchor = {
      rightShoulderAngleDeg: 90,
      rightShoulderAbductionDeg: 0,
    } as any
    // With flex=90, abd=0 the arm is horizontal forward → elevation=90.
    expect(e.read(anchor)).toBeCloseTo(90, 4)
    // Writing elevation=45 at plane=0 → flex=45, abd=0.
    const updated = e.write(anchor, 45)
    expect(updated.rightShoulderAngleDeg).toBeCloseTo(45, 4)
    expect(updated.rightShoulderAbductionDeg).toBeCloseTo(0, 4)
  })
})
