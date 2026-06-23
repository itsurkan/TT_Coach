import { RepAnalysis } from '../drill2d/analyzeDrill'
import { ReferenceStandard, ReferenceRange, perPhaseRange } from '../drill2d/referenceStandard'
import { ALL_KEYS, METRIC_PHASES, Phase } from '../drill2d/drillMetrics'
import type { CoilLabel } from '../drill2d/shoulderCoil'
import type { FeedbackCue } from '../drill2d/feedbackCue'

export type MetricStatus = 'ok' | 'over' | 'under' | 'n/a'

/** Where a measured value sits relative to its ideal band. */
export function metricStatus(value: number | undefined, range: ReferenceRange | undefined): MetricStatus {
  if (value === undefined || range === undefined) return 'n/a'
  if (value > range.hi) return 'over'
  if (value < range.lo) return 'under'
  return 'ok'
}

const STATUS_CLASS: Record<MetricStatus, string> = {
  ok: 'text-emerald-400',
  over: 'text-amber-400',
  under: 'text-sky-400',
  'n/a': 'text-neutral-500',
}

/** Ukrainian metric labels (UI chrome stays UA; spoken feedback is EN per spec). */
const METRIC_LABEL: Record<string, string> = {
  elbow_angle: 'Лікоть',
  shoulder_angle: 'Плече',
  knee_bend: 'Коліна',
  torso_lean: 'Нахил корпусу',
  shoulder_tilt: 'Нахил плечей',
  hip_flexion: 'Стегно',
}

/** Short Ukrainian labels for each phase. */
const PHASE_LABEL: Record<Phase, string> = {
  backswing: 'замах',
  contact: 'удар',
  followthrough: 'завершення',
}

/** Cue display label: pattern cues carry a phase, suffix it so «лікоть (завершення)» is distinct. */
const cueLabel = (c: FeedbackCue): string =>
  c.phase ? `${c.metricKey} (${PHASE_LABEL[c.phase]})` : c.metricKey

interface Props {
  reps: RepAnalysis[]
  standard: ReferenceStandard
  enabledMetrics: Set<string>
  selectedIndex: number | null
  onSelect: (index: number) => void
  /** EXP-4: metric keys flagged as too noisy to coach (shown muted, not flagged). */
  unreliableMetrics?: string[]
  /** Task 6: voiced cue per rep, aligned to reps array (null = cadence-suppressed / clean). */
  voicedByRep?: (FeedbackCue | null)[]
}

/**
 * Render a single per-phase value cell.
 *
 * Coloring logic:
 * - If perPhaseRange returns a range → color using metricStatus (same as single-instant).
 * - If perPhaseRange returns null (no per-phase range for this metric/phase, e.g. elbow_angle,
 *   torso_lean) → render the value UNCOLORED (neutral text-neutral-400).
 * - null/undefined value → render «—».
 */
function PhaseCell({
  metricKey,
  phase,
  value,
  isNoisy,
}: {
  metricKey: string
  phase: Phase
  value: number | null | undefined
  isNoisy: boolean
}) {
  const range = perPhaseRange(metricKey, phase) ?? undefined
  const status = metricStatus(value === null ? undefined : value, range)
  const formatted = value === undefined || value === null ? '—' : `${Math.round(value)}°`

  if (isNoisy) {
    return (
      <span className="text-neutral-600 line-through" title="Нестабільна метрика — приглушено">
        {formatted}
      </span>
    )
  }

  // No per-phase range → neutral, no coloring
  if (range === undefined) {
    return (
      <span className="text-neutral-400">
        {formatted}
      </span>
    )
  }

  return (
    <span className={STATUS_CLASS[status]}>
      {formatted}
      {value !== null && value !== undefined && status !== 'ok' ? ` (${status})` : ''}
    </span>
  )
}

/** Look up the Phase array for a metric key without repeating the cast. */
const phasesOf = (k: string): Phase[] | undefined =>
  METRIC_PHASES[k as keyof typeof METRIC_PHASES] as Phase[] | undefined

/** Ukrainian label for each coil label. */
const COIL_UA: Record<CoilLabel, string> = {
  opened: 'розкрив',
  limited: 'слабка',
}

/**
 * Renders the qualitative coil label for one rep.
 * Neutral/muted styling — no degree, no severity color (trust rule).
 */
function CoilCell({ coil }: { coil: RepAnalysis['coil'] }) {
  if (coil === null) {
    return <span className="text-neutral-600">—</span>
  }
  return (
    <span className="text-neutral-400 italic">
      {COIL_UA[coil.label]}
    </span>
  )
}

export function DrillResultsTable({ reps, standard, enabledMetrics, selectedIndex, onSelect, unreliableMetrics = [], voicedByRep }: Props) {
  const cols = ALL_KEYS.filter(k => enabledMetrics.has(k))
  const noisy = new Set(unreliableMetrics)

  return (
    <table className="w-full text-xs border-collapse">
      <thead>
        <tr className="text-neutral-400 text-left">
          <th className="py-1 pr-2">#</th>
          {cols.map(k => {
            const phases = phasesOf(k)
            if (phases !== undefined && phases.length > 0) {
              // Metric with per-phase breakdown: one th spanning all phase sub-columns
              return (
                <th key={k} className="py-1 px-2" colSpan={phases.length}>
                  {METRIC_LABEL[k] ?? k}
                  {noisy.has(k) && <span className="text-neutral-500" title="Нестабільна метрика — підказки приглушено (EXP-2)"> ~шум</span>}
                </th>
              )
            }
            // Metric without phases (shoulder_tilt): single column header
            return (
              <th key={k} className="py-1 px-2">
                {METRIC_LABEL[k] ?? k}
                {noisy.has(k) && <span className="text-neutral-500" title="Нестабільна метрика — підказки приглушено (EXP-2)"> ~шум</span>}
              </th>
            )
          })}
          <th
            className="py-1 px-2 text-neutral-500 italic"
            title="Якісний індикатор скрутки корпусу (фокусне скорочення плечей) — НЕ градуси; низька достовірність; тільки при наявності замаху"
          >
            ~Скрутка
          </th>
          <th className="py-1 px-2">Всі зауваження</th>
          <th className="py-1 px-2">Підказка</th>
        </tr>
        {/* Phase sub-header row — only rendered when at least one metric has phases */}
        {cols.some(k => METRIC_PHASES[k as keyof typeof METRIC_PHASES]) && (
          <tr className="text-neutral-600 text-left text-[10px]">
            <th className="pr-2" />
            {cols.map(k => {
              const phases = phasesOf(k)
              if (phases !== undefined && phases.length > 0) {
                return phases.map(p => (
                  <th key={`${k}-${p}`} className="px-2 pb-1 font-normal">
                    {PHASE_LABEL[p]}
                  </th>
                ))
              }
              // No phases — single empty sub-header cell
              return <th key={k} className="px-2 pb-1" />
            })}
            <th className="px-2 pb-1" />{/* coil sub-header placeholder */}
            <th className="px-2 pb-1" />{/* Всі зауваження sub-header placeholder */}
            <th className="px-2 pb-1" />{/* Підказка sub-header placeholder */}
          </tr>
        )}
      </thead>
      <tbody>
        {reps.map((rep, i) => {
          return (
            <tr
              key={i}
              className={`cursor-pointer hover:bg-neutral-800 ${selectedIndex === i ? 'bg-neutral-800' : ''}`}
              onClick={() => onSelect(i)}
            >
              <td className="py-1 pr-2 text-neutral-300">{i + 1}</td>
              {cols.map(k => {
                const phases = phasesOf(k)

                if (phases !== undefined && phases.length > 0) {
                  // Per-phase cells
                  return phases.map(phase => {
                    const phaseValues = rep.perPhase?.[k]
                    // phase key absent (e.g. backswing on unpaired cycle) → undefined → «—»
                    const value = phaseValues !== undefined ? phaseValues[phase] : undefined
                    return (
                      <td key={`${k}-${phase}`} className="py-1 px-2">
                        <PhaseCell metricKey={k} phase={phase} value={value} isNoisy={noisy.has(k)} />
                      </td>
                    )
                  })
                }

                // No phases: single-instant cell (shoulder_tilt only). Colored via standard.ranges.
                // NB: torso_lean IS in METRIC_PHASES so it renders through PhaseCell above —
                // uncolored, because perPhaseRange returns null for it (display-only). It never hits this branch.
                const v = rep.metrics[k]
                const status = metricStatus(v, standard.ranges[k])
                if (noisy.has(k)) {
                  return (
                    <td key={k} className="py-1 px-2 text-neutral-600 line-through" title="Нестабільна метрика — приглушено">
                      {v === undefined ? '—' : `${Math.round(v)}°`}
                    </td>
                  )
                }
                return (
                  <td key={k} className={`py-1 px-2 ${STATUS_CLASS[status]}`}>
                    {v === undefined ? '—' : `${Math.round(v)}° ${status === 'ok' ? '' : `(${status})`}`}
                  </td>
                )
              })}
              <td className="py-1 px-2">
                <CoilCell coil={rep.coil} />
              </td>
              <td className="py-1 px-2 text-neutral-300">
                {!rep.placementOk
                  ? '⚠ перевір кут камери (placement)'
                  : rep.cues.length > 0
                    ? rep.cues.map(cueLabel).join(', ')
                    : '✓'}
              </td>
              <td className="py-1 px-2 text-sky-300">
                {voicedByRep?.[i] ? cueLabel(voicedByRep[i]!) : '—'}
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
