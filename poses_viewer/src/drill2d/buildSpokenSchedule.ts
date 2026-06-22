/**
 * The pure voice core. Walks style-independent per-rep observations in time order
 * and produces a deterministic, audio-free spoken schedule:
 *   gate → praise → select → cadence → skip-stale → format.
 * Every item lands in the inter-rep gap at strokeEndMs + postStrokeGapMs, and must
 * finish before the next stroke starts. No pose math here — re-running on a style
 * edit is cheap (enables live preview).
 */
import type { Lang, MetricKey, VoiceStyle } from './voiceStyle'
import { clipKey, lookupClip, type ClipManifest } from './voiceClips'

/** Practitioner WPM budget: ~150 wpm ≈ 2.5 words/s. */
export const BASE_WPM = 150

export interface MetricObservation {
  value: number
  lo: number
  hi: number
}

export interface VoiceRep {
  strokeStartMs: number
  contactMs: number
  strokeEndMs: number
  /** false → bad camera / no measurable coachable metrics; the core emits nothing and leaves state untouched. */
  coachable: boolean
  /** Only voiced (5 in-plane), reliable, measured metrics with their (un-widened) ideal band. */
  observations: Partial<Record<MetricKey, MetricObservation>>
}

export interface SpokenFeedbackItem {
  atMs: number
  text: string
  lang: Lang
  kind: 'cue' | 'praise'
  metricKey: MetricKey | null
  clipKey?: string
  estDurationMs: number
}

export type SpokenSchedule = SpokenFeedbackItem[]

/** Live-TTS utterance duration estimate from word count, scaled by rate. */
export function estimateDurationMs(text: string, rate: number): number {
  const words = text.trim().split(/\s+/).filter(Boolean).length
  const safeRate = rate > 0 ? rate : 1
  const wordsPerSec = (BASE_WPM * safeRate) / 60
  return wordsPerSec > 0 ? (words / wordsPerSec) * 1000 : 0
}

/** First stroke start strictly after atMs, or +Infinity if none (last rep). */
export function nextStrokeStartAfter(strokeStartTimes: number[], atMs: number): number {
  for (const t of strokeStartTimes) if (t > atMs) return t
  return Number.POSITIVE_INFINITY
}

interface Deviation {
  direction: 'up' | 'down'
  deviation: number
  severity: number
}

/** Per-metric out-of-band deviations for one rep, given the style's widened bands. */
function gateRep(
  observations: Partial<Record<MetricKey, MetricObservation>>,
  style: VoiceStyle,
): Map<MetricKey, Deviation> {
  const out = new Map<MetricKey, Deviation>()
  for (const key of Object.keys(observations) as MetricKey[]) {
    const o = observations[key]!
    const center = (o.lo + o.hi) / 2
    const half = (o.hi - o.lo) / 2
    const wLo = center - half * style.bandWidthMult
    const wHi = center + half * style.bandWidthMult
    let direction: 'up' | 'down'
    let deviation: number
    if (o.value > wHi) {
      direction = 'up'
      deviation = o.value - wHi
    } else if (o.value < wLo) {
      direction = 'down'
      deviation = o.value - wLo
    } else {
      continue
    }
    if (Math.abs(deviation) < style.minMeaningfulDeltaDeg) continue
    const severity = half > 0 ? Math.abs(deviation) / half : 0
    out.set(key, { direction, deviation, severity })
  }
  return out
}

export function buildSpokenSchedule(
  reps: VoiceRep[],
  strokeStartTimes: number[],
  style: VoiceStyle,
  clipManifest?: ClipManifest | null,
): SpokenSchedule {
  const schedule: SpokenSchedule = []
  const lang = style.lang
  const phrases = style.phrases[lang]

  let lastSpokenMs = Number.NEGATIVE_INFINITY
  let prevOutOfBand = new Set<MetricKey>()
  const lastCuedMs = new Map<MetricKey, number>()
  let lastCueMetric: MetricKey | null = null
  let cleanStreak = 0
  let praiseIndex = 0

  const durationOf = (text: string): { ms: number; key?: string } => {
    const clip = lookupClip(clipManifest ?? null, lang, text)
    if (clip) return { ms: clip.durationMs, key: clipKey(lang, text) }
    return { ms: estimateDurationMs(text, style.rate) }
  }
  const fits = (atMs: number, ms: number): boolean => {
    if (!style.skipStaleEnabled) return true
    const next = nextStrokeStartAfter(strokeStartTimes, atMs)
    return atMs + ms <= next - style.skipStaleMarginMs
  }

  for (const rep of reps) {
    if (!rep.coachable) continue
    const atMs = rep.strokeEndMs + style.postStrokeGapMs

    // 1. Bandwidth gate.
    const deviations = gateRep(rep.observations, style)
    const curOutOfBand = new Set<MetricKey>(deviations.keys())
    cleanStreak = curOutOfBand.size === 0 ? cleanStreak + 1 : 0

    // 2. Praise candidate (computed before correction; correction wins if both qualify).
    let praiseCandidate = false
    if (style.praiseEnabled) {
      const corrected = [...prevOutOfBand].some(k => !curOutOfBand.has(k))
      if (style.praiseOnCorrection && corrected) praiseCandidate = true
      else if (style.praiseOnStreak && cleanStreak > 0 && cleanStreak >= style.praiseStreakLen) praiseCandidate = true
    }

    // 3. Cue selection (one per rep): max severity, vary-aware, reminder-suppressed.
    let chosen: MetricKey | null = null
    if (curOutOfBand.size > 0) {
      const ranked = [...curOutOfBand].sort(
        (a, b) => deviations.get(b)!.severity - deviations.get(a)!.severity,
      )
      const eligible = ranked.filter(k => {
        const last = lastCuedMs.get(k)
        return last === undefined || atMs - last >= style.reminderIntervalMs
      })
      if (eligible.length > 0) {
        chosen = eligible[0]
        if (style.varyCues && chosen === lastCueMetric && eligible.length > 1) chosen = eligible[1]
      }
    }

    // 4–6. Emit. Correction outranks praise; if the correction is dropped (cadence/stale) the rep stays silent.
    if (chosen !== null) {
      if (atMs - lastSpokenMs >= style.correctiveMinGapMs) {
        const dir = deviations.get(chosen)!.direction
        const text = phrases.cues[chosen][dir]
        const { ms, key } = durationOf(text)
        if (fits(atMs, ms)) {
          schedule.push({ atMs, text, lang, kind: 'cue', metricKey: chosen, clipKey: key, estDurationMs: ms })
          lastSpokenMs = atMs
          lastCueMetric = chosen
          lastCuedMs.set(chosen, atMs)
        }
      }
    } else if (praiseCandidate) {
      if (atMs - lastSpokenMs >= style.praiseMinSilenceMs && phrases.praise.length > 0) {
        const text = phrases.praise[praiseIndex % phrases.praise.length]
        const { ms, key } = durationOf(text)
        if (fits(atMs, ms)) {
          schedule.push({ atMs, text, lang, kind: 'praise', metricKey: null, clipKey: key, estDurationMs: ms })
          lastSpokenMs = atMs
          praiseIndex++
        }
      }
    }

    // Always advance the out-of-band memory, regardless of what was emitted.
    prevOutOfBand = curOutOfBand
  }

  return schedule
}
