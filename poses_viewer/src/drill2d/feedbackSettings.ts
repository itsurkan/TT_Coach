/**
 * Feedback decision/policy — what & when to coach. Drives BOTH the on-screen table
 * and the voice (single source of truth). Reproduction (TTS voice + wording) lives
 * separately in VoiceStyle. Edited in the Налаштування panel; persisted on its own
 * localStorage key. Defaults seeded from the old "Efficient" preset numbers.
 */
import { VOICE_METRIC_KEYS, type MetricKey } from './voiceStyle'

export interface FeedbackSettings {
  enabledMetrics: MetricKey[]
  bandWidthMult: number
  minMeaningfulDeltaDeg: number
  reminderIntervalMs: number
  varyCues: boolean
  correctiveMinGapMs: number
  praiseMinSilenceMs: number
  postStrokeGapMs: number
  praiseEnabled: boolean
  praiseOnCorrection: boolean
  praiseOnStreak: boolean
  praiseStreakLen: number
  skipStaleEnabled: boolean
  skipStaleMarginMs: number
}

export const DEFAULT_FEEDBACK_SETTINGS: FeedbackSettings = {
  enabledMetrics: [...VOICE_METRIC_KEYS],
  bandWidthMult: 1.4,
  minMeaningfulDeltaDeg: 7,
  reminderIntervalMs: 10000,
  varyCues: true,
  correctiveMinGapMs: 5000,
  praiseMinSilenceMs: 10000,
  postStrokeGapMs: 300,
  praiseEnabled: true,
  praiseOnCorrection: true,
  praiseOnStreak: false,
  praiseStreakLen: 3,
  skipStaleEnabled: true,
  skipStaleMarginMs: 150,
}

export const FEEDBACK_SETTINGS_KEY = 'strokes_feedback_settings'

export function loadFeedbackSettings(): FeedbackSettings {
  if (typeof localStorage === 'undefined') return { ...DEFAULT_FEEDBACK_SETTINGS }
  try {
    const raw = localStorage.getItem(FEEDBACK_SETTINGS_KEY)
    if (!raw) return { ...DEFAULT_FEEDBACK_SETTINGS }
    const parsed = JSON.parse(raw) as Partial<FeedbackSettings>
    // Merge over defaults so a newly-added field is never undefined.
    return { ...DEFAULT_FEEDBACK_SETTINGS, ...parsed }
  } catch {
    return { ...DEFAULT_FEEDBACK_SETTINGS }
  }
}

export function saveFeedbackSettings(s: FeedbackSettings): void {
  if (typeof localStorage === 'undefined') return
  localStorage.setItem(FEEDBACK_SETTINGS_KEY, JSON.stringify(s))
}
