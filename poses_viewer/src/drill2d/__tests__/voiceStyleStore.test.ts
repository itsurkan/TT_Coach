// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import {
  STORAGE_KEY, DEFAULT_ACTIVE_ID,
  allStyles, resolveActiveId, cloneStyle, renameStyle, removeStyle, upsertStyle,
  serializeStyle, importStyle, getActiveStyle, loadUserStyles, saveUserStyles, newStyleId,
  normalizeStyle,
  type StoredStyles,
} from '../voiceStyleStore'
import { PRESETS, type VoiceStyle } from '../voiceStyle'

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
  it('rejects a style whose phrases lack en/uk language sets', () => {
    expect(() => importStyle('{"name":"x","phrases":{},"bandWidthMult":1}', 'id')).toThrow()
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

describe('normalizeStyle migration', () => {
  it('strips legacy policy fields', () => {
    const legacy = { ...PRESETS[0], bandWidthMult: 1.4, correctiveMinGapMs: 5000 } as unknown as VoiceStyle
    const n = normalizeStyle(legacy) as unknown as Record<string, unknown>
    expect(n.bandWidthMult).toBeUndefined()
    expect(n.correctiveMinGapMs).toBeUndefined()
    expect(n.rate).toBeDefined()
  })
  it('backfills missing hip_flexion phrases from the default preset', () => {
    const noHip = JSON.parse(JSON.stringify(PRESETS[0]))
    delete noHip.phrases.en.cues.hip_flexion
    delete noHip.phrases.uk.cues.hip_flexion
    const n = normalizeStyle(noHip)
    expect(n.phrases.en.cues.hip_flexion.up.length).toBeGreaterThan(0)
    expect(n.phrases.uk.cues.hip_flexion.down.length).toBeGreaterThan(0)
  })
  it('backfills missing per-phase elbow phrases (phaseCues) from the default preset', () => {
    const noPhase = JSON.parse(JSON.stringify(PRESETS[0]))
    delete noPhase.phrases.en.phaseCues
    delete noPhase.phrases.uk.phaseCues
    const n = normalizeStyle(noPhase)
    expect(n.phrases.en.phaseCues!.elbow_angle!.backswing!.up.length).toBeGreaterThan(0)
    expect(n.phrases.en.phaseCues!.elbow_angle!.followthrough!.down.length).toBeGreaterThan(0)
    expect(n.phrases.uk.phaseCues!.elbow_angle!.followthrough!.up.length).toBeGreaterThan(0)
  })
})
