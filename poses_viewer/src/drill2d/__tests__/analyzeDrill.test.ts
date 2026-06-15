import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { analyzeDrill } from '../analyzeDrill'
import { parsePoseV2 } from '../parsePoseV2'
import { FOREHAND_DRIVE_STANDARD } from '../referenceStandard'

// Mirror golden.test.ts's fixture path pattern (repo-relative).
function loadSeq(name: string) {
  const p = resolve(__dirname, '../../../../shared/src/commonTest/resources/fixtures', name)
  return parsePoseV2(JSON.parse(readFileSync(p, 'utf-8')))
}

describe('analyzeDrill — count parity (anti-drift guardrail)', () => {
  it('andrii_1 produces 15 reps from 29 raw peaks (direction-aware NMS, TS-diverged)', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right',
      drillType: 'forehand_drive',
      standard: FOREHAND_DRIVE_STANDARD,
      cameraYawDeg: 0, // manual override → placementOk, feedback flows
    })
    expect(report.rawPeakCount).toBe(29)
    expect(report.reps).toHaveLength(15)
  })

  it('produces a metric map per rep and (with yaw override) at least one spoken line', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: 0,
    })
    expect(report.reps.every(r => typeof r.metrics === 'object')).toBe(true)
    expect(report.reps.every(r => r.placementOk)).toBe(true) // yaw override 0
    expect(report.feedback.length).toBeGreaterThan(0)
    // cadence: consecutive spoken lines are ≥ 3 s apart
    for (let i = 1; i < report.feedback.length; i++) {
      expect(report.feedback[i].timestampMs - report.feedback[i - 1].timestampMs).toBeGreaterThanOrEqual(3000)
    }
  })

  it('auto yaw estimation flags Videos/ footage as bad placement (L-25 saturation)', () => {
    const seq = loadSeq('andrii_1_rtm.json')
    const report = analyzeDrill(seq, {
      handedness: 'right', drillType: 'forehand_drive', standard: FOREHAND_DRIVE_STANDARD, cameraYawDeg: null,
    })
    expect(report.placementOk).toBe(false)
  })
})
