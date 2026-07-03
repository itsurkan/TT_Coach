/**
 * Deterministic clip lookup for the voice layer. The clip key is derived purely
 * from (lang, normalized text), so an edited phrase yields no matching key and the
 * playback layer falls back to live TTS until the clip is regenerated. The djb2
 * hash here MUST stay byte-identical to scripts/generateVoiceClips.ts.
 */

// Local structural alias (kept dependency-free on purpose; canonical Lang lives in voiceStyle.ts).
type Lang = 'en' | 'uk'

export interface ClipEntry {
  file: string
  durationMs: number
  text: string
  lang: Lang
}

export interface ClipManifest {
  styleId: string
  clips: Record<string, ClipEntry>
}

/** Lowercase, trim, and collapse internal whitespace — the canonical form keys hash over. */
export function normalizeText(text: string): string {
  return text.trim().toLowerCase().replace(/\s+/g, ' ')
}

/** djb2 (xor variant), kept unsigned per step, base36. Mirrored in generateVoiceClips.ts. */
export function hashText(text: string): string {
  let h = 5381
  for (let i = 0; i < text.length; i++) {
    h = ((h * 33) ^ text.charCodeAt(i)) >>> 0
  }
  return h.toString(36)
}

export function clipKey(lang: Lang, text: string): string {
  return `${lang}__${hashText(normalizeText(text))}`
}

/**
 * The clip for (lang, text) if present AND fresh. Freshness = the stored text still
 * normalizes to the same string we are looking up (guards against hash collisions
 * and any future key-scheme drift).
 */
export function lookupClip(manifest: ClipManifest | null, lang: Lang, text: string): ClipEntry | null {
  if (!manifest) return null
  const entry = manifest.clips[clipKey(lang, text)]
  if (!entry) return null
  if (normalizeText(entry.text) !== normalizeText(text)) return null
  return entry
}

/** Best-effort fetch of a style's manifest; missing/erroring → null (all-live playback). */
export async function loadManifest(styleId: string): Promise<ClipManifest | null> {
  try {
    const res = await fetch(`/voice/${encodeURIComponent(styleId)}/manifest.json`)
    if (!res.ok) return null
    return (await res.json()) as ClipManifest
  } catch {
    return null
  }
}
