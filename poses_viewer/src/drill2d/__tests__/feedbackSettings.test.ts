import { describe, expect, it, beforeEach } from 'vitest'
import {
  DEFAULT_FEEDBACK_SETTINGS, loadFeedbackSettings, saveFeedbackSettings, FEEDBACK_SETTINGS_KEY,
} from '../feedbackSettings'
import type { MetricKey } from '../voiceStyle'

describe('feedbackSettings persistence', () => {
  beforeEach(() => localStorage.clear())

  it('returns defaults when nothing stored', () => {
    expect(loadFeedbackSettings()).toEqual(DEFAULT_FEEDBACK_SETTINGS)
  })
  it('round-trips a saved object', () => {
    const custom = { ...DEFAULT_FEEDBACK_SETTINGS, bandWidthMult: 0.8, enabledMetrics: ['knee_bend'] as MetricKey[] }
    saveFeedbackSettings(custom)
    expect(loadFeedbackSettings()).toEqual(custom)
  })
  it('falls back to defaults on corrupt JSON', () => {
    localStorage.setItem(FEEDBACK_SETTINGS_KEY, '{not json')
    expect(loadFeedbackSettings()).toEqual(DEFAULT_FEEDBACK_SETTINGS)
  })
  it('defaults include all six metrics enabled', () => {
    expect([...DEFAULT_FEEDBACK_SETTINGS.enabledMetrics].sort()).toEqual(
      ['elbow_angle', 'hip_flexion', 'knee_bend', 'shoulder_angle', 'shoulder_tilt', 'torso_lean'],
    )
  })
})
