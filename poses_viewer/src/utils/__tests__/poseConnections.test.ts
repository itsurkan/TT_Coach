import { describe, it, expect } from 'vitest'
import { POSE_CONNECTIONS, COCO17_CONNECTIONS, HALPE26_CONNECTIONS, getConnections } from '../poseConnections'

describe('COCO17_CONNECTIONS', () => {
  it('has 16 edges, all indices within 0..16', () => {
    expect(COCO17_CONNECTIONS).toHaveLength(16)
    for (const [a, b] of COCO17_CONNECTIONS) {
      expect(a).toBeGreaterThanOrEqual(0)
      expect(a).toBeLessThan(17)
      expect(b).toBeGreaterThanOrEqual(0)
      expect(b).toBeLessThan(17)
    }
  })

  it('contains the anatomical limb chains', () => {
    expect(COCO17_CONNECTIONS).toContainEqual([5, 7, 'left'])    // l-shoulder → l-elbow
    expect(COCO17_CONNECTIONS).toContainEqual([7, 9, 'left'])    // l-elbow → l-wrist
    expect(COCO17_CONNECTIONS).toContainEqual([6, 8, 'right'])   // r-shoulder → r-elbow
    expect(COCO17_CONNECTIONS).toContainEqual([8, 10, 'right'])  // r-elbow → r-wrist
    expect(COCO17_CONNECTIONS).toContainEqual([11, 13, 'left'])  // l-hip → l-knee
    expect(COCO17_CONNECTIONS).toContainEqual([13, 15, 'left'])  // l-knee → l-ankle
    expect(COCO17_CONNECTIONS).toContainEqual([12, 14, 'right']) // r-hip → r-knee
    expect(COCO17_CONNECTIONS).toContainEqual([14, 16, 'right']) // r-knee → r-ankle
    expect(COCO17_CONNECTIONS).toContainEqual([5, 6, 'center'])  // shoulder line
    expect(COCO17_CONNECTIONS).toContainEqual([11, 12, 'center']) // hip line
  })
})

describe('HALPE26_CONNECTIONS', () => {
  it('extends COCO-17 with 6 foot edges and skips head/neck/hip-mid (17–19)', () => {
    expect(HALPE26_CONNECTIONS).toHaveLength(22)
    for (const conn of COCO17_CONNECTIONS) {
      expect(HALPE26_CONNECTIONS).toContainEqual(conn)
    }
    const used = new Set(HALPE26_CONNECTIONS.flatMap(([a, b]) => [a, b]))
    expect(used.has(17)).toBe(false) // head — intentionally not drawn
    expect(used.has(18)).toBe(false) // neck
    expect(used.has(19)).toBe(false) // hip-mid
    expect(HALPE26_CONNECTIONS).toContainEqual([15, 24, 'left'])   // l-ankle → l-heel
    expect(HALPE26_CONNECTIONS).toContainEqual([24, 20, 'left'])   // l-heel → l-big-toe
    expect(HALPE26_CONNECTIONS).toContainEqual([20, 22, 'left'])   // l-big-toe → l-small-toe
    expect(HALPE26_CONNECTIONS).toContainEqual([16, 25, 'right'])  // r-ankle → r-heel
    expect(HALPE26_CONNECTIONS).toContainEqual([25, 21, 'right'])  // r-heel → r-big-toe
    expect(HALPE26_CONNECTIONS).toContainEqual([21, 23, 'right'])  // r-big-toe → r-small-toe
  })
})

describe('getConnections', () => {
  it('selects edge list by topology', () => {
    expect(getConnections('mediapipe33')).toBe(POSE_CONNECTIONS)
    expect(getConnections('coco17')).toBe(COCO17_CONNECTIONS)
    expect(getConnections('halpe26')).toBe(HALPE26_CONNECTIONS)
  })
})
