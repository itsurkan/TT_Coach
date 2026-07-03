import { describe, expect, it } from 'vitest'
import { clipKey, normalizeText, hashText, lookupClip, type ClipManifest } from '../voiceClips'

describe('normalizeText', () => {
  it('lowercases, trims, and collapses internal whitespace', () => {
    expect(normalizeText('  Зігни   Лікоть ')).toBe('зігни лікоть')
    expect(normalizeText('Bend   Elbow')).toBe('bend elbow')
  })
})

describe('hashText', () => {
  it('is deterministic and differs across inputs', () => {
    expect(hashText('bend elbow')).toBe(hashText('bend elbow'))
    expect(hashText('bend elbow')).not.toBe(hashText('extend arm'))
  })
})

describe('clipKey', () => {
  it('is stable under normalization (whitespace/case)', () => {
    expect(clipKey('uk', 'Зігни  лікоть ')).toBe(clipKey('uk', 'зігни лікоть'))
  })
  it('is language-scoped', () => {
    expect(clipKey('en', 'level shoulders')).not.toBe(clipKey('uk', 'level shoulders'))
  })
})

describe('lookupClip', () => {
  const manifest: ClipManifest = {
    styleId: 'preset-strict',
    clips: {
      [clipKey('en', 'bend elbow')]: { file: 'a.mp3', durationMs: 600, text: 'bend elbow', lang: 'en' },
    },
  }
  it('returns the entry for a fresh (matching-text) clip', () => {
    expect(lookupClip(manifest, 'en', 'bend elbow')?.durationMs).toBe(600)
  })
  it('returns null for a missing key', () => {
    expect(lookupClip(manifest, 'en', 'extend arm')).toBeNull()
  })
  it('returns null when manifest is null', () => {
    expect(lookupClip(null, 'en', 'bend elbow')).toBeNull()
  })
  it('returns null for a stale entry whose stored text no longer matches the key collision', () => {
    const stale: ClipManifest = {
      styleId: 'x',
      clips: { [clipKey('en', 'bend elbow')]: { file: 'a.mp3', durationMs: 600, text: 'different text', lang: 'en' } },
    }
    expect(lookupClip(stale, 'en', 'bend elbow')).toBeNull()
  })
})
