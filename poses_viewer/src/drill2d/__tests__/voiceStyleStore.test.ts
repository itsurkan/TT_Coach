// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import {
  STORAGE_KEY, DEFAULT_ACTIVE_ID,
  allStyles, resolveActiveId, cloneStyle, renameStyle, removeStyle, upsertStyle,
  serializeStyle, importStyle, getActiveStyle, loadUserStyles, saveUserStyles, newStyleId,
  type StoredStyles,
} from '../voiceStyleStore'
import { PRESETS } from '../voiceStyle'

beforeEach(() => localStorage.clear())

describe('resolveActiveId', () => {
  it('keeps a valid id (preset or user)', () => {
    expect(resolveActiveId('preset-playful', [])).toBe('preset-playful')
  })
  it('falls back to the default when the id is unknown', () => {
    expect(resolveActiveId('nope', [])).toBe(DEFAULT_ACTIVE_ID)
    expect(DEFAULT_ACTIVE_ID).toBe('preset-strict')
  })
})

describe('cloneStyle', () => {
  it('produces a non-builtin copy with the given id/name and deep-independent phrases', () => {
    const src = PRESETS.find(p => p.id === 'preset-playful')!
    const clone = cloneStyle(src, 'My Style', 'uid-1')
    expect(clone.id).toBe('uid-1')
    expect(clone.name).toBe('My Style')
    expect(clone.builtin).toBe(false)
    clone.phrases.en.praise.push('mutated')
    expect(src.phrases.en.praise).not.toContain('mutated') // deep copy
  })
})

describe('rename/remove/upsert', () => {
  it('renames only the matching user style', () => {
    const a = cloneStyle(PRESETS[0], 'A', 'a'); const b = cloneStyle(PRESETS[0], 'B', 'b')
    expect(renameStyle([a, b], 'a', 'A2').find(s => s.id === 'a')!.name).toBe('A2')
  })
  it('removes by id', () => {
    const a = cloneStyle(PRESETS[0], 'A', 'a')
    expect(removeStyle([a], 'a')).toEqual([])
  })
  it('upsert replaces in place or appends', () => {
    const a = cloneStyle(PRESETS[0], 'A', 'a')
    const updated = { ...a, name: 'A2' }
    expect(upsertStyle([a], updated).find(s => s.id === 'a')!.name).toBe('A2')
    const b = cloneStyle(PRESETS[0], 'B', 'b')
    expect(upsertStyle([a], b)).toHaveLength(2)
  })
})

describe('serialize/import round-trip', () => {
  it('imports a serialized style as a new non-builtin style with a fresh id', () => {
    const src = cloneStyle(PRESETS[1], 'Exported', 'orig')
    const json = serializeStyle(src)
    const imported = importStyle(json, 'fresh')
    expect(imported.id).toBe('fresh')
    expect(imported.builtin).toBe(false)
    expect(imported.name).toBe('Exported')
    expect(imported.phrases).toEqual(src.phrases)
  })
  it('rejects invalid JSON shapes', () => {
    expect(() => importStyle('{"foo":1}', 'x')).toThrow()
  })
})

describe('persistence', () => {
  it('saves user styles (dropping builtin) and restores active id', () => {
    const user = cloneStyle(PRESETS[0], 'Mine', 'mine')
    const state: StoredStyles = { activeStyleId: 'mine', styles: [user] }
    saveUserStyles(state)
    const loaded = loadUserStyles()
    expect(loaded.activeStyleId).toBe('mine')
    expect(loaded.styles.map(s => s.id)).toEqual(['mine'])
    // raw store never contains presets
    const raw = JSON.parse(localStorage.getItem(STORAGE_KEY)!)
    expect(raw.styles.every((s: { builtin: boolean }) => !s.builtin)).toBe(true)
  })
  it('defaults cleanly when nothing is stored', () => {
    const loaded = loadUserStyles()
    expect(loaded.activeStyleId).toBe(DEFAULT_ACTIVE_ID)
    expect(loaded.styles).toEqual([])
  })
  it('falls back to default active id when the stored active id is invalid', () => {
    saveUserStyles({ activeStyleId: 'ghost', styles: [] })
    expect(loadUserStyles().activeStyleId).toBe(DEFAULT_ACTIVE_ID)
  })
})

describe('getActiveStyle + allStyles', () => {
  it('lists presets first, then user styles', () => {
    const u = cloneStyle(PRESETS[0], 'U', 'u')
    expect(allStyles([u]).map(s => s.id)).toEqual([...PRESETS.map(p => p.id), 'u'])
  })
  it('resolves the active style object', () => {
    const u = cloneStyle(PRESETS[0], 'U', 'u')
    expect(getActiveStyle({ activeStyleId: 'u', styles: [u] }).id).toBe('u')
    expect(getActiveStyle({ activeStyleId: 'bad', styles: [] }).id).toBe(DEFAULT_ACTIVE_ID)
  })
})

describe('newStyleId', () => {
  it('returns distinct non-empty ids', () => {
    expect(newStyleId()).not.toBe(newStyleId())
    expect(newStyleId().length).toBeGreaterThan(0)
  })
})
