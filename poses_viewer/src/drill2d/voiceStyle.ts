/**
 * Voice-style data model + the three immutable built-in presets. A VoiceStyle is a
 * plain editable object holding every knob the feedback research identified:
 * bandwidth, cadence, praise, skip-stale, TTS voice, and a per-language phrase
 * catalog. The user clones a preset into a named, editable style. Trust rule: every
 * phrase is a plain imperative — the voice layer never injects a degree number.
 */

// Type-only: Phase keys the per-phase phrase map is indexed by (pattern metrics).
import type { Phase } from './drillMetrics'

export type MetricKey =
  | 'elbow_angle' | 'shoulder_angle' | 'knee_bend' | 'torso_lean' | 'shoulder_tilt' | 'hip_flexion'
  | 'follow_through_angle_2d' | 'stroke_speed' | 'coil_ratio'

/**
 * The coachable metrics (drive both the table and the voice), fixed order for tie stability.
 * Deliberately NOT the full [MetricKey] union: `follow_through_angle_2d`/`stroke_speed`/
 * `coil_ratio` are new derived-metric keys added to the type (and given `cues` phrases below,
 * for the Kotlin `VoicePresetCatalog` port) but are not yet wired into this viewer's live
 * grading pipeline (decideRepCues/exercise focus areas) — that wiring is a separate task.
 */
export const VOICE_METRIC_KEYS: MetricKey[] = [
  'elbow_angle', 'shoulder_angle', 'knee_bend', 'torso_lean', 'shoulder_tilt', 'hip_flexion',
]

export type Lang = 'en' | 'uk'

export interface PhraseSet {
  /** up = measured value ABOVE the (widened) band; down = BELOW it. */
  cues: Record<MetricKey, { up: string; down: string }>
  /**
   * Per-phase phrases for PATTERN metrics (elbow, shoulder_angle, knee_bend, hip_flexion,
   * torso_lean), graded at a stroke phase rather than the contact instant. When a cue
   * carries a `phase` and a phrase exists here, it overrides `cues[metric]`. up/down
   * semantics match `cues`.
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
    follow_through_angle_2d: { up: 'shorten the follow-through a touch', down: 'finish a little higher' },
    stroke_speed: { up: 'ease off the speed a touch', down: 'swing with a bit more pace' },
    coil_ratio: { up: 'ease off the rotation a touch', down: 'rotate through the ball a bit more' },
  },
  phaseCues: {
    elbow_angle: {
      backswing:     { up: "don't lock the elbow on the backswing", down: 'open the elbow a little on the backswing' },
      followthrough: { up: 'finish the elbow through', down: "don't over-fold the finish" },
    },
    shoulder_angle: {
      backswing:     { up: 'keep the arm low on the take-back', down: 'let the arm hang back a bit' },
      followthrough: { up: "don't over-swing the finish", down: 'sweep up to finish' },
    },
    knee_bend: {
      backswing: { up: 'sit a little deeper as you load', down: "don't over-sink the load" },
      contact:   { up: 'stay down through contact', down: "don't over-bend at contact" },
    },
    hip_flexion: {
      backswing: { up: 'hinge into the hips a touch more', down: 'ease the hips up as you load' },
      contact:   { up: 'keep the hip hinge at contact', down: 'ease the hips up at contact' },
    },
    torso_lean: {
      backswing: { up: 'stay a bit taller on the take-back', down: 'lean in a little as you load' },
      contact:   { up: 'stand a touch taller at contact', down: 'lean into the ball at contact' },
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
    follow_through_angle_2d: { up: 'трохи коротший фініш', down: 'завершуй трохи вище' },
    stroke_speed: { up: 'трохи скинь швидкість', down: 'додай швидкості удару' },
    coil_ratio: { up: 'трохи менше крутись', down: 'розкручуйся у мʼяч трохи більше' },
  },
  phaseCues: {
    elbow_angle: {
      backswing:     { up: 'не блокуй лікоть на замаху', down: 'трохи розправ лікоть на замаху' },
      followthrough: { up: 'доводь лікоть до кінця', down: 'не затискай на завершенні' },
    },
    shoulder_angle: {
      backswing:     { up: 'тримай руку нижче на замаху', down: 'трохи опусти руку назад' },
      followthrough: { up: 'не перемахуй на завершенні', down: 'доводь руку вгору' },
    },
    knee_bend: {
      backswing: { up: 'присядь трохи глибше на замаху', down: 'не присідай надто на замаху' },
      contact:   { up: 'тримай присід в ударі', down: 'не перегинай коліна в ударі' },
    },
    hip_flexion: {
      backswing: { up: 'трохи більше нахилу в стегнах', down: 'трохи розігни стегна на замаху' },
      contact:   { up: 'тримай нахил стегон в ударі', down: 'розігни стегна в ударі' },
    },
    torso_lean: {
      backswing: { up: 'тримайся рівніше на замаху', down: 'трохи нахились на замаху' },
      contact:   { up: 'тримайся рівніше в ударі', down: 'нахились до мʼяча в ударі' },
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
    follow_through_angle_2d: { up: 'shorten the follow-through', down: 'finish higher' },
    stroke_speed: { up: 'ease off the speed', down: 'swing faster' },
    coil_ratio: { up: 'reduce the rotation', down: 'rotate more through the ball' },
  },
  phaseCues: {
    elbow_angle: {
      backswing:     { up: "don't lock the elbow on the backswing", down: 'open the elbow on the backswing' },
      followthrough: { up: 'finish the elbow through', down: "don't over-fold the finish" },
    },
    shoulder_angle: {
      backswing:     { up: 'arm low on the backswing', down: 'arm back on the backswing' },
      followthrough: { up: "don't over-swing", down: 'sweep up to finish' },
    },
    knee_bend: {
      backswing: { up: 'sit deeper to load', down: "don't over-sink" },
      contact:   { up: 'stay down at contact', down: "don't over-bend" },
    },
    hip_flexion: {
      backswing: { up: 'hinge forward to load', down: 'hips up on the load' },
      contact:   { up: 'keep the hinge at contact', down: 'hips up at contact' },
    },
    torso_lean: {
      backswing: { up: 'taller on the backswing', down: 'lean in to load' },
      contact:   { up: 'taller at contact', down: 'lean in at contact' },
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
    follow_through_angle_2d: { up: 'коротший завершальний рух', down: 'завершуй вище' },
    stroke_speed: { up: 'зменш швидкість', down: 'бий швидше' },
    coil_ratio: { up: 'менше обертання тулуба', down: 'більше розкручуйся у мʼяч' },
  },
  phaseCues: {
    elbow_angle: {
      backswing:     { up: 'не блокуй лікоть на замаху', down: 'розправ лікоть на замаху' },
      followthrough: { up: 'доводь лікоть', down: 'не затискай на завершенні' },
    },
    shoulder_angle: {
      backswing:     { up: 'рука нижче на замаху', down: 'рука назад на замаху' },
      followthrough: { up: 'не перемахуй', down: 'доводь руку вгору' },
    },
    knee_bend: {
      backswing: { up: 'присядь глибше', down: 'не присідай надто' },
      contact:   { up: 'тримай присід в ударі', down: 'не перегинай коліна' },
    },
    hip_flexion: {
      backswing: { up: 'нахились у стегнах', down: 'стегна вище' },
      contact:   { up: 'тримай нахил в ударі', down: 'стегна вище в ударі' },
    },
    torso_lean: {
      backswing: { up: 'рівніше на замаху', down: 'нахились на замаху' },
      contact:   { up: 'рівніше в ударі', down: 'нахились в ударі' },
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
    follow_through_angle_2d: { up: 'shorten follow-through', down: 'finish higher' },
    stroke_speed: { up: 'slow down', down: 'swing faster' },
    coil_ratio: { up: 'less rotation', down: 'rotate more' },
  },
  phaseCues: {
    elbow_angle: {
      backswing:     { up: "don't lock elbow", down: 'open elbow' },
      followthrough: { up: 'finish elbow', down: "don't over-fold" },
    },
    shoulder_angle: {
      backswing:     { up: 'arm low', down: 'arm back' },
      followthrough: { up: "don't over-swing", down: 'sweep up' },
    },
    knee_bend: {
      backswing: { up: 'sit deeper', down: "don't over-sink" },
      contact:   { up: 'stay down', down: "don't over-bend" },
    },
    hip_flexion: {
      backswing: { up: 'hinge', down: 'hips up' },
      contact:   { up: 'hold hinge', down: 'hips up' },
    },
    torso_lean: {
      backswing: { up: 'taller', down: 'lean in' },
      contact:   { up: 'taller', down: 'lean in' },
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
    follow_through_angle_2d: { up: 'коротший фініш', down: 'вище фініш' },
    stroke_speed: { up: 'повільніше', down: 'швидше' },
    coil_ratio: { up: 'менше обертання', down: 'більше розкрутки' },
  },
  phaseCues: {
    elbow_angle: {
      backswing:     { up: 'не блокуй лікоть', down: 'розправ лікоть' },
      followthrough: { up: 'доводь лікоть', down: 'не затискай' },
    },
    shoulder_angle: {
      backswing:     { up: 'рука нижче', down: 'рука назад' },
      followthrough: { up: 'не перемахуй', down: 'руку вгору' },
    },
    knee_bend: {
      backswing: { up: 'глибше присід', down: 'не пересідай' },
      contact:   { up: 'тримай присід', down: 'не перегинай' },
    },
    hip_flexion: {
      backswing: { up: 'нахил', down: 'стегна вище' },
      contact:   { up: 'тримай нахил', down: 'стегна вище' },
    },
    torso_lean: {
      backswing: { up: 'рівніше', down: 'нахились' },
      contact:   { up: 'рівніше', down: 'нахились' },
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
