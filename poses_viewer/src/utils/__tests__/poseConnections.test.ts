import { describe, it, expect } from 'vitest'
import { POSE_CONNECTIONS, COCO17_CONNECTIONS, getConnections } from '../poseConnections'

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

describe('getConnections', () => {
  it('selects edge list by topology', () => {
    expect(getConnections('mediapipe33')).toBe(POSE_CONNECTIONS)
    expect(getConnections('coco17')).toBe(COCO17_CONNECTIONS)
  })
})
