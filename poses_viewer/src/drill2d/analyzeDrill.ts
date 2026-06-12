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
import { filterReps } from './repFilter'
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

export interface DrillAnalysisReport {
  reps: RepAnalysis[]
  feedback: SpokenFeedback[]
  /** Session summary: false → over half the reps had bad camera placement. */
  placementOk: boolean
  rawPeakCount: number
  forwardRepCount: number
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
  const reps = filterReps(forwardStrokes)

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

  const feedback: SpokenFeedback[] = []
  for (const rep of repAnalyses) {
    if (!rep.placementOk) continue // silent rep; UI surfaces the placement flag
    const atMs = rep.stroke.endFrame * seq.intervalMs
    const cue = cadence.offer(atMs, rep.cues)
    if (cue !== null) {
      feedback.push({ timestampMs: atMs, message: formatCue(cue), cue })
    } else if (rep.cues.length === 0 && Object.keys(rep.metrics).length > 0 && cadence.offerPositive(atMs)) {
      feedback.push({ timestampMs: atMs, message: positiveMessage(), cue: null })
    }
  }

  const okCount = repAnalyses.filter(r => r.placementOk).length
  return {
    reps: repAnalyses,
    feedback,
    placementOk: repAnalyses.length === 0 || okCount * 2 >= repAnalyses.length,
    rawPeakCount: rawStrokes.length,
    forwardRepCount: reps.length,
  }
}
