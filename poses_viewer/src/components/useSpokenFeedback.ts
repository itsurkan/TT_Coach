import { useCallback, useEffect, useRef, useState } from 'react'
import { SpokenFeedback } from '../drill2d/analyzeDrill'

/** Feedback entries whose timestamp lies in (prevMs, nowMs] — the ones just crossed. */
export function newlyCrossed(feed: SpokenFeedback[], prevMs: number, nowMs: number): SpokenFeedback[] {
  if (nowMs <= prevMs) return [] // paused or seeked backward — fire nothing
  return feed.filter(f => f.timestampMs > prevMs && f.timestampMs <= nowMs)
}

export type FeedbackMode = 'audio' | 'text'

export interface SpokenFeedbackState {
  log: SpokenFeedback[]
  latest: SpokenFeedback | null
  /** Call on every video timeupdate with currentTime in ms. */
  onTime: (nowMs: number) => void
  /** Reset when the clip or report changes, or on a manual seek. */
  reset: (toMs?: number) => void
}

/**
 * Speaks each feedback line once, when playback first crosses its timestamp.
 * Audio via window.speechSynthesis (EN voice); text mode still logs/banners.
 */
export function useSpokenFeedback(feed: SpokenFeedback[], mode: FeedbackMode): SpokenFeedbackState {
  const lastMsRef = useRef(0)
  const [log, setLog] = useState<SpokenFeedback[]>([])
  const [latest, setLatest] = useState<SpokenFeedback | null>(null)

  const speak = useCallback((msg: string) => {
    if (mode !== 'audio') return
    if (typeof window === 'undefined' || !('speechSynthesis' in window)) return
    const u = new SpeechSynthesisUtterance(msg)
    u.lang = 'en-US'
    window.speechSynthesis.speak(u)
  }, [mode])

  const onTime = useCallback((nowMs: number) => {
    const fired = newlyCrossed(feed, lastMsRef.current, nowMs)
    lastMsRef.current = nowMs
    if (fired.length === 0) return
    setLog(prev => [...prev, ...fired])
    setLatest(fired[fired.length - 1])
    for (const f of fired) speak(f.message)
  }, [feed, speak])

  const reset = useCallback((toMs = 0) => {
    lastMsRef.current = toMs
    setLog([])
    setLatest(null)
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) window.speechSynthesis.cancel()
  }, [])

  // New report → start fresh.
  useEffect(() => { reset(0) }, [feed, reset])

  return { log, latest, onTime, reset }
}
