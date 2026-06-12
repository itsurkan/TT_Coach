import { describe, expect, it } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import { parsePoseV2 } from '../parsePoseV2'
import { countStrokes } from '../countStrokes'
import { analyzeDrill } from '../analyzeDrill'
import { REFERENCE_STANDARDS } from '../referenceStandard'
import { ALL_KEYS } from '../drillMetrics'

const FIX = path.resolve(__dirname, '../../../../shared/src/commonTest/resources/fixtures')
const load = (n: string) => parsePoseV2(JSON.parse(fs.readFileSync(path.join(FIX, n), 'utf-8')))
const cfg = { handedness: 'right' as const, cameraYawDeg: 0 }

describe('locomotion gate — countStrokes', () => {
  it('off by default: count goldens unchanged', () => {
    expect(countStrokes(load('video_4_rtm.json'), cfg).reps.length).toBe(9)
    expect(countStrokes(load('andrii_1_rtm.json'), cfg).reps.length).toBe(15)
  })

  it('at 0.4 torso: drops the walking rep in video_4, keeps all of andrii_1', () => {
    const seq = load('video_4_rtm.json')
    const r = countStrokes(seq, { ...cfg, hipTravelMaxTorso: 0.4 })
    expect(r.reps.length).toBe(8)
    expect(r.locomotionStrokes.length).toBe(1)
    // the dropped stroke is the one peaking ~15.18s
    const peakMs = seq.frames[r.locomotionStrokes[0].peakFrame].timestampMs
    expect(peakMs / 1000).toBeCloseTo(15.18, 1)
    expect(countStrokes(load('andrii_1_rtm.json'), { ...cfg, hipTravelMaxTorso: 0.4 }).reps.length).toBe(15)
  })
})

describe('locomotion gate — analyzeDrill', () => {
  const base = {
    handedness: 'right' as const,
    drillType: 'forehand_drive',
    standard: REFERENCE_STANDARDS['forehand_drive'],
    enabledMetrics: new Set(ALL_KEYS),
    cameraYawDeg: 0,
  }
  it('off by default: 9 reps in video_4', () => {
    expect(analyzeDrill(load('video_4_rtm.json'), base).reps.length).toBe(9)
  })
  it('at 0.4 torso: 8 reps, the 15.18s walking rep removed', () => {
    const seq = load('video_4_rtm.json')
    const rep = analyzeDrill(seq, { ...base, hipTravelMaxTorso: 0.4 })
    expect(rep.reps.length).toBe(8)
    const has15 = rep.reps.some(r => Math.abs(seq.frames[r.stroke.peakFrame].timestampMs / 1000 - 15.18) < 0.2)
    expect(has15).toBe(false)
  })
})
