// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import {
  STORAGE_KEY, DEFAULT_ACTIVE_ID, PRESETS,
  allExercises, resolveActiveId, cloneExercise, renameExercise, removeExercise, upsertExercise,
  getActiveExercise, loadExercises, saveExercises, newExerciseId,
  type StoredExercises,
} from '../exerciseStore'

beforeEach(() => localStorage.clear())

describe('presets', () => {
  it('ships a single built-in forehand-drive default', () => {
    expect(PRESETS.map(p => p.id)).toEqual([DEFAULT_ACTIVE_ID])
    expect(PRESETS[0].builtin).toBe(true)
    expect(PRESETS[0].focusAreas.length).toBe(5)
  })
})

describe('resolveActiveId', () => {
  it('keeps a valid id, falls back to default otherwise', () => {
    expect(resolveActiveId(DEFAULT_ACTIVE_ID, [])).toBe(DEFAULT_ACTIVE_ID)
    expect(resolveActiveId('nope', [])).toBe(DEFAULT_ACTIVE_ID)
  })
})

describe('cloneExercise', () => {
  it('produces a non-builtin, deep-independent copy with the given id/name', () => {
    const clone = cloneExercise(PRESETS[0], 'Legs day', 'uid-1')
    expect(clone.id).toBe('uid-1')
    expect(clone.name).toBe('Legs day')
    expect(clone.builtin).toBe(false)
    const sourceLen = PRESETS[0].focusAreas.length
    clone.focusAreas.push('arm') // mutate the clone's array
    expect(PRESETS[0].focusAreas.length).toBe(sourceLen) // deep copy, source untouched
    expect(PRESETS[0].focusAreas).not.toBe(clone.focusAreas)
  })
})

describe('rename / remove / upsert', () => {
  it('renames only the matching user exercise', () => {
    const a = cloneExercise(PRESETS[0], 'A', 'a')
    const b = cloneExercise(PRESETS[0], 'B', 'b')
    expect(renameExercise([a, b], 'a', 'A2').find(e => e.id === 'a')!.name).toBe('A2')
  })
  it('removes by id', () => {
    const a = cloneExercise(PRESETS[0], 'A', 'a')
    expect(removeExercise([a], 'a')).toEqual([])
  })
  it('upsert replaces in place or appends', () => {
    const a = cloneExercise(PRESETS[0], 'A', 'a')
    expect(upsertExercise([a], { ...a, name: 'A2' }).find(e => e.id === 'a')!.name).toBe('A2')
    expect(upsertExercise([a], cloneExercise(PRESETS[0], 'B', 'b'))).toHaveLength(2)
  })
})

describe('persistence', () => {
  it('saves user exercises (dropping builtin) and restores active id', () => {
    const user = cloneExercise(PRESETS[0], 'Mine', 'mine')
    const state: StoredExercises = { activeExerciseId: 'mine', exercises: [user] }
    saveExercises(state)
    const loaded = loadExercises()
    expect(loaded.activeExerciseId).toBe('mine')
    expect(loaded.exercises.map(e => e.id)).toEqual(['mine'])
    const raw = JSON.parse(localStorage.getItem(STORAGE_KEY)!)
    expect(raw.exercises.every((e: { builtin?: boolean }) => !e.builtin)).toBe(true)
  })
  it('defaults cleanly when nothing is stored', () => {
    const loaded = loadExercises()
    expect(loaded.activeExerciseId).toBe(DEFAULT_ACTIVE_ID)
    expect(loaded.exercises).toEqual([])
  })
  it('falls back to default active id when the stored active id is invalid', () => {
    saveExercises({ activeExerciseId: 'ghost', exercises: [] })
    expect(loadExercises().activeExerciseId).toBe(DEFAULT_ACTIVE_ID)
  })
  it('normalizes a stored exercise missing newer fields', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      activeExerciseId: 'old', exercises: [{ id: 'old', name: 'Old' }],
    }))
    const loaded = loadExercises()
    const old = loaded.exercises.find(e => e.id === 'old')!
    expect(old.strictness).toBe(1)
    expect(old.referenceSource).toBe('standard')
    expect(old.focusAreas.length).toBe(5)
  })
})

describe('allExercises + getActiveExercise', () => {
  it('lists presets first, then user exercises', () => {
    const u = cloneExercise(PRESETS[0], 'U', 'u')
    expect(allExercises([u]).map(e => e.id)).toEqual([...PRESETS.map(p => p.id), 'u'])
  })
  it('resolves the active exercise object', () => {
    const u = cloneExercise(PRESETS[0], 'U', 'u')
    expect(getActiveExercise({ activeExerciseId: 'u', exercises: [u] }).id).toBe('u')
    expect(getActiveExercise({ activeExerciseId: 'bad', exercises: [] }).id).toBe(DEFAULT_ACTIVE_ID)
  })
})

describe('newExerciseId', () => {
  it('returns distinct non-empty ids', () => {
    expect(newExerciseId()).not.toBe(newExerciseId())
    expect(newExerciseId().length).toBeGreaterThan(0)
  })
})
