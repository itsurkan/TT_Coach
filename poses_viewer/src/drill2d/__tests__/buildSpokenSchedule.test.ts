import { describe, expect, it } from 'vitest'
import { buildSpokenSchedule, estimateDurationMs, nextStrokeStartAfter, BASE_WPM, type RepInput } from '../buildSpokenSchedule'
import { PRESETS } from '../voiceStyle'
import { DEFAULT_FEEDBACK_SETTINGS } from '../feedbackSettings'
import type { FeedbackCue } from '../feedbackCue'

const STRICT = PRESETS.find(p => p.id === 'preset-strict')!

function settings(o: Partial<typeof DEFAULT_FEEDBACK_SETTINGS> = {}) {
  return {
    ...DEFAULT_FEEDBACK_SETTINGS,
    correctiveMinGapMs: 0, praiseMinSilenceMs: 0, postStrokeGapMs: 0,
    reminderIntervalMs: 0, varyCues: false,
    praiseEnabled: false, praiseOnCorrection: false, praiseOnStreak: false,
    skipStaleEnabled: false, skipStaleMarginMs: 0,
    ...o,
  }
}
const cue = (metricKey: string, direction: FeedbackCue['direction'], severity = 1, phase?: FeedbackCue['phase']): FeedbackCue =>
  ({ metricKey, direction, deltaFromRange: direction === 'too_high' ? 10 : -10, severity, precision: 'precise_degrees', ...(phase ? { phase } : {}) })
const rep = (startMs: number, cues: FeedbackCue[], coachable = true): RepInput =>
  ({ cues, timing: { strokeStartMs: startMs, contactMs: startMs + 200, strokeEndMs: startMs + 400 }, coachable })

describe('estimateDurationMs', () => {
  it('uses the WPM budget and scales with rate', () => {
    // 2 words at 150 wpm (2.5 w/s) → 800ms
    expect(estimateDurationMs('bend elbow', 1)).toBeCloseTo((2 / (BASE_WPM / 60)) * 1000, 5)
    expect(estimateDurationMs('bend elbow', 2)).toBeCloseTo(estimateDurationMs('bend elbow', 1) / 2, 5)
  })
})

describe('nextStrokeStartAfter', () => {
  it('returns the first start strictly greater than atMs, else +Infinity', () => {
    expect(nextStrokeStartAfter([0, 1000, 2000], 1000)).toBe(2000)
    expect(nextStrokeStartAfter([0, 1000], 1500)).toBe(Number.POSITIVE_INFINITY)
  })
})

describe('buildSpokenSchedule (unified cues)', () => {
  it('says nothing when a rep has no cues', () => {
    const { schedule, voicedByRep } = buildSpokenSchedule([rep(0, [])], [0], settings(), STRICT.phrases.en, 'en', STRICT.rate)
    expect(schedule).toEqual([])
    expect(voicedByRep).toEqual([null])
  })
  it('voices the top cue and records it in voicedByRep', () => {
    const { schedule, voicedByRep } = buildSpokenSchedule(
      [rep(0, [cue('elbow_angle', 'too_low')])], [0], settings(), STRICT.phrases.en, 'en', STRICT.rate)
    expect(schedule).toHaveLength(1)
    expect(schedule[0].metricKey).toBe('elbow_angle')
    expect(schedule[0].text).toBe(STRICT.phrases.en.cues.elbow_angle.down) // too_low → 'down' phrase
    expect(voicedByRep[0]?.metricKey).toBe('elbow_angle')
  })
  it('suppresses a cue inside correctiveMinGapMs and marks the rep null', () => {
    const reps = [rep(0, [cue('elbow_angle', 'too_low')]), rep(500, [cue('knee_bend', 'too_high')])]
    const { schedule, voicedByRep } = buildSpokenSchedule(
      reps, [0, 500], settings({ correctiveMinGapMs: 2000 }), STRICT.phrases.en, 'en', STRICT.rate)
    expect(schedule).toHaveLength(1)         // only the first fires
    expect(voicedByRep[0]?.metricKey).toBe('elbow_angle')
    expect(voicedByRep[1]).toBeNull()
  })
  it('never voices a cue the rep did not contain', () => {
    const { voicedByRep } = buildSpokenSchedule(
      [rep(0, [cue('knee_bend', 'too_high')])], [0], settings(), STRICT.phrases.en, 'en', STRICT.rate)
    expect(voicedByRep[0]?.metricKey).toBe('knee_bend')
  })
  it('leaves a non-coachable rep silent and null', () => {
    const { schedule, voicedByRep } = buildSpokenSchedule(
      [rep(0, [cue('elbow_angle', 'too_low')], false)], [0], settings(), STRICT.phrases.en, 'en', STRICT.rate)
    expect(schedule).toEqual([])
    expect(voicedByRep).toEqual([null])
  })
  it('speaks the per-phase phrase for a pattern (elbow) cue, not the single-instant phrase', () => {
    const { schedule } = buildSpokenSchedule(
      [rep(0, [cue('elbow_angle', 'too_high', 1, 'followthrough')])], [0], settings(), STRICT.phrases.en, 'en', STRICT.rate)
    expect(schedule).toHaveLength(1)
    expect(schedule[0].text).toBe(STRICT.phrases.en.phaseCues!.elbow_angle!.followthrough!.up)
    expect(schedule[0].text).not.toBe(STRICT.phrases.en.cues.elbow_angle.up)
  })
  it('a backswing cue does not suppress a followthrough cue within reminderIntervalMs', () => {
    const reps = [
      rep(0, [cue('elbow_angle', 'too_low', 1, 'backswing')]),
      rep(500, [cue('elbow_angle', 'too_high', 1, 'followthrough')]),
    ]
    const { schedule, voicedByRep } = buildSpokenSchedule(
      reps, [0, 500], settings({ reminderIntervalMs: 5000 }), STRICT.phrases.en, 'en', STRICT.rate)
    expect(schedule).toHaveLength(2)
    expect(voicedByRep[0]?.phase).toBe('backswing')
    expect(voicedByRep[1]?.phase).toBe('followthrough')
  })
})
