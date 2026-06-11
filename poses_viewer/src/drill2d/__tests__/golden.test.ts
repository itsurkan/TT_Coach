import fs from 'fs'
import path from 'path'
import { describe, expect, it } from 'vitest'
import { parsePoseV2 } from '../parsePoseV2'
import { countStrokes } from '../countStrokes'

// __dirname = poses_viewer/src/drill2d/__tests__ → repo root is 4 levels up
const FIXTURES = path.resolve(__dirname, '../../../../shared/src/commonTest/resources/fixtures')

const load = (name: string) =>
  parsePoseV2(JSON.parse(fs.readFileSync(path.join(FIXTURES, name), 'utf-8')))

describe('parsePoseV2', () => {
  it('parses andrii_1_rtm metadata', () => {
    const seq = load('andrii_1_rtm.json')
    expect(seq.topology).toBe('coco17')
    expect(seq.intervalMs).toBe(17)
    expect(seq.aspectRatio).toBeCloseTo(720 / 1280, 6)
    expect(seq.frames.length).toBeGreaterThan(1000)
    expect(seq.frames[0].keypoints).toHaveLength(17)
    expect(seq.frames[0].keypoints[0].score).toBeGreaterThan(0)
  })

  it('rejects legacy schema-v1 (MediaPipe-33) files', () => {
    expect(() => parsePoseV2({ frames: [] })).toThrow(/schemaVersion/)
    expect(() => parsePoseV2({ schemaVersion: 1, frames: [] })).toThrow(/schemaVersion/)
  })
})

describe('GOLDEN parity vs Kotlin E2E (ForehandDriveEndToEndTest)', () => {
  // Kotlin source of truth: 23 raw peaks, 15 reps, handedness RIGHT, yaw 0.
  // If this fails, the TS port is the bug — diff per-stroke output against a
  // Kotlin diagnostic; NEVER loosen these numbers to make TS pass.
  it('andrii_1: 23 raw peaks → 15 reps', () => {
    const seq = load('andrii_1_rtm.json')
    const r = countStrokes(seq, { handedness: 'right', cameraYawDeg: 0 })
    expect(r.rawStrokes).toHaveLength(23)
    expect(r.forwardStrokes).toHaveLength(15) // observed; matches Kotlin (RepFilter drops none on this fixture)
    expect(r.reps).toHaveLength(15)
    // eslint-disable-next-line no-console
    console.log(`andrii_1: raw=${r.rawStrokes.length} forward=${r.forwardStrokes.length} reps=${r.reps.length}`)
  })

  it('video_2: pipeline invariants hold', () => {
    const seq = load('video_2_rtm.json')
    expect(seq.intervalMs).toBe(20)
    const r = countStrokes(seq, { handedness: 'right', cameraYawDeg: 0 })
    expect(r.rawStrokes.length).toBeGreaterThan(0)
    expect(r.forwardStrokes.length).toBeLessThanOrEqual(r.rawStrokes.length)
    expect(r.reps.length).toBeLessThanOrEqual(r.forwardStrokes.length)
    // eslint-disable-next-line no-console
    console.log(`video_2: raw=${r.rawStrokes.length} forward=${r.forwardStrokes.length} reps=${r.reps.length}`)
  })
})
