import { describe, expect, it } from 'vitest'
import { estimateSideViewYawDeg, estimateYawForStroke } from '../cameraYaw'
import { Coco17, Keypoint2D, PoseFrame2D, Stroke2D } from '../types'

const K = (x: number, y: number, score = 1): Keypoint2D => ({ x, y, score })

function frame(idx: number, shoulderSep: number): PoseFrame2D {
  const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: 0, y: 0, score: 0 }))
  // torso length 1.0 (shoulders at y=0, hips at y=1), shoulders centered on x=0.5
  kp[Coco17.LEFT_SHOULDER] = K(0.5 - shoulderSep / 2, 0)
  kp[Coco17.RIGHT_SHOULDER] = K(0.5 + shoulderSep / 2, 0)
  kp[Coco17.LEFT_HIP] = K(0.5, 1)
  kp[Coco17.RIGHT_HIP] = K(0.5, 1)
  return { frameIndex: idx, timestampMs: idx * 33, keypoints: kp }
}

describe('estimateSideViewYawDeg', () => {
  it('is ~0° in a true profile (shoulders overlap)', () => {
    const frames = [frame(0, 0), frame(1, 0)]
    expect(estimateSideViewYawDeg(frames, 1)).toBeCloseTo(0, 1)
  })
  it('grows as shoulders separate', () => {
    // sep = 0.9 * torso * sin(yaw); with ratio 0.9 and torso 1, sep 0.45 → sin=0.5 → 30°
    const frames = [frame(0, 0.45), frame(1, 0.45)]
    expect(estimateSideViewYawDeg(frames, 1)).toBeCloseTo(30, 0)
  })
  it('returns null when no frame has scored shoulders+hips', () => {
    const empty: PoseFrame2D = { frameIndex: 0, timestampMs: 0, keypoints: [] }
    expect(estimateSideViewYawDeg([empty], 1)).toBeNull()
  })
})

describe('estimateYawForStroke', () => {
  it('uses the pre-stroke lookback window', () => {
    const frames = [frame(0, 0), frame(1, 0), frame(2, 0.45), frame(3, 0.45)]
    const stroke: Stroke2D = { strokeIndex: 0, startFrame: 2, peakFrame: 3, endFrame: 3, peakSpeed: 1 }
    expect(estimateYawForStroke(frames, stroke, 1, 33, undefined, 66)).toBeCloseTo(0, 1)
  })
})
