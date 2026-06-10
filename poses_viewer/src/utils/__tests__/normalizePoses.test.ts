import { describe, it, expect } from 'vitest'
import { normalizeData } from '../normalizePoses'

const legacyFile = {
  videoName: 'video_2.mp4',
  intervalMs: 100,
  totalFrames: 1,
  videoDurationMs: 100,
  videoWidth: 712,
  videoHeight: 1280,
  exportTimestamp: 123,
  frames: [
    {
      frameIndex: 0,
      timestampMs: 0,
      landmarks: [
        { index: 0, x: 0.5, y: 0.4, z: -0.1, visibility: 0.9, presence: 0.8 },
      ],
    },
  ],
}

describe('normalizeData (legacy v1)', () => {
  it('preserves mediapipe landmark fields', () => {
    const d = normalizeData(legacyFile)
    expect(d.frames).toHaveLength(1)
    const lm = d.frames[0].landmarks[0]
    expect(lm).toEqual({ index: 0, x: 0.5, y: 0.4, z: -0.1, visibility: 0.9, presence: 0.8 })
    expect(d.videoWidth).toBe(712)
  })

  it('accepts tuple landmarks and a top-level frame array', () => {
    const d = normalizeData([{ landmarks: [[0.1, 0.2, 0.3, 0.95, 0.9]] }])
    const lm = d.frames[0].landmarks[0]
    expect(lm.x).toBeCloseTo(0.1)
    expect(lm.visibility).toBeCloseTo(0.95)
  })
})

const v2File = {
  schemaVersion: 2,
  topology: 'coco17',
  model: 'rtmpose-m',
  videoName: 'video_2.mp4',
  intervalMs: 100,
  totalFrames: 1,
  videoDurationMs: 100,
  videoWidth: 712,
  videoHeight: 1280,
  exportTimestamp: 123,
  frames: [
    {
      frameIndex: 0,
      timestampMs: 0,
      landmarks: [{ index: 5, x: 0.31, y: 0.42, score: 0.93 }],
    },
  ],
}

describe('normalizeData (schema v2 / coco17)', () => {
  it('detects coco17 topology', () => {
    expect(normalizeData(v2File).topology).toBe('coco17')
  })

  it('defaults legacy files to mediapipe33', () => {
    expect(normalizeData(legacyFile).topology).toBe('mediapipe33')
  })

  it('detects halpe26 topology', () => {
    expect(normalizeData({ ...v2File, topology: 'halpe26' }).topology).toBe('halpe26')
  })

  it('maps score to visibility/presence and z to 0', () => {
    const lm = normalizeData(v2File).frames[0].landmarks[0]
    expect(lm).toEqual({ index: 5, x: 0.31, y: 0.42, z: 0, visibility: 0.93, presence: 0.93 })
  })
})
