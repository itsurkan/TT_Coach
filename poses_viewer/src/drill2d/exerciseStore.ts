/**
 * Exercise persistence. The built-in preset (the original forehand-drive defaults) is a
 * code constant — never stored; only user exercises live in localStorage. Pure operations
 * (clone/rename/remove/upsert/resolve) are split from the thin localStorage read/write so
 * the logic is node-testable. Editing a preset is prevented at the UI layer by cloning
 * first. 1:1 structural mirror of voiceStyleStore.ts.
 */
import { Exercise, defaultExercise, normalizeExercise } from './exercise'

export const STORAGE_KEY = 'poses_viewer_exercises'
export const DEFAULT_ACTIVE_ID = 'preset-forehand-drive'

/**
 * Built-in presets (merged in at load, never persisted). The default reproduces the
 * pre-feature behaviour exactly: all focus areas (⇒ all metrics), textbook reference,
 * strictness 1 — so existing golden tests stay green.
 */
export const PRESETS: Exercise[] = [
  { ...defaultExercise(), id: DEFAULT_ACTIVE_ID, name: 'Накат справа (forehand drive)', builtin: true },
]

/** Persisted shape: active id + user exercises only (presets are merged in at load). */
export interface StoredExercises {
  activeExerciseId: string
  exercises: Exercise[]
}

function deepCopy(ex: Exercise): Exercise {
  return JSON.parse(JSON.stringify(ex)) as Exercise
}

/** Presets first, then user exercises — the selector order. */
export function allExercises(userExercises: Exercise[]): Exercise[] {
  return [...PRESETS, ...userExercises]
}

export function resolveActiveId(activeExerciseId: string, userExercises: Exercise[]): string {
  return allExercises(userExercises).some(e => e.id === activeExerciseId) ? activeExerciseId : DEFAULT_ACTIVE_ID
}

/** Clone an exercise into a fresh, editable (non-builtin) copy — "create from existing". */
export function cloneExercise(source: Exercise, newName: string, id: string): Exercise {
  return { ...deepCopy(source), id, name: newName, builtin: false }
}

export function renameExercise(userExercises: Exercise[], id: string, name: string): Exercise[] {
  return userExercises.map(e => (e.id === id ? { ...e, name } : e))
}

export function removeExercise(userExercises: Exercise[], id: string): Exercise[] {
  return userExercises.filter(e => e.id !== id)
}

export function upsertExercise(userExercises: Exercise[], ex: Exercise): Exercise[] {
  const i = userExercises.findIndex(e => e.id === ex.id)
  if (i === -1) return [...userExercises, ex]
  const copy = [...userExercises]
  copy[i] = ex
  return copy
}

export function getActiveExercise(state: StoredExercises): Exercise {
  const id = resolveActiveId(state.activeExerciseId, state.exercises)
  return allExercises(state.exercises).find(e => e.id === id)
    ?? PRESETS.find(p => p.id === DEFAULT_ACTIVE_ID)
    ?? PRESETS[0]
}

let newExerciseIdCounter = 0
export function newExerciseId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  // Fallback: timestamp+counter (app-only; tests run where crypto exists).
  newExerciseIdCounter += 1
  return `exercise-${newExerciseIdCounter}-${typeof performance !== 'undefined' ? Math.floor(performance.now()) : newExerciseIdCounter}`
}

export function loadExercises(): StoredExercises {
  if (typeof localStorage === 'undefined') return { activeExerciseId: DEFAULT_ACTIVE_ID, exercises: [] }
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { activeExerciseId: DEFAULT_ACTIVE_ID, exercises: [] }
    const parsed = JSON.parse(raw) as StoredExercises
    const exercises = Array.isArray(parsed.exercises)
      ? parsed.exercises.filter(e => !e.builtin).map(normalizeExercise)
      : []
    return { activeExerciseId: resolveActiveId(parsed.activeExerciseId ?? DEFAULT_ACTIVE_ID, exercises), exercises }
  } catch {
    return { activeExerciseId: DEFAULT_ACTIVE_ID, exercises: [] }
  }
}

export function saveExercises(state: StoredExercises): void {
  if (typeof localStorage === 'undefined') return
  const toStore: StoredExercises = {
    activeExerciseId: state.activeExerciseId,
    exercises: state.exercises.filter(e => !e.builtin),
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(toStore))
}
