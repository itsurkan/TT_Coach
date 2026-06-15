/**
 * Drill analysis orchestrator — adapted from Kotlin ForehandDriveDrillAnalyzer.
 * Same pipeline (detect → ForwardStrokeFilter → RepFilter → per-rep yaw → metrics →
 * cues → cadenced feedback), but cues compare to the external IDEAL range
 * (feedbackEngine), not a personal baseline. Detection runs on plain aspectRatio so
 * the M0 count-golden (andrii_1: 23 raw / 15 reps) is preserved; per-rep metrics use
 * the yaw-corrected xScale.
 */
import { Handedness, Stroke2D } from './types'
import { PoseSequence2D } from './parsePoseV2'
import { xScaleFor, MAX_YAW_DEG } from './geometry'
import { detectStrokes, StrokeDetectorOptions } from './strokeDetector2d'
import { filterForwardStrokes } from './forwardStrokeFilter'
import { filterCycleReps } from './repFilter'
import { pairCycles, MAX_PAIR_GAP_MS } from './cyclePairing'
import { filterStationaryCycles, DEFAULT_MAX_TRAVEL_TORSO } from './locomotionFilter'
import { DEFAULT_MIN_SCORE } from './facing'
import { estimateYawForStroke } from './cameraYaw'
import { extractAtPeak } from './drillMetrics'
import { evaluateRep } from './feedbackEngine'
import { FeedbackCue } from './feedbackCue'
import { formatCue, positiveMessage } from './messageCatalog'
import { FeedbackCadencePolicy } from './cadencePolicy'
import { ReferenceStandard } from './referenceStandard'

/** |yaw| beyond this → rep excluded from feedback (CLAUDE.md: ~30° gate). */
export const DEFAULT_MAX_CAMERA_YAW_DEG = 30

/** EXP-1: a persistent single-issue cue is re-surfaced at most this often (ms),
 *  instead of repeating it every rep. */
export const REMINDER_INTERVAL_MS = 8000

/** EXP-2: a metric whose cross-rep IQR exceeds this (degrees) is treated as
 *  unreliable (measurement noise) and not coached. ~20° cleanly separates blur
 *  artifacts (andrii elbow IQR≈28) from real systematic faults (video_3 lean IQR≈1). */
export const UNRELIABLE_IQR_DEG = 20
/** Need at least this many measured reps before judging a metric's reliability. */
export const MIN_REPS_FOR_RELIABILITY = 4

// Intentional nearest-rank quantile (NOT Tukey/linear-interpolated). UNRELIABLE_IQR_DEG
// is tuned against THIS estimator, so don't switch to interpolation without re-tuning.
// Empty input is unreachable (callers gate on >= MIN_REPS_FOR_RELIABILITY).
function quantile(sorted: number[], p: number): number {
  if (sorted.length === 0) return NaN
  return sorted[Math.min(sorted.length - 1, Math.floor(p * sorted.length))]
}

/**
 * EXP-2: keys of metrics whose value is too inconsistent across the player's reps
 * to coach on. Uses the inter-quartile range (robust to the odd glitch frame).
 */
export function unreliableMetricKeys(reps: RepAnalysis[]): Set<string> {
  const byKey: Record<string, number[]> = {}
  for (const rep of reps) {
    if (!rep.placementOk) continue
    for (const [key, value] of Object.entries(rep.metrics)) (byKey[key] ??= []).push(value)
  }
  const unreliable = new Set<string>()
  for (const [key, values] of Object.entries(byKey)) {
    if (values.length < MIN_REPS_FOR_RELIABILITY) continue
    const sorted = [...values].sort((a, b) => a - b)
    const iqr = quantile(sorted, 0.75) - quantile(sorted, 0.25)
    if (iqr > UNRELIABLE_IQR_DEG) unreliable.add(key)
  }
  return unreliable
}

/**
 * EXP-1 cue selection: prefer the most-severe cue for an issue OTHER than the one
 * just spoken (variety). If the only cues are the same metric as last time, only
 * re-surface it once enough time has passed (spaced reminder), else stay quiet.
 * `cues` is already severity-sorted (feedbackEngine).
 */
export function pickVariedCue(
  cues: FeedbackCue[],
  lastMetric: string | null,
  lastSpokenMsByMetric: Record<string, number>,
  nowMs: number,
): FeedbackCue | null {
  if (cues.length === 0) return null
  const different = cues.find(c => c.metricKey !== lastMetric)
  if (different) return different
  // Only same-metric cues remain (single persistent fault): space the reminder out.
  const top = cues[0]
  const lastMs = lastSpokenMsByMetric[top.metricKey]
  if (lastMs === undefined || nowMs - lastMs >= REMINDER_INTERVAL_MS) return top
  return null
}

export interface RepAnalysis {
  stroke: Stroke2D
  metrics: Record<string, number>
  cues: FeedbackCue[]
  /** Yaw used for this rep (override or pre-stroke estimate); null = unmeasurable. */
  cameraYawDeg: number | null
  /** false → camera too far off side view (or unmeasurable): no cues, metrics diagnostic only. */
  placementOk: boolean
}

export interface SpokenFeedback {
  timestampMs: number
  message: string
  /** null = positive reinforcement, not a correction. */
  cue: FeedbackCue | null
}

/** EXP-3: the single actionable takeaway for the whole set. */
export interface SessionFocus {
  /** Dominant metric to work on, or null when the set was clean. */
  metricKey: string | null
  /** Coaching message — the focus, or praise when clean. */
  message: string
  /** Reps where this fault appeared / coachable reps. */
  count: number
  total: number
}

export interface DrillAnalysisReport {
  reps: RepAnalysis[]
  feedback: SpokenFeedback[]
  /** Session summary: false → over half the reps had bad camera placement. */
  placementOk: boolean
  rawPeakCount: number
  forwardRepCount: number
  /** EXP-3: one-line "what to work on" for the whole set. */
  focus: SessionFocus
  /** EXP-2/4: metric keys whose cross-rep spread made them untrustworthy (cues dropped,
   *  values shown but flagged as noisy in the UI). */
  unreliableMetrics: string[]
  /** EXP-9: reliable metrics the player kept in the ideal band on most reps (their strengths). */
  strengths: string[]
  /** EXP-9: coachable reps with zero faults. */
  cleanReps: number
}

export interface DrillAnalysisConfig {
  handedness: Handedness
  drillType: string
  standard: ReferenceStandard
  /** Metric keys to evaluate; omitted → all metrics in the standard. */
  enabledMetrics?: Set<string>
  /** Manual yaw applied to ALL reps; null → per-rep auto-estimate; undefined → default 0. */
  cameraYawDeg?: number | null
  maxCameraYawDeg?: number
  detector?: StrokeDetectorOptions
  cadence?: { minIntervalMs: number; maxIntervalMs: number }
  /** EXPERIMENTAL locomotion gate (L-30): drop reps whose hip-mid travels more
   *  than this many torso-lengths (walking). 0/undefined = off. */
  hipTravelMaxTorso?: number
  /** Cycle-pairing gap cap (ms); undefined → MAX_PAIR_GAP_MS (800). */
  maxPairGapMs?: number
}

export function analyzeDrill(seq: PoseSequence2D, config: DrillAnalysisConfig): DrillAnalysisReport {
  const minScore = config.detector?.minScore ?? DEFAULT_MIN_SCORE
  const maxYaw = config.maxCameraYawDeg ?? DEFAULT_MAX_CAMERA_YAW_DEG
  // undefined → default 0 (demoable on Videos/); explicit null → per-rep auto-estimate.
  const yawOverride = config.cameraYawDeg === undefined ? 0 : config.cameraYawDeg

  // Detection on plain aspect (yaw 0) — preserves the M0 count-golden.
  const detectXScale = xScaleFor(seq.aspectRatio, 0)
  const rawStrokes = detectStrokes(seq.frames, config.handedness, detectXScale, seq.intervalMs, config.detector)
  const forwardStrokes = filterForwardStrokes(rawStrokes, seq.frames, config.handedness, minScore)
  const cycles = pairCycles(rawStrokes, forwardStrokes, seq.frames, seq.intervalMs, config.maxPairGapMs ?? MAX_PAIR_GAP_MS)
  const banded = filterCycleReps(cycles)
  // undefined → default-on (DEFAULT_MAX_TRAVEL_TORSO); explicit 0 → gate off.
  const hipTravelMax = config.hipTravelMaxTorso ?? DEFAULT_MAX_TRAVEL_TORSO
  const keptCycles = hipTravelMax > 0
    ? filterStationaryCycles(banded, seq.frames, detectXScale, hipTravelMax, minScore)
    : banded
  // Metrics anchor stays the drive peak — analysis sees the same frames as before.
  const reps = keptCycles.map(c => c.drive)

  const cadence = new FeedbackCadencePolicy(
    config.cadence?.minIntervalMs ?? 3000,
    config.cadence?.maxIntervalMs ?? 5000,
  )

  const repAnalyses: RepAnalysis[] = reps.map(stroke => {
    const yaw = yawOverride !== null
      ? yawOverride
      : estimateYawForStroke(seq.frames, stroke, seq.aspectRatio, seq.intervalMs, minScore)
    const placementOk = yaw !== null && Math.abs(yaw) <= maxYaw
    // Beyond the gate (or unmeasurable) the 1/cos model is unreliable: fall back to
    // plain aspect; this rep's metrics become diagnostics only (no cues).
    // Also cap at MAX_YAW_DEG (60°) to avoid xScaleFor throwing.
    const metricsYaw = placementOk && yaw !== null && Math.abs(yaw) <= MAX_YAW_DEG
      ? yaw
      : 0
    const xScale = xScaleFor(seq.aspectRatio, metricsYaw)
    const metrics = extractAtPeak(seq.frames, stroke.peakFrame, config.handedness, xScale, seq.intervalMs, minScore)
    const cues = placementOk ? evaluateRep(metrics, config.standard, config.enabledMetrics) : []
    return { stroke, metrics, cues, cameraYawDeg: yaw, placementOk }
  })

  // EXP-2 (reliability/trust gating): a metric whose value swings wildly across the
  // player's reps is not a stable coaching signal — it's measurement noise (e.g.
  // RTMPose mis-locates the fast-swinging forearm at the wrist-speed peak: andrii's
  // elbow reads 35–124° across reps). The CLAUDE.md trust rule says only coach precise
  // degrees we can stand behind, so we DROP cues for any metric whose cross-rep IQR
  // exceeds UNRELIABLE_IQR_DEG. Consistent-but-off metrics (video_3 lean, IQR≈1) are
  // untouched — they're real systematic faults worth coaching.
  // Safe to mutate rep.cues: repAnalyses is rebuilt fresh on every analyzeDrill() call
  // (reps.map above), so no RepAnalysis reference survives to be double-filtered.
  const unreliable = unreliableMetricKeys(repAnalyses)
  if (unreliable.size > 0) {
    for (const rep of repAnalyses) {
      rep.cues = rep.cues.filter(c => !unreliable.has(c.metricKey))
    }
  }

  // EXP-1 (variety-aware feedback): a real coach surfaces different faults across a
  // set instead of repeating the same line every rep. We (a) prefer the most-severe
  // cue addressing a DIFFERENT issue than the one just spoken, and (b) for a
  // persistent single issue, space reminders out (REMINDER_INTERVAL_MS) rather than
  // nagging it every rep. The cadence rate-limit still applies on top.
  const feedback: SpokenFeedback[] = []
  let lastMetric: string | null = null
  let positiveCount = 0 // EXP-6: rotate positive phrasings
  const lastSpokenMsByMetric: Record<string, number> = {}
  for (const rep of repAnalyses) {
    if (!rep.placementOk) continue // silent rep; UI surfaces the placement flag
    const atMs = rep.stroke.endFrame * seq.intervalMs
    const chosen = pickVariedCue(rep.cues, lastMetric, lastSpokenMsByMetric, atMs)
    const cue = cadence.offer(atMs, chosen ? [chosen] : [])
    if (cue !== null) {
      feedback.push({ timestampMs: atMs, message: formatCue(cue), cue })
      lastMetric = cue.metricKey
      lastSpokenMsByMetric[cue.metricKey] = atMs
    } else if (rep.cues.length === 0 && Object.keys(rep.metrics).length > 0 && cadence.offerPositive(atMs)) {
      feedback.push({ timestampMs: atMs, message: positiveMessage(positiveCount++), cue: null })
    }
  }

  const okCount = repAnalyses.filter(r => r.placementOk).length
  const coached = repAnalyses.filter(r => r.placementOk)
  return {
    reps: repAnalyses,
    feedback,
    placementOk: repAnalyses.length === 0 || okCount * 2 >= repAnalyses.length,
    rawPeakCount: rawStrokes.length,
    forwardRepCount: reps.length,
    focus: sessionFocus(repAnalyses),
    unreliableMetrics: [...unreliable],
    strengths: sessionStrengths(coached, unreliable, config.standard, config.enabledMetrics),
    cleanReps: coached.filter(r => r.cues.length === 0).length,
  }
}

/** EXP-9: STRENGTH_FRACTION of coached reps must keep a reliable metric in-band for it
 *  to count as a strength worth praising. */
export const STRENGTH_FRACTION = 0.8

/**
 * EXP-9: reliable metrics the player held inside the ideal band on most reps. A metric
 * is a strength when it's measured, not flagged unreliable, and in-range on
 * ≥ STRENGTH_FRACTION of the reps where it was measured.
 */
export function sessionStrengths(
  coached: RepAnalysis[],
  unreliable: Set<string>,
  standard: ReferenceStandard,
  enabledKeys?: Set<string>,
): string[] {
  const strengths: string[] = []
  for (const [key, range] of Object.entries(standard.ranges)) {
    if (unreliable.has(key)) continue
    if (enabledKeys && !enabledKeys.has(key)) continue
    let measured = 0
    let inBand = 0
    for (const rep of coached) {
      const v = rep.metrics[key]
      if (v === undefined) continue
      measured++
      if (v >= range.lo && v <= range.hi) inBand++
    }
    if (measured >= MIN_REPS_FOR_RELIABILITY && inBand >= STRENGTH_FRACTION * measured) {
      strengths.push(key)
    }
  }
  return strengths
}

function medianSeverity(cues: FeedbackCue[]): number {
  if (cues.length === 0) return 0
  const s = cues.map(c => c.severity).sort((a, b) => a - b)
  return s[Math.floor(s.length / 2)]
}

/**
 * EXP-3: one actionable takeaway for the whole set. The dominant fault is the
 * (reliable, post-EXP-2) metric flagged on the most reps; ties break toward the more
 * severe one. A representative (median-severity) cue supplies the wording. When no
 * rep was flagged, return praise.
 */
export function sessionFocus(reps: RepAnalysis[]): SessionFocus {
  const coached = reps.filter(r => r.placementOk)
  const total = coached.length
  if (total === 0) {
    return { metricKey: null, message: 'No coachable reps — fix the camera placement and retry.', count: 0, total: 0 }
  }
  const flaggedReps: Record<string, number> = {}
  const cuesByKey: Record<string, FeedbackCue[]> = {}
  for (const rep of coached) {
    const seen = new Set<string>()
    for (const c of rep.cues) {
      ;(cuesByKey[c.metricKey] ??= []).push(c)
      if (!seen.has(c.metricKey)) { flaggedReps[c.metricKey] = (flaggedReps[c.metricKey] ?? 0) + 1; seen.add(c.metricKey) }
    }
  }
  const keys = Object.keys(flaggedReps)
  if (keys.length === 0) {
    return { metricKey: null, message: 'Great set — your technique stayed close to the standard.', count: 0, total }
  }
  keys.sort((a, b) => (flaggedReps[b] - flaggedReps[a]) || (medianSeverity(cuesByKey[b]) - medianSeverity(cuesByKey[a])))
  const top = keys[0]
  const cues = [...cuesByKey[top]].sort((a, b) => a.severity - b.severity)
  const representative = cues[Math.floor(cues.length / 2)]
  return { metricKey: top, message: `Main focus: ${formatCue(representative)}`, count: flaggedReps[top], total }
}
