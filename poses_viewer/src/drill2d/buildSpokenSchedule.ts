/**
 * The pure voice core. Walks per-rep DECISION cues (from decideRepCues — already
 * band-gated) in time order and produces a deterministic spoken schedule:
 *   select → cadence → praise → skip-stale → format.
 * It never re-decides what's wrong (no band math here) — it only chooses which of a
 * rep's cues to voice and when. Returns voicedByRep so the table can show exactly
 * what was spoken per rep.
 */
import type { FeedbackCue } from './feedbackCue'
import type { FeedbackSettings } from './feedbackSettings'
import type { Lang, MetricKey, PhraseSet } from './voiceStyle'
import { clipKey, lookupClip, type ClipManifest } from './voiceClips'

/** Practitioner WPM budget: ~150 wpm ≈ 2.5 words/s. */
export const BASE_WPM = 150

export interface RepTiming { strokeStartMs: number; contactMs: number; strokeEndMs: number }
export interface RepInput {
  cues: FeedbackCue[]   // severity-desc, already gated by decideRepCues
  timing: RepTiming
  coachable: boolean    // false → bad camera / unmeasurable: emit nothing, state untouched
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
export interface ScheduleResult { schedule: SpokenSchedule; voicedByRep: (FeedbackCue | null)[] }

export function estimateDurationMs(text: string, rate: number): number {
  const words = text.trim().split(/\s+/).filter(Boolean).length
  const safeRate = rate > 0 ? rate : 1
  const wordsPerSec = (BASE_WPM * safeRate) / 60
  return wordsPerSec > 0 ? (words / wordsPerSec) * 1000 : 0
}

export function nextStrokeStartAfter(strokeStartTimes: number[], atMs: number): number {
  for (const t of strokeStartTimes) if (t > atMs) return t
  return Number.POSITIVE_INFINITY
}

const dirToPhrase = (d: FeedbackCue['direction']): 'up' | 'down' => (d === 'too_high' ? 'up' : 'down')

export function buildSpokenSchedule(
  reps: RepInput[],
  strokeStartTimes: number[],
  settings: FeedbackSettings,
  phrases: PhraseSet,
  lang: Lang,
  rate: number,
  clipManifest?: ClipManifest | null,
): ScheduleResult {
  const schedule: SpokenSchedule = []
  const voicedByRep: (FeedbackCue | null)[] = []

  let lastSpokenMs = Number.NEGATIVE_INFINITY
  let prevOutOfBand = new Set<string>()
  const lastCuedMs = new Map<string, number>()
  let lastCueMetric: string | null = null
  let cleanStreak = 0
  let praiseIndex = 0

  const durationOf = (text: string): { ms: number; key?: string } => {
    const clip = lookupClip(clipManifest ?? null, lang, text)
    if (clip) return { ms: clip.durationMs, key: clipKey(lang, text) }
    return { ms: estimateDurationMs(text, rate) }
  }
  const fits = (atMs: number, ms: number): boolean => {
    if (!settings.skipStaleEnabled) return true
    const next = nextStrokeStartAfter(strokeStartTimes, atMs)
    return atMs + ms <= next - settings.skipStaleMarginMs
  }

  for (const rep of reps) {
    if (!rep.coachable) { voicedByRep.push(null); continue }
    const atMs = rep.timing.strokeEndMs + settings.postStrokeGapMs
    const curOutOfBand = new Set<string>(rep.cues.map(c => c.metricKey))
    cleanStreak = curOutOfBand.size === 0 ? cleanStreak + 1 : 0

    // Praise candidate (correction wins if both qualify).
    let praiseCandidate = false
    if (settings.praiseEnabled) {
      const corrected = [...prevOutOfBand].some(k => !curOutOfBand.has(k))
      if (settings.praiseOnCorrection && corrected) praiseCandidate = true
      else if (settings.praiseOnStreak && cleanStreak > 0 && cleanStreak >= settings.praiseStreakLen) praiseCandidate = true
    }

    // Cue selection: cues are already severity-desc; drop reminder-suppressed, vary-aware.
    let chosen: FeedbackCue | null = null
    const eligible = rep.cues.filter(c => {
      const last = lastCuedMs.get(c.metricKey)
      return last === undefined || atMs - last >= settings.reminderIntervalMs
    })
    if (eligible.length > 0) {
      chosen = eligible[0]
      if (settings.varyCues && chosen.metricKey === lastCueMetric && eligible.length > 1) chosen = eligible[1]
    }

    let voiced: FeedbackCue | null = null
    if (chosen !== null) {
      if (atMs - lastSpokenMs >= settings.correctiveMinGapMs) {
        const text = phrases.cues[chosen.metricKey as MetricKey][dirToPhrase(chosen.direction)]
        const { ms, key } = durationOf(text)
        if (fits(atMs, ms)) {
          schedule.push({ atMs, text, lang, kind: 'cue', metricKey: chosen.metricKey as MetricKey, clipKey: key, estDurationMs: ms })
          lastSpokenMs = atMs
          lastCueMetric = chosen.metricKey
          lastCuedMs.set(chosen.metricKey, atMs)
          voiced = chosen
        }
      }
    } else if (praiseCandidate) {
      if (atMs - lastSpokenMs >= settings.praiseMinSilenceMs && phrases.praise.length > 0) {
        const text = phrases.praise[praiseIndex % phrases.praise.length]
        const { ms, key } = durationOf(text)
        if (fits(atMs, ms)) {
          schedule.push({ atMs, text, lang, kind: 'praise', metricKey: null, clipKey: key, estDurationMs: ms })
          lastSpokenMs = atMs
          praiseIndex++
        }
      }
    }

    voicedByRep.push(voiced)
    prevOutOfBand = curOutOfBand
  }

  return { schedule, voicedByRep }
}
