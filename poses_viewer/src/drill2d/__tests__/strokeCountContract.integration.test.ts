import { describe, expect, it } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import { parsePoseV2 } from '../parsePoseV2'
import { countStrokes } from '../countStrokes'
import { analyzeDrill } from '../analyzeDrill'
import { REFERENCE_STANDARDS } from '../referenceStandard'
import { ALL_KEYS } from '../drillMetrics'
import { DEFAULT_FEEDBACK_SETTINGS } from '../feedbackSettings'

const RAW_BAND_SETTINGS = { ...DEFAULT_FEEDBACK_SETTINGS, bandWidthMult: 1, minMeaningfulDeltaDeg: 5 }

/**
 * STROKE-COUNT CONTRACT (2026-06-15). Direction-aware NMS + full-cycle model.
 * These are the user-confirmed-correct counts on real footage; both UI entry points
 * (countStrokes for the timeline, analyzeDrill for the metrics table) must agree.
 *
 *   video_4 = 10  ← HARD CONTRACT (must never regress)
 *   video_3 = 20  ← steady forehand drill, even ~0.95s cadence
 *
 * If a detection change moves either number, it moved a user-validated count —
 * investigate the change, do not edit these numbers to make the suite pass.
 */
const FIX = path.resolve(__dirname, '../../../../shared/src/commonTest/resources/fixtures')
const load = (n: string) => parsePoseV2(JSON.parse(fs.readFileSync(path.join(FIX, n), 'utf-8')))
const cfg = { handedness: 'right' as const, cameraYawDeg: 0 }
const drillCfg = (extra = {}) => ({
  handedness: 'right' as const,
  drillType: 'forehand_drive',
  standard: REFERENCE_STANDARDS['forehand_drive'],
  feedbackSettings: RAW_BAND_SETTINGS,
  cameraYawDeg: 0,
  ...extra,
})

describe('stroke-count contract — video_4 = 10 (HARD)', () => {
  const seq = load('video_4_rtm.json')

  it('countStrokes → exactly 10 reps', () => {
    expect(countStrokes(seq, cfg).reps).toHaveLength(10)
  })

  it('analyzeDrill → exactly 10 reps', () => {
    expect(analyzeDrill(seq, drillCfg()).reps).toHaveLength(10)
  })

  it('the ~15.18s walking step is excluded and flagged as locomotion', () => {
    const r = countStrokes(seq, cfg)
    const at1518 = (s: { peakFrame: number }) => Math.abs(seq.frames[s.peakFrame].timestampMs / 1000 - 15.18) < 0.2
    expect(r.reps.some(at1518)).toBe(false)
    expect(r.locomotionStrokes.some(at1518)).toBe(true)
  })
})

describe('stroke-count contract — video_3 = 20 (steady drill)', () => {
  const seq = load('video_3_rtm.json')

  it('countStrokes → exactly 20 reps', () => {
    expect(countStrokes(seq, cfg).reps).toHaveLength(20)
  })

  it('analyzeDrill → exactly 20 reps', () => {
    expect(analyzeDrill(seq, drillCfg()).reps).toHaveLength(20)
  })

  it('the 20 reps are evenly spaced (~0.95s cadence) — proof they are real strokes', () => {
    const r = countStrokes(seq, cfg)
    const peaks = r.reps.map(s => seq.frames[s.peakFrame].timestampMs / 1000).sort((a, b) => a - b)
    const gaps = peaks.slice(1).map((p, i) => p - peaks[i])
    // Direction-aware NMS recovers the warm-up + mid-clip drives the old 500ms-gap NMS
    // suppressed; the result is a regular cadence with no 2×/3× holes.
    for (const g of gaps) {
      expect(g).toBeGreaterThan(0.7)
      expect(g).toBeLessThan(1.3)
    }
  })
})

describe('stroke-count contract — andrii_1 = 15 (topspin, different camera)', () => {
  // andrii_1 is a TOPSPIN clip from a different camera position — the detector is
  // stroke-type- and camera-agnostic (it keys on wrist-speed direction reversals), so
  // it must still count all 15. Two of those drives lack a detectable backswing (their
  // cycle is the ~0.5s drive-half only); the relaxed RepFilter keeps them because they
  // are short-but-STRONG, while still dropping video_4's short-AND-slow trailing junk (L-31).
  it('countStrokes → exactly 15 reps', () => {
    expect(countStrokes(load('andrii_1_rtm.json'), cfg).reps).toHaveLength(15)
  })

  it('analyzeDrill → exactly 15 reps', () => {
    expect(analyzeDrill(load('andrii_1_rtm.json'), drillCfg()).reps).toHaveLength(15)
  })
})
