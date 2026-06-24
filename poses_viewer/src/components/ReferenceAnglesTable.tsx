import { ReferenceStandard, ReferenceRange, perPhaseRange } from '../drill2d/referenceStandard'
import { ALL_KEYS, METRIC_PHASES, Phase } from '../drill2d/drillMetrics'

/** Ukrainian metric labels (mirror DrillResultsTable chrome). */
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

/** Evidence-tag chrome: measured ranges read stronger than coach-opinion. */
const EVIDENCE_LABEL: Record<ReferenceRange['evidence'], string> = {
  measured: 'виміряно',
  coach_opinion: 'оцінка тренера',
}
const EVIDENCE_CLASS: Record<ReferenceRange['evidence'], string> = {
  measured: 'text-emerald-400',
  coach_opinion: 'text-amber-400',
}

const phasesOf = (k: string): Phase[] | undefined =>
  METRIC_PHASES[k as keyof typeof METRIC_PHASES] as Phase[] | undefined

interface Row {
  metricKey: string
  /** null = single-instant metric (no per-phase breakdown). */
  phase: Phase | null
  range: ReferenceRange
}

/** Flatten the standard + per-phase ranges into one row per (metric, phase). */
function buildRows(standard: ReferenceStandard): Row[] {
  const rows: Row[] = []
  for (const k of ALL_KEYS) {
    const phases = phasesOf(k)
    if (phases !== undefined && phases.length > 0) {
      for (const phase of phases) {
        const range = perPhaseRange(k, phase)
        if (range) rows.push({ metricKey: k, phase, range })
      }
    } else {
      // Single-instant metric (shoulder_tilt) — graded against standard.ranges.
      const range = standard.ranges[k]
      if (range) rows.push({ metricKey: k, phase: null, range })
    }
  }
  return rows
}

interface Props {
  standard: ReferenceStandard
}

/**
 * Reference-angle table for the selected drill: the ideal in-plane bands the
 * tool grades each rep against, per metric and per stroke phase. Read-only
 * reference chrome — mirrors the bands in referenceStandard.ts / PER_PHASE_RANGES.
 */
export function ReferenceAnglesTable({ standard }: Props) {
  const rows = buildRows(standard)

  return (
    <div className="space-y-2">
      <h2 className="text-sm font-semibold">Еталонні кути ({standard.drillType})</h2>
      <p className="text-xs text-neutral-400">
        Орієнтовні («ідеальні») діапазони, з якими порівнюється кожен повтор. Конвенція внутрішнього
        кута: 180° = повністю випрямлено. Більшість значень ПОПЕРЕДНІ — деталі в наведенні на джерело.
      </p>
      <table className="w-full text-xs border-collapse">
        <thead>
          <tr className="text-neutral-400 text-left">
            <th className="py-1 pr-2">Метрика</th>
            <th className="py-1 px-2">Фаза</th>
            <th className="py-1 px-2">Ідеальний діапазон</th>
            <th className="py-1 px-2">Достовірність</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(({ metricKey, phase, range }) => (
            <tr key={`${metricKey}-${phase ?? 'single'}`} className="border-t border-neutral-800">
              <td className="py-1 pr-2 text-neutral-200">{METRIC_LABEL[metricKey] ?? metricKey}</td>
              <td className="py-1 px-2 text-neutral-400">{phase ? PHASE_LABEL[phase] : 'удар'}</td>
              <td className="py-1 px-2 text-neutral-100">{range.lo}–{range.hi}°</td>
              <td className={`py-1 px-2 ${EVIDENCE_CLASS[range.evidence]}`} title={range.source}>
                {EVIDENCE_LABEL[range.evidence]}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
