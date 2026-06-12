import { RepAnalysis } from '../drill2d/analyzeDrill'
import { ReferenceStandard, ReferenceRange } from '../drill2d/referenceStandard'
import { ALL_KEYS } from '../drill2d/drillMetrics'

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
}

interface Props {
  reps: RepAnalysis[]
  standard: ReferenceStandard
  enabledMetrics: Set<string>
  selectedIndex: number | null
  onSelect: (index: number) => void
  /** EXP-4: metric keys flagged as too noisy to coach (shown muted, not flagged). */
  unreliableMetrics?: string[]
}

export function DrillResultsTable({ reps, standard, enabledMetrics, selectedIndex, onSelect, unreliableMetrics = [] }: Props) {
  const cols = ALL_KEYS.filter(k => enabledMetrics.has(k))
  const noisy = new Set(unreliableMetrics)
  return (
    <table className="w-full text-xs border-collapse">
      <thead>
        <tr className="text-neutral-400 text-left">
          <th className="py-1 pr-2">#</th>
          {cols.map(k => (
            <th key={k} className="py-1 px-2">
              {METRIC_LABEL[k] ?? k}
              {noisy.has(k) && <span className="text-neutral-500" title="Нестабільна метрика — підказки приглушено (EXP-2)"> ~шум</span>}
            </th>
          ))}
          <th className="py-1 px-2">Підказка</th>
        </tr>
      </thead>
      <tbody>
        {reps.map((rep, i) => {
          const top = rep.cues[0]
          return (
            <tr
              key={i}
              className={`cursor-pointer hover:bg-neutral-800 ${selectedIndex === i ? 'bg-neutral-800' : ''}`}
              onClick={() => onSelect(i)}
            >
              <td className="py-1 pr-2 text-neutral-300">{i + 1}</td>
              {cols.map(k => {
                const v = rep.metrics[k]
                const status = metricStatus(v, standard.ranges[k])
                // EXP-4: a noisy metric's value is shown muted/struck — present but not trusted.
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
              <td className="py-1 px-2 text-neutral-300">
                {!rep.placementOk
                  ? '⚠ перевір кут камери (placement)'
                  : top
                    ? top.metricKey
                    : '✓'}
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
