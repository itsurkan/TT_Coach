/**
 * Exercises page (#/exercises) — manage TRAINING settings (the first of the three
 * independent settings types; feedback + voice stay global, edited on the Симулятор page).
 * Create a drill from scratch or clone an existing one, pick a body-part focus, choose the
 * reference source, and dial strictness. The selected exercise is persisted as the active
 * one and consumed by StrokesPage's `report` memo (effective standard/settings — the
 * golden-tested grading core is untouched).
 *
 * Built-in presets are read-only (clone to edit), mirroring VoiceStyleEditor.
 */
import { useMemo, useState } from 'react'
import {
  type Exercise, type FocusArea, FOCUS_AREAS, defaultExercise,
  effectiveEnabledMetrics, mergePerPhaseOverrides,
} from '../drill2d/exercise'
import {
  type StoredExercises, allExercises, cloneExercise, removeExercise, upsertExercise, newExerciseId,
  loadExercises, saveExercises,
} from '../drill2d/exerciseStore'
import { PER_PHASE_RANGES } from '../drill2d/referenceStandard'
import type { Phase } from '../drill2d/drillMetrics'
import { Slider } from './controls'

const METRIC_LABEL_UA: Record<string, string> = {
  elbow_angle: 'лікоть',
  shoulder_angle: 'плече',
  knee_bend: 'коліна',
  torso_lean: 'нахил корпусу',
  shoulder_tilt: 'нахил плечей',
  hip_flexion: 'таз',
}
const PHASE_LABEL_UA: Record<Phase, string> = {
  backswing: 'замах',
  contact: 'удар',
  followthrough: 'завершення',
}

export function ExercisesPage({ onClose }: { onClose: () => void }) {
  const [state, setState] = useState<StoredExercises>(() => loadExercises())
  const [selectedId, setSelectedId] = useState(state.activeExerciseId)

  function commit(next: StoredExercises) {
    setState(next)
    saveExercises(next)
  }

  const list = allExercises(state.exercises)
  const selected = list.find(e => e.id === selectedId) ?? list[0]
  const editable = !selected.builtin

  /** Select an exercise AND make it the active one used by the Симулятор. */
  function select(id: string) {
    setSelectedId(id)
    commit({ ...state, activeExerciseId: id })
  }

  function patch(p: Partial<Exercise>) {
    if (!editable) return
    const updated = { ...selected, ...p }
    commit({ ...state, exercises: upsertExercise(state.exercises, updated) })
  }

  function createFromScratch() {
    const id = newExerciseId()
    const ex: Exercise = { ...defaultExercise(), id, name: 'Нова вправа' }
    commit({ activeExerciseId: id, exercises: upsertExercise(state.exercises, ex) })
    setSelectedId(id)
  }

  function cloneSelected() {
    const id = newExerciseId()
    const ex = cloneExercise(selected, `${selected.name} (копія)`, id)
    commit({ activeExerciseId: id, exercises: upsertExercise(state.exercises, ex) })
    setSelectedId(id)
  }

  function deleteSelected() {
    if (!editable) return
    if (!window.confirm(`Видалити «${selected.name}»?`)) return
    const exercises = removeExercise(state.exercises, selected.id)
    const fallback = allExercises(exercises)[0].id
    commit({ activeExerciseId: fallback, exercises })
    setSelectedId(fallback)
  }

  function toggleFocus(area: FocusArea) {
    const has = selected.focusAreas.includes(area)
    patch({ focusAreas: has ? selected.focusAreas.filter(f => f !== area) : [...selected.focusAreas, area] })
  }

  const activeMetrics = effectiveEnabledMetrics(selected)
  const effPhase = useMemo(
    () => mergePerPhaseOverrides(PER_PHASE_RANGES, selected.perPhaseOverrides),
    [selected.perPhaseOverrides],
  )

  function patchPhase(metricKey: string, phase: Phase, edge: 'lo' | 'hi', value: number) {
    const base = effPhase[metricKey as keyof typeof effPhase]?.[phase]
    if (!base) return
    const prev = selected.perPhaseOverrides ?? {}
    const mk = metricKey as keyof typeof prev
    patch({
      perPhaseOverrides: {
        ...prev,
        [mk]: { ...(prev[mk] ?? {}), [phase]: { ...base, [edge]: value } },
      },
    })
  }

  return (
    <div className="min-h-screen bg-neutral-900 text-neutral-100 p-4">
      <header className="flex items-center gap-4 flex-wrap mb-4">
        <button
          onClick={onClose}
          className="px-3 py-1.5 rounded text-sm bg-sky-800 hover:bg-sky-700 transition-colors text-sky-100"
        >
          ← Poses Viewer
        </button>
        <a
          href="#/strokes"
          className="px-3 py-1.5 rounded text-sm bg-neutral-800 hover:bg-neutral-700 transition-colors"
        >
          Симулятор →
        </a>
        <h1 className="text-lg font-semibold">Вправи</h1>
        <span className="text-xs text-neutral-400">Обрана вправа застосовується у Симуляторі ефективності.</span>
      </header>

      <div className="flex flex-col lg:flex-row gap-4 items-start">
        {/* List + actions */}
        <div className="w-full lg:w-72 shrink-0 space-y-2">
          <div className="flex gap-2">
            <button onClick={createFromScratch} className="flex-1 px-2 py-1.5 rounded text-sm bg-emerald-700 hover:bg-emerald-600">
              + Нова
            </button>
            <button onClick={cloneSelected} className="flex-1 px-2 py-1.5 rounded text-sm bg-sky-700 hover:bg-sky-600">
              ⧉ Клонувати
            </button>
          </div>
          <ul className="border border-neutral-700 rounded divide-y divide-neutral-800">
            {list.map(e => (
              <li key={e.id}>
                <button
                  onClick={() => select(e.id)}
                  className={`w-full text-left px-3 py-2 text-sm flex items-center gap-2 ${
                    e.id === selectedId ? 'bg-neutral-700' : 'hover:bg-neutral-800'
                  }`}
                >
                  <span className="flex-1 truncate">{e.name}</span>
                  {e.builtin && <span className="text-[10px] text-neutral-400 border border-neutral-600 rounded px-1">пресет</span>}
                </button>
              </li>
            ))}
          </ul>
        </div>

        {/* Editor */}
        <div className="flex-1 min-w-0 max-w-xl space-y-4">
          {!editable && (
            <div className="rounded p-2 text-xs bg-amber-900/40 border border-amber-700 text-amber-100">
              Це вбудований пресет — лише для перегляду. Натисни «Клонувати», щоб редагувати.
            </div>
          )}

          {/* Simple view */}
          <fieldset className="border border-neutral-700 rounded p-3 space-y-3 text-sm">
            <legend className="px-1">Налаштування вправи</legend>

            <label className="flex items-center gap-2">
              <span className="w-32">Назва:</span>
              <input
                type="text"
                className="flex-1 bg-neutral-800 rounded px-2 py-1 disabled:opacity-50"
                value={selected.name}
                disabled={!editable}
                onChange={e => patch({ name: e.target.value })}
              />
            </label>

            <div className="flex items-start gap-2">
              <span className="w-32 pt-1">Фокус:</span>
              <div className="flex flex-wrap gap-2">
                {FOCUS_AREAS.map(f => {
                  const on = selected.focusAreas.includes(f.id)
                  return (
                    <button
                      key={f.id}
                      disabled={!editable}
                      onClick={() => toggleFocus(f.id)}
                      className={`px-2.5 py-1 rounded-full text-xs border transition-colors disabled:opacity-50 ${
                        on
                          ? 'bg-emerald-700 border-emerald-500 text-emerald-50'
                          : 'bg-neutral-800 border-neutral-600 text-neutral-300 hover:bg-neutral-700'
                      }`}
                    >
                      {f.labelUa}
                    </button>
                  )
                })}
              </div>
            </div>
            <p className="text-xs text-neutral-400 pl-32">
              Активні метрики: {activeMetrics.map(k => METRIC_LABEL_UA[k] ?? k).join(', ') || '—'}
              {selected.focusAreas.length === 0 && ' (фокус не задано → усі)'}
            </p>

            <div className="flex items-start gap-2">
              <span className="w-32 pt-1">Еталон:</span>
              <div className="flex flex-col gap-1">
                <label className="flex items-center gap-2">
                  <input
                    type="radio" name="refsrc" disabled={!editable}
                    checked={selected.referenceSource === 'personal-baseline'}
                    onChange={() => patch({ referenceSource: 'personal-baseline' })}
                  />
                  <span>Мій базовий рівень (калібрувати під мене)</span>
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="radio" name="refsrc" disabled={!editable}
                    checked={selected.referenceSource === 'standard'}
                    onChange={() => patch({ referenceSource: 'standard' })}
                  />
                  <span>Стандарт (еталонна техніка)</span>
                </label>
              </div>
            </div>

            <div className={editable ? '' : 'opacity-50 pointer-events-none'}>
              <Slider
                label="Суворість"
                min={0.5} max={2} step={0.05}
                value={selected.strictness}
                onChange={v => patch({ strictness: v })}
                fmt={v => `×${v.toFixed(2)}`}
                hint="Вище = суворіше (вужча зона допуску), нижче = поблажливіше. Накладається на глобальну «ширину зони»."
              />
            </div>
          </fieldset>

          {/* Advanced */}
          <details className="border border-neutral-700 rounded text-sm">
            <summary className="px-3 py-2 cursor-pointer select-none text-neutral-300">
              Додатково — пофазові цілі (необов'язково)
            </summary>
            <div className="p-3 space-y-2 border-t border-neutral-800">
              <p className="text-xs text-neutral-400">
                Перевизначення ідеальних діапазонів за фазами для метрик руху. Порожні поля = стандартні значення.
                {selected.referenceSource === 'personal-baseline' && ' (При «мій базовий рівень» ці зони рецентруються під твою медіану.)'}
              </p>
              <table className="w-full text-xs">
                <thead className="text-neutral-400">
                  <tr><th className="text-left font-normal">Метрика · фаза</th><th className="font-normal">від°</th><th className="font-normal">до°</th></tr>
                </thead>
                <tbody>
                  {Object.entries(effPhase).flatMap(([metricKey, phases]) =>
                    Object.entries(phases ?? {}).map(([phaseStr, range]) => {
                      const phase = phaseStr as Phase
                      return (
                        <tr key={`${metricKey}-${phase}`} className="border-t border-neutral-800/60">
                          <td className="py-1">{METRIC_LABEL_UA[metricKey] ?? metricKey} · {PHASE_LABEL_UA[phase]}</td>
                          <td className="text-center">
                            <input
                              type="number" disabled={!editable}
                              className="w-16 bg-neutral-800 rounded px-1 py-0.5 text-center disabled:opacity-50"
                              value={Math.round(range!.lo)}
                              onChange={e => patchPhase(metricKey, phase, 'lo', Number(e.target.value))}
                            />
                          </td>
                          <td className="text-center">
                            <input
                              type="number" disabled={!editable}
                              className="w-16 bg-neutral-800 rounded px-1 py-0.5 text-center disabled:opacity-50"
                              value={Math.round(range!.hi)}
                              onChange={e => patchPhase(metricKey, phase, 'hi', Number(e.target.value))}
                            />
                          </td>
                        </tr>
                      )
                    }),
                  )}
                </tbody>
              </table>
              {editable && selected.perPhaseOverrides && (
                <button
                  onClick={() => patch({ perPhaseOverrides: undefined })}
                  className="px-2 py-1 rounded text-xs bg-neutral-800 hover:bg-neutral-700 text-neutral-300"
                >
                  Скинути перевизначення
                </button>
              )}
            </div>
          </details>
        </div>
      </div>
    </div>
  )
}
