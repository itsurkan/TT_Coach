import { describe, it, expect } from 'vitest'
import { polarToFlexAbd, flexAbdToPolar } from '../polarShoulder'

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
