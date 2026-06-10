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
})

describe('getConnections', () => {
  it('selects edge list by topology', () => {
    expect(getConnections('mediapipe33')).toBe(POSE_CONNECTIONS)
    expect(getConnections('coco17')).toBe(COCO17_CONNECTIONS)
  })
})
