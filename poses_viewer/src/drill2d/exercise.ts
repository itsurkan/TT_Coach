/**
 * Exercise = the TRAINING settings of a drill: which body parts to focus on, where
 * the reference angles come from, and how strict the grading is. This is the FIRST of
 * the three independent settings types in the viewer; feedback (when/how the coach
 * speaks — feedbackSettings.ts) and voice (TTS persona — voiceStyle.ts) stay GLOBAL and
 * separate. An exercise carries NO feedback/voice knobs.
 *
 * Pure helpers only (no localStorage, no React) so they are node-testable; persistence
 * lives in exerciseStore.ts (mirrors voiceStyleStore.ts). The exercise drives the
 * existing grading pipeline by producing *effective* objects in StrokesPage — it does
 * NOT edit the golden-parity-tested core (decideRepCues / analyzeDrill).
 */
import { VOICE_METRIC_KEYS, type MetricKey } from './voiceStyle'
import type { ReferenceStandard, ReferenceRange, MetricKey as RefMetricKey } from './referenceStandard'
import type { Phase } from './drillMetrics'

/** Body-part focus the player picks; each maps to one or more in-plane metrics. */
export type FocusArea = 'arm' | 'shoulder' | 'legs' | 'torso' | 'hip'

/** Focus chips, in display order, with Ukrainian labels (repo UI convention). */
export const FOCUS_AREAS: { id: FocusArea; labelUa: string }[] = [
  { id: 'arm', labelUa: 'Рука' },
  { id: 'shoulder', labelUa: 'Плечі' },
  { id: 'legs', labelUa: 'Ноги' },
  { id: 'torso', labelUa: 'Корпус' },
  { id: 'hip', labelUa: 'Таз' },
]

/**
 * Focus → which metrics are active. `hip_flexion` is intentionally shared by `legs`
 * and `hip`; the union dedupes it. Keep values valid MetricKeys.
 */
export const FOCUS_TO_METRICS: Record<FocusArea, MetricKey[]> = {
  arm: ['elbow_angle', 'shoulder_angle'],
  shoulder: ['shoulder_tilt'],
  legs: ['knee_bend', 'hip_flexion'],
  torso: ['torso_lean'],
  hip: ['hip_flexion'],
}

export type ReferenceSource = 'standard' | 'personal-baseline'

/** Per-phase override map — same shape as referenceStandard's PER_PHASE_RANGES. */
export type PerPhaseRanges = Partial<Record<RefMetricKey, Partial<Record<Phase, ReferenceRange>>>>

export interface Exercise {
  id: string
  name: string
  /** Built-in preset — merged at load, never persisted (mirrors VoiceStyle.builtin). */
  builtin?: boolean
  /** Registered drill type; only 'forehand_drive' exists today. */
  drillType: string
  /** Empty ⇒ all metrics active (no narrowing); otherwise the union of mapped metrics. */
  focusAreas: FocusArea[]
  /** 'standard' = textbook ideal bands; 'personal-baseline' = recenter on the player's own medians. */
  referenceSource: ReferenceSource
  /** Tolerance dial: 1 = same bands, >1 stricter (narrower), <1 looser. */
  strictness: number
  /** Advanced: per-phase target overrides (collapsed UI). Merged onto the global defaults. */
  perPhaseOverrides?: PerPhaseRanges
}

export const DEFAULT_DRILL_TYPE = 'forehand_drive'
const ALL_FOCUS_AREAS: FocusArea[] = ['arm', 'shoulder', 'legs', 'torso', 'hip']

/** Create-from-scratch template: every focus area, textbook reference, neutral strictness. */
export function defaultExercise(): Exercise {
  return {
    id: '',
    name: '',
    drillType: DEFAULT_DRILL_TYPE,
    focusAreas: [...ALL_FOCUS_AREAS],
    referenceSource: 'standard',
    strictness: 1,
  }
}

/**
 * Backfill any missing field over the default so an older stored exercise never has an
 * undefined field (forward-compat, mirrors normalizeStyle). Keeps id/name/builtin verbatim.
 */
export function normalizeExercise(raw: Partial<Exercise> & { id: string; name: string }): Exercise {
  const d = defaultExercise()
  const focusAreas = Array.isArray(raw.focusAreas)
    ? raw.focusAreas.filter((f): f is FocusArea => ALL_FOCUS_AREAS.includes(f as FocusArea))
    : d.focusAreas
  return {
    id: raw.id,
    name: raw.name,
    builtin: !!raw.builtin,
    drillType: typeof raw.drillType === 'string' ? raw.drillType : d.drillType,
    focusAreas,
    referenceSource: raw.referenceSource === 'personal-baseline' ? 'personal-baseline' : 'standard',
    strictness: typeof raw.strictness === 'number' && raw.strictness > 0 ? raw.strictness : d.strictness,
    perPhaseOverrides: raw.perPhaseOverrides && typeof raw.perPhaseOverrides === 'object'
      ? raw.perPhaseOverrides
      : undefined,
  }
}

/**
 * Active metric set from focus, in canonical VOICE_METRIC_KEYS order (stable for the UI
 * and Set membership). Empty focus ⇒ all metrics (no narrowing) so a freshly-made
 * exercise still produces feedback. The default exercise (all focus areas) therefore
 * reproduces VOICE_METRIC_KEYS exactly — the golden-parity guard.
 */
export function effectiveEnabledMetrics(ex: Exercise): MetricKey[] {
  if (ex.focusAreas.length === 0) return [...VOICE_METRIC_KEYS]
  const active = new Set<MetricKey>(ex.focusAreas.flatMap(f => FOCUS_TO_METRICS[f] ?? []))
  return VOICE_METRIC_KEYS.filter(k => active.has(k))
}

/**
 * Compose the global feedback bandWidthMult with the exercise strictness. Higher
 * strictness ⇒ narrower band ⇒ smaller effective multiplier (divide). strictness 1 is a
 * no-op, so the default exercise leaves the global slider's value untouched.
 */
export function effectiveBandWidthMult(ex: Exercise, globalMult: number): number {
  const s = ex.strictness > 0 ? ex.strictness : 1
  return globalMult / s
}

// ---------------------------------------------------------------------------
// Personal-baseline derivation ("calibrate to the player, don't re-teach")
// ---------------------------------------------------------------------------

/** Need at least this many measured reps for a metric before recentering on the player. */
export const MIN_BASELINE_SAMPLES = 3

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}

function recenter(range: ReferenceRange, center: number): ReferenceRange {
  const half = (range.hi - range.lo) / 2
  return { lo: center - half, hi: center + half, evidence: range.evidence, source: 'personal-baseline (recentred on your median)' }
}

/** Minimal per-rep shape the baseline derivation reads (decoupled from RepAnalysis). */
export interface BaselineRep {
  metrics: Record<string, number>
  perPhase: Record<string, Partial<Record<Phase, number | null>>>
  placementOk: boolean
}

/**
 * Recenter the SINGLE-INSTANT ideal bands on the player's own per-metric medians, keeping
 * each band's width (then strictness scales it downstream). Metrics with fewer than
 * MIN_BASELINE_SAMPLES measured reps fall back to the textbook band. Used for shoulder_tilt
 * + sessionStrengths (the only single-instant consumers).
 */
export function deriveBaselineStandard(reps: BaselineRep[], base: ReferenceStandard): ReferenceStandard {
  const ok = reps.filter(r => r.placementOk)
  const ranges: Record<string, ReferenceRange> = {}
  for (const [key, range] of Object.entries(base.ranges)) {
    const samples = ok.map(r => r.metrics[key]).filter((v): v is number => typeof v === 'number')
    ranges[key] = samples.length >= MIN_BASELINE_SAMPLES ? recenter(range, median(samples)) : range
  }
  return { drillType: base.drillType, ranges }
}

/**
 * Recenter the PER-PHASE pattern bands on the player's own per-phase medians (keeping each
 * width). Without this, toggling "my baseline" would only affect shoulder_tilt and leave
 * the five movement metrics on textbook bands — a dishonest half-feature. Phases with too
 * few measured reps keep the base band.
 */
export function deriveBaselinePerPhase(reps: BaselineRep[], base: PerPhaseRanges): PerPhaseRanges {
  const ok = reps.filter(r => r.placementOk)
  const out: PerPhaseRanges = {}
  for (const [metricKey, phases] of Object.entries(base) as [RefMetricKey, Partial<Record<Phase, ReferenceRange>>][]) {
    if (!phases) continue
    const phaseOut: Partial<Record<Phase, ReferenceRange>> = {}
    for (const [phaseStr, range] of Object.entries(phases) as [Phase, ReferenceRange][]) {
      const samples = ok
        .map(r => r.perPhase[metricKey]?.[phaseStr])
        .filter((v): v is number => typeof v === 'number')
      phaseOut[phaseStr] = samples.length >= MIN_BASELINE_SAMPLES ? recenter(range, median(samples)) : range
    }
    out[metricKey] = phaseOut
  }
  return out
}

/** Merge manual per-phase overrides onto the base map (override wins per metric+phase). */
export function mergePerPhaseOverrides(base: PerPhaseRanges, overrides?: PerPhaseRanges): PerPhaseRanges {
  if (!overrides) return base
  const out: PerPhaseRanges = {}
  const keys = new Set<RefMetricKey>([...Object.keys(base), ...Object.keys(overrides)] as RefMetricKey[])
  for (const k of keys) {
    out[k] = { ...(base[k] ?? {}), ...(overrides[k] ?? {}) }
  }
  return out
}
