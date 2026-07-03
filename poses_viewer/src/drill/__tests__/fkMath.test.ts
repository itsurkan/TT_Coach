import { describe, it, expect } from 'vitest'
import { add, scale, normalize, rotY, rotAroundAxis, mkLm, deg, type V3 } from '../fkMath'

describe('fkMath', () => {
  describe('deg', () => {
    it('converts degrees to radians', () => {
      expect(deg(180)).toBeCloseTo(Math.PI, 5)
      expect(deg(90)).toBeCloseTo(Math.PI / 2, 5)
      expect(deg(0)).toBeCloseTo(0, 5)
    })
  })

  describe('add', () => {
    it('adds two vectors', () => {
      const result = add([1, 2, 3], [4, 5, 6])
      expect(result).toEqual([5, 7, 9])
    })

    it('handles negative values', () => {
      const result = add([1, -2, 3], [-1, 2, -3])
      expect(result).toEqual([0, 0, 0])
    })
  })

  describe('scale', () => {
    it('scales a vector by a scalar', () => {
      const result = scale([1, 2, 3], 2)
      expect(result).toEqual([2, 4, 6])
    })

    it('handles zero scale', () => {
      const result = scale([1, 2, 3], 0)
      expect(result).toEqual([0, 0, 0])
    })

    it('handles negative scale', () => {
      const result = scale([1, 2, 3], -1)
      expect(result).toEqual([-1, -2, -3])
    })
  })

  describe('normalize', () => {
    it('normalizes a unit vector [3, 0, 0] to [1, 0, 0]', () => {
      const result = normalize([3, 0, 0])
      expect(result[0]).toBeCloseTo(1, 5)
      expect(result[1]).toBeCloseTo(0, 5)
      expect(result[2]).toBeCloseTo(0, 5)
    })

    it('normalizes a diagonal vector', () => {
      const result = normalize([1, 1, 1])
      const len = Math.sqrt(3)
      expect(result[0]).toBeCloseTo(1 / len, 5)
      expect(result[1]).toBeCloseTo(1 / len, 5)
      expect(result[2]).toBeCloseTo(1 / len, 5)
    })

    it('returns zero vector for zero input', () => {
      const result = normalize([0, 0, 0])
      expect(result).toEqual([0, 0, 0])
    })

    it('handles very small vectors (near-zero)', () => {
      const result = normalize([1e-10, 1e-10, 1e-10])
      expect(result).toEqual([0, 0, 0])
    })
  })

  describe('rotY', () => {
    it('is identity at 0 degrees', () => {
      const v: V3 = [1, 2, 3]
      const result = rotY(v, 0)
      expect(result[0]).toBeCloseTo(1, 5)
      expect(result[1]).toBeCloseTo(2, 5)
      expect(result[2]).toBeCloseTo(3, 5)
    })

    it('rotates [1, 0, 0] to approx [0, 0, -1] at 90 degrees', () => {
      const result = rotY([1, 0, 0], 90)
      expect(result[0]).toBeCloseTo(0, 5)
      expect(result[1]).toBeCloseTo(0, 5)
      expect(result[2]).toBeCloseTo(-1, 5)
    })

    it('negates x and z at 180 degrees', () => {
      const result = rotY([1, 2, 3], 180)
      expect(result[0]).toBeCloseTo(-1, 5)
      expect(result[1]).toBeCloseTo(2, 5)
      expect(result[2]).toBeCloseTo(-3, 5)
    })

    it('rotates [0, 0, 1] to [1, 0, 0] at 90 degrees', () => {
      const result = rotY([0, 0, 1], 90)
      expect(result[0]).toBeCloseTo(1, 5)
      expect(result[1]).toBeCloseTo(0, 5)
      expect(result[2]).toBeCloseTo(0, 5)
    })

    it('preserves y-coordinate', () => {
      const result = rotY([5, 10, 15], 45)
      expect(result[1]).toBeCloseTo(10, 5)
    })
  })

  describe('rotAroundAxis', () => {
    it('is identity at 0 degrees', () => {
      const v: V3 = [1, 2, 3]
      const result = rotAroundAxis(v, [0, 1, 0], 0)
      expect(result[0]).toBeCloseTo(1, 5)
      expect(result[1]).toBeCloseTo(2, 5)
      expect(result[2]).toBeCloseTo(3, 5)
    })

    it('returns identity at 360 degrees', () => {
      const v: V3 = [1, 2, 3]
      const result = rotAroundAxis(v, [1, 1, 1], 360)
      expect(result[0]).toBeCloseTo(1, 5)
      expect(result[1]).toBeCloseTo(2, 5)
      expect(result[2]).toBeCloseTo(3, 5)
    })

    it('matches rotY when rotating around Y-axis at 90 degrees', () => {
      const v: V3 = [1, 0, 0]
      const resultY = rotY(v, 90)
      const resultAxis = rotAroundAxis(v, [0, 1, 0], 90)
      expect(resultAxis[0]).toBeCloseTo(resultY[0], 5)
      expect(resultAxis[1]).toBeCloseTo(resultY[1], 5)
      expect(resultAxis[2]).toBeCloseTo(resultY[2], 5)
    })

    it('rotates around X-axis correctly', () => {
      const result = rotAroundAxis([0, 1, 0], [1, 0, 0], 90)
      expect(result[0]).toBeCloseTo(0, 5)
      expect(result[1]).toBeCloseTo(0, 5)
      expect(result[2]).toBeCloseTo(1, 5)
    })

    it('rotates around Z-axis correctly', () => {
      const result = rotAroundAxis([1, 0, 0], [0, 0, 1], 90)
      expect(result[0]).toBeCloseTo(0, 5)
      expect(result[1]).toBeCloseTo(1, 5)
      expect(result[2]).toBeCloseTo(0, 5)
    })

    it('normalizes non-unit axis before rotating', () => {
      // Rotating around a non-unit axis [2, 0, 0] should be same as [1, 0, 0]
      const v: V3 = [0, 1, 0]
      const resultUnit = rotAroundAxis(v, [1, 0, 0], 90)
      const resultNonUnit = rotAroundAxis(v, [2, 0, 0], 90)
      expect(resultNonUnit[0]).toBeCloseTo(resultUnit[0], 5)
      expect(resultNonUnit[1]).toBeCloseTo(resultUnit[1], 5)
      expect(resultNonUnit[2]).toBeCloseTo(resultUnit[2], 5)
    })
  })

  describe('mkLm', () => {
    it('creates a landmark with correct fields', () => {
      const lm = mkLm(5, [0.5, 0.6, 0.1])
      expect(lm.index).toBe(5)
      expect(lm.x).toBe(0.5)
      expect(lm.y).toBe(0.6)
      expect(lm.z).toBe(0.1)
      expect(lm.visibility).toBe(1)
      expect(lm.presence).toBe(1)
    })

    it('uses default visibility of 1', () => {
      const lm = mkLm(10, [0.2, 0.3, 0.4])
      expect(lm.visibility).toBe(1)
      expect(lm.presence).toBe(1)
    })

    it('uses custom visibility value', () => {
      const lm = mkLm(15, [0.1, 0.2, 0.3], 0.7)
      expect(lm.visibility).toBe(0.7)
      expect(lm.presence).toBe(0.7)
    })

    it('handles zero visibility', () => {
      const lm = mkLm(20, [0, 0, 0], 0)
      expect(lm.visibility).toBe(0)
      expect(lm.presence).toBe(0)
    })
  })
})
