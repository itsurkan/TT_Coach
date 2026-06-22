import { describe, expect, it } from 'vitest'
import { buildSpokenSchedule, estimateDurationMs, nextStrokeStartAfter, BASE_WPM, type VoiceRep } from '../buildSpokenSchedule'
import { PRESETS, type VoiceStyle, type MetricKey } from '../voiceStyle'
import { clipKey, type ClipManifest } from '../voiceClips'

const STRICT = PRESETS.find(p => p.id === 'preset-strict')!

/** A style with everything relaxed so timing/gates don't interfere unless a test sets them. */
function style(overrides: Partial<VoiceStyle> = {}): VoiceStyle {
  return {
    ...STRICT,
    correctiveMinGapMs: 0, praiseMinSilenceMs: 0, postStrokeGapMs: 0,
    bandWidthMult: 1.0, minMeaningfulDeltaDeg: 5, reminderIntervalMs: 0, varyCues: false,
    praiseEnabled: false, praiseOnCorrection: false, praiseOnStreak: false,
    skipStaleEnabled: false, skipStaleMarginMs: 0,
    ...overrides,
  }
}

/** Build a rep at t with given observations; spaced 2000ms apart by index helper below. */
function rep(startMs: number, obs: VoiceRep['observations'], coachable = true): VoiceRep {
  return { strokeStartMs: startMs, contactMs: startMs + 200, strokeEndMs: startMs + 400, coachable, observations: obs }
}
const band = (value: number) => ({ value, lo: 100, hi: 140 }) // half=20, center=120

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

describe('buildSpokenSchedule — bandwidth gate', () => {
  it('stays silent when every metric is in band', () => {
    const reps = [rep(0, { elbow_angle: band(120) })]
    expect(buildSpokenSchedule(reps, [0], style())).toEqual([])
  })
  it('emits a cue when a metric exits the band by >= minMeaningfulDeltaDeg', () => {
    const reps = [rep(0, { elbow_angle: band(160) })] // 160 > 140, dev=20 >= 5
    const out = buildSpokenSchedule(reps, [0], style())
    expect(out).toHaveLength(1)
    expect(out[0].metricKey).toBe('elbow_angle')
    expect(out[0].kind).toBe('cue')
    expect(out[0].text).toBe(STRICT.phrases.en.cues.elbow_angle.up)
  })
  it('respects minMeaningfulDeltaDeg as a floor', () => {
    const reps = [rep(0, { elbow_angle: band(143) })] // dev=3 < 5 → silent
    expect(buildSpokenSchedule(reps, [0], style())).toEqual([])
  })
  it('bandWidthMult widens the band (quieter)', () => {
    const reps = [rep(0, { elbow_angle: band(150) })] // dev vs raw hi=10; widened hi=120+20*1.4=148 → dev=2 <5
    expect(buildSpokenSchedule(reps, [0], style({ bandWidthMult: 1.4 }))).toEqual([])
  })
  it('selects the down phrase when below band', () => {
    const reps = [rep(0, { elbow_angle: band(80) })] // 80 < 100, dev=-20
    expect(buildSpokenSchedule(reps, [0], style())[0].text).toBe(STRICT.phrases.en.cues.elbow_angle.down)
  })
})

describe('buildSpokenSchedule — one cue per rep + selection', () => {
  it('emits at most one cue per rep, the highest severity', () => {
    // elbow dev = 40 (sev 2), knee dev = 10 (sev 0.5) → elbow wins
    const reps = [rep(0, { elbow_angle: { value: 180, lo: 100, hi: 140 }, knee_bend: { value: 150, lo: 100, hi: 140 } })]
    const out = buildSpokenSchedule(reps, [0], style())
    expect(out).toHaveLength(1)
    expect(out[0].metricKey).toBe('elbow_angle')
  })
  it('varyCues prefers a different metric than the previous cue', () => {
    const reps = [
      rep(0, { elbow_angle: band(160) }),                                  // elbow
      rep(2000, { elbow_angle: band(160), knee_bend: band(155) }),         // elbow sev=1, knee sev=0.75 → vary picks knee
    ]
    const out = buildSpokenSchedule(reps, [0, 2000], style({ varyCues: true }))
    expect(out.map(o => o.metricKey)).toEqual(['elbow_angle', 'knee_bend'])
  })
  it('reminderIntervalMs suppresses re-flagging the same metric too soon', () => {
    const reps = [
      rep(0, { elbow_angle: band(160) }),
      rep(2000, { elbow_angle: band(160) }), // only fault, within reminder window → suppressed
    ]
    const out = buildSpokenSchedule(reps, [0, 2000], style({ reminderIntervalMs: 5000 }))
    expect(out).toHaveLength(1)
    expect(out[0].atMs).toBe(400)
  })
})

describe('buildSpokenSchedule — cadence', () => {
  it('drops a correction that arrives before correctiveMinGapMs', () => {
    const reps = [
      rep(0, { elbow_angle: band(160) }),
      rep(1000, { knee_bend: band(160) }), // atMs 1400 - 400 = 1000 < 3000 → dropped
    ]
    const out = buildSpokenSchedule(reps, [0, 1000], style({ correctiveMinGapMs: 3000 }))
    expect(out).toHaveLength(1)
    expect(out[0].metricKey).toBe('elbow_angle')
  })
})

describe('buildSpokenSchedule — praise', () => {
  it('fires when a previously-flagged metric returns to band', () => {
    const reps = [
      rep(0, { elbow_angle: band(160) }),  // flagged
      rep(5000, { elbow_angle: band(120) }), // corrected → praise
    ]
    const out = buildSpokenSchedule(reps, [0, 5000], style({ praiseEnabled: true, praiseOnCorrection: true, praiseMinSilenceMs: 1000 }))
    expect(out.map(o => o.kind)).toEqual(['cue', 'praise'])
    expect(out[1].metricKey).toBeNull()
    expect(STRICT.phrases.en.praise).toContain(out[1].text)
  })
  it('fires streak praise after praiseStreakLen clean reps', () => {
    const clean = (t: number) => rep(t, { elbow_angle: band(120) })
    const reps = [clean(0), clean(3000), clean(6000)]
    const out = buildSpokenSchedule(reps, [0, 3000, 6000], style({
      praiseEnabled: true, praiseOnStreak: true, praiseStreakLen: 3, praiseMinSilenceMs: 1000,
    }))
    expect(out).toHaveLength(1)
    expect(out[0].kind).toBe('praise')
    expect(out[0].atMs).toBe(6400)
  })
  it('a correction outranks praise in the same rep', () => {
    const reps = [
      rep(0, { elbow_angle: band(160) }),                       // flag elbow
      rep(5000, { elbow_angle: band(120), knee_bend: band(160) }), // elbow corrected (praise candidate) BUT knee now bad
    ]
    const out = buildSpokenSchedule(reps, [0, 5000], style({ praiseEnabled: true, praiseOnCorrection: true, praiseMinSilenceMs: 1000 }))
    expect(out[1].kind).toBe('cue')
    expect(out[1].metricKey).toBe('knee_bend')
  })
})

describe('buildSpokenSchedule — timing anchor', () => {
  it('places every item at strokeEndMs + postStrokeGapMs', () => {
    const reps = [rep(1000, { elbow_angle: band(160) })] // strokeEnd = 1400
    const out = buildSpokenSchedule(reps, [1000], style({ postStrokeGapMs: 300 }))
    expect(out[0].atMs).toBe(1700)
  })
})

describe('buildSpokenSchedule — skip-stale', () => {
  it('drops a cue that cannot finish before the next stroke starts', () => {
    // atMs = 400; next stroke at 600; margin 150 → must finish by 450. A multi-word cue won't.
    const reps = [rep(0, { elbow_angle: band(160) }), rep(600, {})]
    const out = buildSpokenSchedule(reps, [0, 600], style({ skipStaleEnabled: true, skipStaleMarginMs: 150 }))
    expect(out).toHaveLength(0)
  })
  it('keeps the last rep cue (no next stroke)', () => {
    const reps = [rep(0, { elbow_angle: band(160) })]
    const out = buildSpokenSchedule(reps, [0], style({ skipStaleEnabled: true, skipStaleMarginMs: 150 }))
    expect(out).toHaveLength(1)
  })
  it('uses the manifest clip duration when a fresh clip exists', () => {
    const text = STRICT.phrases.en.cues.elbow_angle.up
    const manifest: ClipManifest = {
      styleId: 'preset-strict',
      clips: { [clipKey('en', text)]: { file: 'e.mp3', durationMs: 100, text, lang: 'en' } },
    }
    // atMs=400, next stroke=600, margin=50 → must finish by 550. The 100ms clip fits;
    // the word-count estimate (~1200ms) would not → proves clip duration is used.
    const reps = [rep(0, { elbow_angle: band(160) }), rep(600, {})]
    const out = buildSpokenSchedule(reps, [0, 600], style({ skipStaleEnabled: true, skipStaleMarginMs: 50 }), manifest)
    expect(out).toHaveLength(1)
    expect(out[0].clipKey).toBe(clipKey('en', text))
    expect(out[0].estDurationMs).toBe(100)
  })
})

describe('buildSpokenSchedule — coachable + determinism', () => {
  it('skips non-coachable reps entirely', () => {
    const reps = [rep(0, {}, false)]
    expect(buildSpokenSchedule(reps, [0], style())).toEqual([])
  })
  it('is deterministic for identical inputs', () => {
    const mk = () => [rep(0, { elbow_angle: band(160) }), rep(5000, { knee_bend: band(160) })]
    const s = style({ correctiveMinGapMs: 3000 })
    expect(buildSpokenSchedule(mk(), [0, 5000], s)).toEqual(buildSpokenSchedule(mk(), [0, 5000], s))
  })
  it('uses the uk phrase set when style.lang = uk', () => {
    const reps = [rep(0, { elbow_angle: band(160) })]
    const out = buildSpokenSchedule(reps, [0], style({ lang: 'uk' }))
    expect(out[0].text).toBe(STRICT.phrases.uk.cues.elbow_angle.up)
    expect(out[0].lang).toBe('uk')
  })
})
