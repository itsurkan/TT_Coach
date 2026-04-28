import { describe, it, expect } from 'vitest'
import {
  rightShoulderAbdMin,
  rightShoulderFlexMax,
  clampRightShoulder,
  RIGHT_SHOULDER_FLEX_KNEE,
  RIGHT_SHOULDER_FLEX_CEIL,
  RIGHT_SHOULDER_ABD_AT_KNEE,
  RIGHT_SHOULDER_ABD_AT_CEIL,
} from '../shoulderClamp'

describe('rightShoulderAbdMin', () => {
  it('is -40 at flex=100 (sample A)', () => {
    expect(rightShoulderAbdMin(100)).toBe(-40)
  })
  it('is 70 at flex=180 (sample B)', () => {
    expect(rightShoulderAbdMin(180)).toBe(70)
  })
  it('is flat at -40 below the knee', () => {
    expect(rightShoulderAbdMin(50)).toBe(-40)
    expect(rightShoulderAbdMin(-30)).toBe(-40)
  })
  it('is flat at 70 at or above the ceiling', () => {
    expect(rightShoulderAbdMin(200)).toBe(70)
  })
  it('interpolates linearly at flex=140 (midpoint)', () => {
    expect(rightShoulderAbdMin(140)).toBeCloseTo(15, 5)
  })
})

describe('rightShoulderFlexMax', () => {
  it('returns the knee flex at abd=-40', () => {
    expect(rightShoulderFlexMax(-40)).toBe(RIGHT_SHOULDER_FLEX_KNEE)
  })
  it('returns the ceiling flex at abd=70', () => {
    expect(rightShoulderFlexMax(70)).toBe(RIGHT_SHOULDER_FLEX_CEIL)
  })
  it('clamps to ceiling flex when abd > 70', () => {
    expect(rightShoulderFlexMax(120)).toBe(RIGHT_SHOULDER_FLEX_CEIL)
  })
  it('clamps to knee flex when abd < -40', () => {
    expect(rightShoulderFlexMax(-80)).toBe(RIGHT_SHOULDER_FLEX_KNEE)
  })
  it('is the inverse of rightShoulderAbdMin on the sloped segment', () => {
    // abd=15 at flex=140 from the other test; round-trip
    expect(rightShoulderFlexMax(15)).toBeCloseTo(140, 5)
  })
})

describe('clampRightShoulder', () => {
  it('passes through valid combinations unchanged', () => {
    const out = clampRightShoulder(41, 31, 'rightShoulderAngleDeg')
    expect(out).toEqual({ flex: 41, abd: 31 })
  })
  it('leaves sample A untouched (on the bound)', () => {
    const out = clampRightShoulder(100, -40, 'rightShoulderAngleDeg')
    expect(out).toEqual({ flex: 100, abd: -40 })
  })
  it('leaves sample B untouched (on the bound)', () => {
    const out = clampRightShoulder(180, 70, 'rightShoulderAbductionDeg')
    expect(out).toEqual({ flex: 180, abd: 70 })
  })
  it('raises abd when flex is the active key at flex=180, abd=0', () => {
    const out = clampRightShoulder(180, 0, 'rightShoulderAngleDeg')
    expect(out.flex).toBe(180)
    expect(out.abd).toBe(70)
  })
  it('lowers flex when abd is the active key at flex=180, abd=0', () => {
    const out = clampRightShoulder(180, 0, 'rightShoulderAbductionDeg')
    expect(out.abd).toBe(0)
    // abd_min(flex)=0 → flex = 100 + (0-(-40))/(70-(-40)) * 80 ≈ 129.09
    expect(out.flex).toBeCloseTo(129.09, 1)
  })
  it('raises abd to 15 when flex=140 is the active key and abd=0', () => {
    const out = clampRightShoulder(140, 0, 'rightShoulderAngleDeg')
    expect(out.flex).toBe(140)
    expect(out.abd).toBeCloseTo(15, 5)
  })
  it('leaves sub-knee flex alone (constraint inactive)', () => {
    const out = clampRightShoulder(50, -40, 'rightShoulderAngleDeg')
    expect(out).toEqual({ flex: 50, abd: -40 })
  })
})
