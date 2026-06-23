/**
 * Voice-style data model + the three immutable built-in presets. A VoiceStyle is a
 * plain editable object holding every knob the feedback research identified:
 * bandwidth, cadence, praise, skip-stale, TTS voice, and a per-language phrase
 * catalog. The user clones a preset into a named, editable style. Trust rule: every
 * phrase is a plain imperative — the voice layer never injects a degree number.
 */

// Type-only: Phase keys the per-phase phrase map is indexed by (pattern metrics).
import type { Phase } from './drillMetrics'

export type MetricKey = 'elbow_angle' | 'shoulder_angle' | 'knee_bend' | 'torso_lean' | 'shoulder_tilt' | 'hip_flexion'

/** The coachable metrics (drive both the table and the voice), fixed order for tie stability. */
export const VOICE_METRIC_KEYS: MetricKey[] = [
  'elbow_angle', 'shoulder_angle', 'knee_bend', 'torso_lean', 'shoulder_tilt', 'hip_flexion',
]

export type Lang = 'en' | 'uk'

export interface PhraseSet {
  /** up = measured value ABOVE the (widened) band; down = BELOW it. */
  cues: Record<MetricKey, { up: string; down: string }>
  /**
   * Per-phase phrases for PATTERN metrics (currently elbow), graded at a stroke
   * phase rather than the contact instant. When a cue carries a `phase` and a
   * phrase exists here, it overrides `cues[metric]`. up/down semantics match `cues`.
   */
  phaseCues?: Partial<Record<MetricKey, Partial<Record<Phase, { up: string; down: string }>>>>
  /** Rotated praise pool; specific, never bare "good job". */
  praise: string[]
}

export interface VoiceStyle {
  id: string
  name: string
  builtin: boolean

  // Voice (TTS)
  lang: Lang
  voiceURI: string | null
  rate: number
  pitch: number
  volume: number

  // Phrases (word choice)
  phrases: Record<Lang, PhraseSet>
}

/** The TTS-only subset the playback layer needs. */
export interface VoiceProfile {
  lang: Lang
  voiceURI: string | null
  rate: number
  pitch: number
  volume: number
}

export function voiceProfileOf(s: VoiceStyle): VoiceProfile {
  return { lang: s.lang, voiceURI: s.voiceURI, rate: s.rate, pitch: s.pitch, volume: s.volume }
}

// ── Seed phrases (editable after cloning) ───────────────────────────────────
// EN seeded from the practitioner cue vocabulary; UA equivalents authored here and
// refined by the user in the editor. Efficient = terse keyword; Playful = warm.

const PLAYFUL_EN: PhraseSet = {
  cues: {
    elbow_angle: { up: 'give the elbow a little bend', down: 'reach through it' },
    shoulder_angle: { up: 'drop the shoulder a touch', down: 'open the shoulder a bit more' },
    knee_bend: { up: 'sit into it, legs on', down: 'ease up, stand a little taller' },
    torso_lean: { up: 'stand a bit taller', down: 'lean into the ball' },
    shoulder_tilt: { up: 'level the shoulders', down: 'level the shoulders' },
    hip_flexion: { up: 'ease the hips up a touch', down: 'sink into the hips' },
  },
  phaseCues: {
    elbow_angle: {
      backswing:     { up: "don't lock the elbow on the backswing", down: 'open the elbow a little on the backswing' },
      followthrough: { up: 'finish the elbow through', down: "don't over-fold the finish" },
    },
  },
  praise: ["that's the shape!", 'yes — that follow-through', 'clean — do that again', 'nice, really solid', 'love it, keep going'],
}
const PLAYFUL_UK: PhraseSet = {
  cues: {
    elbow_angle: { up: 'трохи зігни лікоть', down: 'тягнися крізь мʼяч' },
    shoulder_angle: { up: 'ледь опусти плече', down: 'трохи відкрий плече' },
    knee_bend: { up: 'присядь, працюй ногами', down: 'трохи випрямись' },
    torso_lean: { up: 'тримайся трохи рівніше', down: 'нахились до мʼяча' },
    shoulder_tilt: { up: 'вирівняй плечі', down: 'вирівняй плечі' },
    hip_flexion: { up: 'трохи вище стегнами', down: 'присядь у стегнах' },
  },
  phaseCues: {
    elbow_angle: {
      backswing:     { up: 'не блокуй лікоть на замаху', down: 'трохи розправ лікоть на замаху' },
      followthrough: { up: 'доводь лікоть до кінця', down: 'не затискай на завершенні' },
    },
  },
  praise: ['оце форма!', 'так — оце завершення', 'чисто — ще раз так', 'гарно, дуже впевнено', 'клас, продовжуй'],
}

const STRICT_EN: PhraseSet = {
  cues: {
    elbow_angle: { up: 'bend the elbow', down: 'extend more' },
    shoulder_angle: { up: 'drop the shoulder', down: 'open the shoulder' },
    knee_bend: { up: 'bend the knees', down: 'stand taller' },
    torso_lean: { up: 'stand taller', down: 'lean in' },
    shoulder_tilt: { up: 'level the shoulders', down: 'level the shoulders' },
    hip_flexion: { up: 'stand tall', down: 'hinge forward' },
  },
  phaseCues: {
    elbow_angle: {
      backswing:     { up: "don't lock the elbow on the backswing", down: 'open the elbow on the backswing' },
      followthrough: { up: 'finish the elbow through', down: "don't over-fold the finish" },
    },
  },
  praise: ["that's the shape", 'clean — repeat that', 'correct'],
}
const STRICT_UK: PhraseSet = {
  cues: {
    elbow_angle: { up: 'зігни лікоть', down: 'більше випрями руку' },
    shoulder_angle: { up: 'опусти плече', down: 'відкрий плече' },
    knee_bend: { up: 'зігни коліна', down: 'стань вище' },
    torso_lean: { up: 'тримайся рівніше', down: 'нахились уперед' },
    shoulder_tilt: { up: 'вирівняй плечі', down: 'вирівняй плечі' },
    hip_flexion: { up: 'вище', down: 'нахились у стегнах' },
  },
  phaseCues: {
    elbow_angle: {
      backswing:     { up: 'не блокуй лікоть на замаху', down: 'розправ лікоть на замаху' },
      followthrough: { up: 'доводь лікоть', down: 'не затискай на завершенні' },
    },
  },
  praise: ['оце форма', 'чисто — повтори', 'правильно'],
}

const EFFICIENT_EN: PhraseSet = {
  cues: {
    elbow_angle: { up: 'bend elbow', down: 'extend arm' },
    shoulder_angle: { up: 'drop shoulder', down: 'open shoulder' },
    knee_bend: { up: 'bend knees', down: 'stand taller' },
    torso_lean: { up: 'taller', down: 'lean in' },
    shoulder_tilt: { up: 'level shoulders', down: 'level shoulders' },
    hip_flexion: { up: 'hips up', down: 'hinge' },
  },
  phaseCues: {
    elbow_angle: {
      backswing:     { up: "don't lock elbow", down: 'open elbow' },
      followthrough: { up: 'finish elbow', down: "don't over-fold" },
    },
  },
  praise: ['clean', 'yes', 'good'],
}
const EFFICIENT_UK: PhraseSet = {
  cues: {
    elbow_angle: { up: 'зігни лікоть', down: 'випрями руку' },
    shoulder_angle: { up: 'опусти плече', down: 'відкрий плече' },
    knee_bend: { up: 'зігни коліна', down: 'вище' },
    torso_lean: { up: 'вище', down: 'нахились' },
    shoulder_tilt: { up: 'рівніше плечі', down: 'рівніше плечі' },
    hip_flexion: { up: 'вище', down: 'нахились' },
  },
  phaseCues: {
    elbow_angle: {
      backswing:     { up: 'не блокуй лікоть', down: 'розправ лікоть' },
      followthrough: { up: 'доводь лікоть', down: 'не затискай' },
    },
  },
  praise: ['чисто', 'так', 'добре'],
}

export const PRESETS: VoiceStyle[] = [
  {
    id: 'preset-playful', name: 'Playful', builtin: true,
    lang: 'en', voiceURI: null, rate: 0.95, pitch: 1.05, volume: 1.0,
    phrases: { en: PLAYFUL_EN, uk: PLAYFUL_UK },
  },
  {
    id: 'preset-strict', name: 'Strict', builtin: true,
    lang: 'en', voiceURI: null, rate: 1.0, pitch: 0.95, volume: 1.0,
    phrases: { en: STRICT_EN, uk: STRICT_UK },
  },
  {
    id: 'preset-efficient', name: 'Efficient', builtin: true,
    lang: 'en', voiceURI: null, rate: 1.15, pitch: 1.0, volume: 1.0,
    phrases: { en: EFFICIENT_EN, uk: EFFICIENT_UK },
  },
]
