import { describe, expect, it } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import { parsePoseV2 } from '../parsePoseV2'
import { countStrokes } from '../countStrokes'
import { analyzeDrill } from '../analyzeDrill'
import { REFERENCE_STANDARDS } from '../referenceStandard'
import { ALL_KEYS } from '../drillMetrics'

/**
 * Integration guard for the full-cycle stroke model (2026-06-15): video_4 must
 * count EXACTLY 10 strokes through BOTH viewer entry points, with the player's
 * walking step (~15.18s) excluded by the locomotion gate. andrii_1 must stay 15
 * (the full-cycle change must not regress the planted-player fixture).
 *
 * If this fails after a detection change, that change moved the video_4 count —
 * investigate before "fixing" the number here.
 */
const FIX = path.resolve(__dirname, '../../../../shared/src/commonTest/resources/fixtures')
const load = (n: string) => parsePoseV2(JSON.parse(fs.readFileSync(path.join(FIX, n), 'utf-8')))

describe('video_4 full-cycle integration — exactly 10 strokes', () => {
  const seq = load('video_4_rtm.json')

  it('countStrokes(video_4) keeps exactly 10 reps', () => {
    const r = countStrokes(seq, { handedness: 'right', cameraYawDeg: 0 })
    expect(r.reps).toHaveLength(10)
  })

  it('analyzeDrill(video_4) reports exactly 10 reps', () => {
    const report = analyzeDrill(seq, {
      handedness: 'right',
      drillType: 'forehand_drive',
      standard: REFERENCE_STANDARDS['forehand_drive'],
      enabledMetrics: new Set(ALL_KEYS),
      cameraYawDeg: 0,
    })
    expect(report.reps).toHaveLength(10)
  })

  it('the ~15.18s walking step is excluded from reps and flagged as locomotion', () => {
    const r = countStrokes(seq, { handedness: 'right', cameraYawDeg: 0 })
    const at1518 = (s: { peakFrame: number }) =>
      Math.abs(seq.frames[s.peakFrame].timestampMs / 1000 - 15.18) < 0.2
    expect(r.reps.some(at1518)).toBe(false)
    expect(r.locomotionStrokes.some(at1518)).toBe(true)
  })

  it('andrii_1 stays at 15 reps (full-cycle change is regression-safe)', () => {
    expect(countStrokes(load('andrii_1_rtm.json'), { handedness: 'right', cameraYawDeg: 0 }).reps).toHaveLength(15)
  })
})
