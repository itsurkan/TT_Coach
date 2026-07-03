import { describe, expect, it } from 'vitest'
import { hipMidTravelTorso, filterStationaryStrokes } from '../locomotionFilter'
import { Coco17, Keypoint2D, PoseFrame2D, Stroke2D } from '../types'

/**
 * Frames where the whole torso (shoulders + hips) sits at horizontal position
 * baseXs[i]; torso stays vertical (shoulders 0.25 above hips) so torso-length is
 * a constant 0.25. A planted stroke holds baseX; a walking stroke slides it.
 */
function frames(baseXs: number[]): PoseFrame2D[] {
  return baseXs.map((bx, i) => {
    const kp: Keypoint2D[] = Array.from({ length: 17 }, () => ({ x: bx, y: 0.5, score: 1 }))
    kp[Coco17.LEFT_SHOULDER] = { x: bx - 0.01, y: 0.30, score: 1 }
    kp[Coco17.RIGHT_SHOULDER] = { x: bx + 0.01, y: 0.30, score: 1 }
    kp[Coco17.LEFT_HIP] = { x: bx - 0.01, y: 0.55, score: 1 }
    kp[Coco17.RIGHT_HIP] = { x: bx + 0.01, y: 0.55, score: 1 }
    return { frameIndex: i, timestampMs: i * 100, keypoints: kp }
  })
}

const stroke = (start: number, peak: number, end: number): Stroke2D =>
  ({ strokeIndex: 0, startFrame: start, peakFrame: peak, endFrame: end, peakSpeed: 8 })

describe('hipMidTravelTorso', () => {
  it('is ~0 for a planted stroke (hip-mid held still)', () => {
    const f = frames([0.5, 0.5, 0.5, 0.5, 0.5])
    const t = hipMidTravelTorso(f, stroke(0, 2, 4), 1, 0.3)
    expect(t).not.toBeNull()
    expect(t!).toBeCloseTo(0, 5)
  })

  it('measures peak-to-peak excursion in torso-lengths for a sliding hip-mid', () => {
    // hip-mid slides 0.4 → 0.6 (Δ0.2); torso-length 0.25 → travel 0.8 torso-lengths
    const f = frames([0.4, 0.45, 0.5, 0.55, 0.6])
    const t = hipMidTravelTorso(f, stroke(0, 2, 4), 1, 0.3)
    expect(t!).toBeCloseTo(0.8, 5)
  })

  it('returns null when hip keypoints are gated below minScore', () => {
    const f = frames([0.5, 0.5, 0.5])
    f.forEach(fr => { fr.keypoints[Coco17.LEFT_HIP].score = 0.1; fr.keypoints[Coco17.RIGHT_HIP].score = 0.1 })
    expect(hipMidTravelTorso(f, stroke(0, 1, 2), 1, 0.3)).toBeNull()
  })
})

describe('filterStationaryStrokes', () => {
  it('drops a walking stroke (travel over threshold), keeps a planted one', () => {
    // strokeA frames 0-4 planted; strokeB frames 5-9 walking (Δ0.2 → 0.8 torso)
    const f = frames([0.5, 0.5, 0.5, 0.5, 0.5, 0.4, 0.45, 0.5, 0.55, 0.6])
    const planted: Stroke2D = { strokeIndex: 0, startFrame: 0, peakFrame: 2, endFrame: 4, peakSpeed: 8 }
    const walking: Stroke2D = { strokeIndex: 1, startFrame: 5, peakFrame: 7, endFrame: 9, peakSpeed: 8 }
    expect(filterStationaryStrokes([planted, walking], f, 1, 0.5, 0.3)).toEqual([planted])
  })

  it('keeps everything when threshold is not exceeded', () => {
    const f = frames([0.5, 0.5, 0.5, 0.5, 0.5])
    const s = stroke(0, 2, 4)
    expect(filterStationaryStrokes([s], f, 1, 0.5, 0.3)).toEqual([s])
  })

  it('keeps an unmeasurable stroke (cannot prove locomotion)', () => {
    const f = frames([0.5, 0.5, 0.5])
    f.forEach(fr => { fr.keypoints[Coco17.LEFT_HIP].score = 0.1; fr.keypoints[Coco17.RIGHT_HIP].score = 0.1 })
    const s = stroke(0, 1, 2)
    expect(filterStationaryStrokes([s], f, 1, 0.5, 0.3)).toEqual([s])
  })
})
