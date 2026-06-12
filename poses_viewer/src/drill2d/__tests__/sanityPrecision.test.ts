import { describe, expect, it } from 'vitest'
import { isSane } from '../sanityBounds'
import { precisionFor } from '../metricPrecision'

describe('sanityBounds', () => {
  it('passes an in-band elbow angle', () => {
    expect(isSane('elbow_angle', 130)).toBe(true)
  })
  it('drops an out-of-band elbow angle (tracking glitch)', () => {
    expect(isSane('elbow_angle', 5)).toBe(false)
  })
  it('passes metrics without a registered band', () => {
    expect(isSane('unknown_metric', 9999)).toBe(true)
  })
})

describe('metricPrecision', () => {
  it('the five in-plane metrics are precise-degrees', () => {
    expect(precisionFor('elbow_angle')).toBe('precise_degrees')
    expect(precisionFor('shoulder_tilt')).toBe('precise_degrees')
  })
  it('unknown metrics default to qualitative', () => {
    expect(precisionFor('wrist_snap')).toBe('qualitative')
  })
})
