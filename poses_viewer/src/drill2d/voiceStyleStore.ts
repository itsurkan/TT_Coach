/**
 * Voice-style persistence. Built-in presets are code constants (never stored);
 * only user styles live in localStorage. Pure operations (clone/rename/remove/
 * import/resolve) are split from the thin localStorage read/write so the logic is
 * node-testable. Editing a preset is prevented at the UI layer by cloning first.
 */
import { PRESETS, type VoiceStyle, type Lang, type PhraseSet, VOICE_METRIC_KEYS } from './voiceStyle'

export const STORAGE_KEY = 'poses_viewer_voice_styles'
export const DEFAULT_ACTIVE_ID = 'preset-strict'

const REPRO_DEFAULT = PRESETS.find(p => p.id === DEFAULT_ACTIVE_ID) ?? PRESETS[0]

/** Keep only reproduction fields; backfill any missing cue phrases (e.g. hip_flexion). */
export function normalizeStyle(raw: VoiceStyle): VoiceStyle {
  const phrases = JSON.parse(JSON.stringify(raw.phrases ?? REPRO_DEFAULT.phrases)) as VoiceStyle['phrases']
  for (const lang of ['en', 'uk'] as Lang[]) {
    phrases[lang] ??= JSON.parse(JSON.stringify(REPRO_DEFAULT.phrases[lang]))
    phrases[lang].cues ??= JSON.parse(JSON.stringify(REPRO_DEFAULT.phrases[lang].cues))
    for (const k of VOICE_METRIC_KEYS) {
      phrases[lang].cues[k] ??= { ...REPRO_DEFAULT.phrases[lang].cues[k] }
    }
    // Backfill per-phase pattern phrases (elbow at backswing/followthrough) from
    // the default preset when an older stored style predates them.
    const defaultPhaseCues = REPRO_DEFAULT.phrases[lang].phaseCues
    if (defaultPhaseCues) {
      const pc: NonNullable<PhraseSet['phaseCues']> = (phrases[lang].phaseCues ??= {})
      for (const [metric, defPhases] of Object.entries(defaultPhaseCues)) {
        if (!defPhases) continue
        const mk = metric as keyof typeof defaultPhaseCues
        const target = (pc[mk] ??= {})
        for (const [phase, slot] of Object.entries(defPhases)) {
          if (!slot) continue
          ;(target as Record<string, { up: string; down: string }>)[phase] ??= { ...slot }
        }
      }
    }
    phrases[lang].praise ??= [...REPRO_DEFAULT.phrases[lang].praise]
  }
  return {
    id: raw.id, name: raw.name, builtin: !!raw.builtin,
    lang: raw.lang ?? REPRO_DEFAULT.lang,
    voiceURI: raw.voiceURI ?? null,
    rate: raw.rate ?? REPRO_DEFAULT.rate,
    pitch: raw.pitch ?? REPRO_DEFAULT.pitch,
    volume: raw.volume ?? REPRO_DEFAULT.volume,
    phrases,
  }
}

/** Persisted shape: active id + user styles only (presets are merged in at load). */
export interface StoredStyles {
  activeStyleId: string
  styles: VoiceStyle[]
}

function deepCopy(style: VoiceStyle): VoiceStyle {
  return JSON.parse(JSON.stringify(style)) as VoiceStyle
}

/** Presets first, then user styles — the selector order. */
export function allStyles(userStyles: VoiceStyle[]): VoiceStyle[] {
  return [...PRESETS, ...userStyles]
}

export function resolveActiveId(activeStyleId: string, userStyles: VoiceStyle[]): string {
  return allStyles(userStyles).some(s => s.id === activeStyleId) ? activeStyleId : DEFAULT_ACTIVE_ID
}

export function cloneStyle(source: VoiceStyle, newName: string, id: string): VoiceStyle {
  return { ...deepCopy(source), id, name: newName, builtin: false }
}

export function renameStyle(userStyles: VoiceStyle[], id: string, name: string): VoiceStyle[] {
  return userStyles.map(s => (s.id === id ? { ...s, name } : s))
}

export function removeStyle(userStyles: VoiceStyle[], id: string): VoiceStyle[] {
  return userStyles.filter(s => s.id !== id)
}

export function upsertStyle(userStyles: VoiceStyle[], style: VoiceStyle): VoiceStyle[] {
  const i = userStyles.findIndex(s => s.id === style.id)
  if (i === -1) return [...userStyles, style]
  const copy = [...userStyles]
  copy[i] = style
  return copy
}

export function serializeStyle(style: VoiceStyle): string {
  return JSON.stringify(style, null, 2)
}

/** Parse + validate an exported style; always returns a fresh non-builtin style. */
export function importStyle(json: string, id: string): VoiceStyle {
  const parsed = JSON.parse(json) as Partial<VoiceStyle>
  if (
    parsed === null || typeof parsed !== 'object' ||
    typeof parsed.name !== 'string' ||
    typeof parsed.phrases !== 'object' || parsed.phrases === null ||
    typeof (parsed.phrases as { en?: unknown }).en !== 'object' || (parsed.phrases as { en?: unknown }).en === null ||
    typeof (parsed.phrases as { uk?: unknown }).uk !== 'object' || (parsed.phrases as { uk?: unknown }).uk === null
  ) {
    throw new Error('invalid voice style JSON')
  }
  return normalizeStyle({ ...(parsed as VoiceStyle), id, builtin: false })
}

export function getActiveStyle(state: StoredStyles): VoiceStyle {
  const id = resolveActiveId(state.activeStyleId, state.styles)
  return allStyles(state.styles).find(s => s.id === id) ?? PRESETS.find(p => p.id === DEFAULT_ACTIVE_ID) ?? PRESETS[0]
}

export function newStyleId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  // Fallback: timestamp+counter (app-only; tests run where crypto exists).
  newStyleIdCounter += 1
  return `style-${newStyleIdCounter}-${typeof performance !== 'undefined' ? Math.floor(performance.now()) : newStyleIdCounter}`
}
let newStyleIdCounter = 0

export function loadUserStyles(): StoredStyles {
  if (typeof localStorage === 'undefined') return { activeStyleId: DEFAULT_ACTIVE_ID, styles: [] }
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { activeStyleId: DEFAULT_ACTIVE_ID, styles: [] }
    const parsed = JSON.parse(raw) as StoredStyles
    const styles = Array.isArray(parsed.styles) ? parsed.styles.filter(s => !s.builtin).map(normalizeStyle) : []
    return { activeStyleId: resolveActiveId(parsed.activeStyleId ?? DEFAULT_ACTIVE_ID, styles), styles }
  } catch {
    return { activeStyleId: DEFAULT_ACTIVE_ID, styles: [] }
  }
}

export function saveUserStyles(state: StoredStyles): void {
  if (typeof localStorage === 'undefined') return
  const toStore: StoredStyles = { activeStyleId: state.activeStyleId, styles: state.styles.filter(s => !s.builtin) }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(toStore))
}
