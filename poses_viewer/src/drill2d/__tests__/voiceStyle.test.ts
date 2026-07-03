import { describe, expect, it } from 'vitest'
import { PRESETS, VOICE_METRIC_KEYS, voiceProfileOf, type Lang } from '../voiceStyle'

const LANGS: Lang[] = ['en', 'uk']

describe('PRESETS', () => {
  it('ships exactly the three built-in presets with stable ids', () => {
    expect(PRESETS.map(p => p.id)).toEqual(['preset-playful', 'preset-strict', 'preset-efficient'])
  })
  it('marks every preset builtin', () => {
    expect(PRESETS.every(p => p.builtin)).toBe(true)
  })
  it('covers every voiced metric (up+down) and a non-empty praise pool in EN and UK', () => {
    for (const p of PRESETS) {
      for (const lang of LANGS) {
        const set = p.phrases[lang]
        expect(set, `${p.id}/${lang}`).toBeDefined()
        expect(set.praise.length).toBeGreaterThan(0)
        for (const key of VOICE_METRIC_KEYS) {
          expect(set.cues[key].up.length, `${p.id}/${lang}/${key}.up`).toBeGreaterThan(0)
          expect(set.cues[key].down.length, `${p.id}/${lang}/${key}.down`).toBeGreaterThan(0)
        }
      }
    }
  })
  it('never embeds a digit in any phrase (trust rule: no spoken numbers)', () => {
    for (const p of PRESETS) {
      for (const lang of LANGS) {
        const all = [
          ...VOICE_METRIC_KEYS.flatMap(k => [p.phrases[lang].cues[k].up, p.phrases[lang].cues[k].down]),
          ...p.phrases[lang].praise,
        ]
        for (const s of all) expect(/\d/.test(s), `${p.id}/${lang}: "${s}"`).toBe(false)
      }
    }
  })
})

describe('hip_flexion is a coachable, voiced metric', () => {
  it('is in VOICE_METRIC_KEYS', () => {
    expect(VOICE_METRIC_KEYS).toContain('hip_flexion')
  })
  it('every preset has EN + UK hip_flexion phrases', () => {
    for (const p of PRESETS) {
      expect(p.phrases.en.cues.hip_flexion.up.length).toBeGreaterThan(0)
      expect(p.phrases.en.cues.hip_flexion.down.length).toBeGreaterThan(0)
      expect(p.phrases.uk.cues.hip_flexion.up.length).toBeGreaterThan(0)
      expect(p.phrases.uk.cues.hip_flexion.down.length).toBeGreaterThan(0)
    }
  })
})

describe('voiceProfileOf', () => {
  it('projects the TTS subset of a style', () => {
    const strict = PRESETS.find(p => p.id === 'preset-strict')!
    expect(voiceProfileOf(strict)).toEqual({
      lang: strict.lang, voiceURI: strict.voiceURI, rate: strict.rate, pitch: strict.pitch, volume: strict.volume,
    })
  })
})
