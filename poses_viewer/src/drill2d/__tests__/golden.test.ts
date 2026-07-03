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

describe('GOLDEN counts (TS viewer pipeline — diverged from Kotlin)', () => {
  // NOTE: the TS detector now uses DIRECTION-AWARE NMS (a backswing and its forward
  // drive both survive) + the full-cycle model. This intentionally diverges from the
  // Kotlin shared/ chain (still gap-based). These are the TS numbers; do not "restore"
  // them to the old Kotlin goldens. Direction-aware NMS raises raw-peak counts (both
  // swing halves detected) and shifts RepFilter banding.
  it('andrii_1: 29 raw peaks → 15 reps', () => {
    const seq = load('andrii_1_rtm.json')
    const r = countStrokes(seq, { handedness: 'right', cameraYawDeg: 0 })
    expect(r.rawStrokes).toHaveLength(29)
    expect(r.forwardStrokes).toHaveLength(15) // all 15 forward drives detected
    expect(r.reps).toHaveLength(15)            // short-but-strong unpaired drives now kept (L-31 fix)
    expect(r.locomotionStrokes).toHaveLength(0)
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

  it('video_4: shadow play — 25 raw → 12 drives → 12 cycles → 11 banded → 10 after locomotion gate', () => {
    const seq = load('video_4_rtm.json')
    const r = countStrokes(seq, { handedness: 'right', cameraYawDeg: 0 })
    expect(r.rawStrokes).toHaveLength(25)     // direction-aware NMS keeps both swing halves
    expect(r.forwardStrokes).toHaveLength(12) // 12 drives, visually verified
    expect(r.cycles).toHaveLength(12)         // one cycle per drive (backswing paired where present)
    // Full-cycle model: banding on near-uniform cycle duration keeps the two short
    // fast drives (7.53s, 13.80s) the forward-half filter dropped → 11 banded; the
    // L-30 gate then drops the player's walking step (~15.18s, hip ~0.68 torso) → 10.
    expect(r.reps).toHaveLength(10)
    expect(r.locomotionStrokes).toHaveLength(1)
    // eslint-disable-next-line no-console
    console.log(`video_4: raw=${r.rawStrokes.length} forward=${r.forwardStrokes.length} reps=${r.reps.length} loco=${r.locomotionStrokes.length}`)
  })

  it('video_3: steady forehand drill — 20 reps at even ~0.95s cadence', () => {
    // Direction-aware NMS recovers the strokes the old 500ms-gap NMS suppressed
    // (the backswing and forward drive were near-equal speed → gap kept only one).
    const seq = load('video_3_rtm.json')
    const r = countStrokes(seq, { handedness: 'right', cameraYawDeg: 0 })
    expect(r.reps).toHaveLength(20)
    // eslint-disable-next-line no-console
    console.log(`video_3: raw=${r.rawStrokes.length} forward=${r.forwardStrokes.length} reps=${r.reps.length}`)
  })
})
