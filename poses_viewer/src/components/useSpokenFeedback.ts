import { useCallback, useEffect, useRef, useState } from 'react'
import type { SpokenFeedbackItem, SpokenSchedule } from '../drill2d/buildSpokenSchedule'
import { lookupClip, type ClipManifest } from '../drill2d/voiceClips'
import type { VoiceProfile } from '../drill2d/voiceStyle'

/** Schedule items whose atMs lies in (prevMs, nowMs] — the ones just crossed. */
export function newlyCrossed(schedule: SpokenSchedule, prevMs: number, nowMs: number): SpokenFeedbackItem[] {
  if (nowMs <= prevMs) return [] // paused or seeked backward — fire nothing
  return schedule.filter(f => f.atMs > prevMs && f.atMs <= nowMs)
}

export interface SpokenFeedbackState {
  log: SpokenFeedbackItem[]
  latest: SpokenFeedbackItem | null
  /** Call on every video timeupdate with currentTime in ms. */
  onTime: (nowMs: number) => void
  /** Reset when the clip or report changes, or on a manual seek. */
  reset: (toMs?: number) => void
}

// Module-level so barge-in can stop whatever is in flight (clip or live utterance).
let currentAudio: HTMLAudioElement | null = null

export function cancelPlayback(): void {
  if (typeof window !== 'undefined' && 'speechSynthesis' in window) window.speechSynthesis.cancel()
  if (currentAudio) {
    currentAudio.pause()
    currentAudio = null
  }
}

/** Play one item: a fresh pre-rendered clip if available, else live Web Speech. Barges in. */
export function speakNow(
  item: { text: string; clipKey?: string },
  profile: VoiceProfile,
  manifest: ClipManifest | null,
): void {
  cancelPlayback()
  const clip = item.clipKey ? lookupClip(manifest, profile.lang, item.text) : null
  if (clip && manifest) {
    const audio = new Audio(`/voice/${encodeURIComponent(manifest.styleId)}/${clip.file}`)
    audio.volume = profile.volume
    currentAudio = audio
    void audio.play().catch(() => {})
    return
  }
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) return
  const u = new SpeechSynthesisUtterance(item.text)
  u.lang = profile.lang === 'uk' ? 'uk-UA' : 'en-US'
  if (profile.voiceURI) {
    const v = window.speechSynthesis.getVoices().find(voice => voice.voiceURI === profile.voiceURI)
    if (v) u.voice = v
  }
  u.rate = profile.rate
  u.pitch = profile.pitch
  u.volume = profile.volume
  window.speechSynthesis.speak(u)
}

/**
 * Fires each schedule item once, when playback first crosses its atMs. Prefers a
 * deterministic pre-rendered clip, falls back to live Web Speech. `muted` keeps the
 * on-screen log but suppresses audio (replaces the old audio/text mode).
 */
export function useSpokenFeedback(
  schedule: SpokenSchedule,
  profile: VoiceProfile,
  manifest: ClipManifest | null,
  muted: boolean,
): SpokenFeedbackState {
  const lastMsRef = useRef(0)
  const [log, setLog] = useState<SpokenFeedbackItem[]>([])
  const [latest, setLatest] = useState<SpokenFeedbackItem | null>(null)
  const profileRef = useRef(profile)
  const manifestRef = useRef(manifest)
  const mutedRef = useRef(muted)
  profileRef.current = profile
  manifestRef.current = manifest
  mutedRef.current = muted

  const onTime = useCallback((nowMs: number) => {
    const fired = newlyCrossed(schedule, lastMsRef.current, nowMs)
    lastMsRef.current = nowMs
    if (fired.length === 0) return
    setLog(prev => [...prev, ...fired])
    setLatest(fired[fired.length - 1])
    if (mutedRef.current) return
    // Speak only the newest crossed item (barge-in); older ones are already stale.
    speakNow(fired[fired.length - 1], profileRef.current, manifestRef.current)
  }, [schedule])

  const reset = useCallback((toMs = 0) => {
    lastMsRef.current = toMs
    setLog([])
    setLatest(null)
    cancelPlayback()
  }, [])

  // New schedule → start fresh.
  useEffect(() => { reset(0) }, [schedule, reset])

  return { log, latest, onTime, reset }
}
