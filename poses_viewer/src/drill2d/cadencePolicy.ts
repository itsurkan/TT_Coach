/**
 * 3–5 s feedback cadence — 1:1 mirror of Kotlin FeedbackCadencePolicy. At most one
 * corrective cue per minIntervalMs; positive reinforcement only after maxIntervalMs
 * of silence (corrections keep priority for the voice channel). Stateful and
 * single-session: one per drill run, reset() between runs. Timestamps must be
 * monotonically increasing.
 */
import { FeedbackCue } from './feedbackCue'

export class FeedbackCadencePolicy {
  private lastEmittedMs: number | null = null

  constructor(
    private readonly minIntervalMs = 3000,
    private readonly maxIntervalMs = 5000,
  ) {
    if (minIntervalMs < 0 || maxIntervalMs < minIntervalMs) {
      throw new Error(`invalid cadence intervals: min=${minIntervalMs} max=${maxIntervalMs}`)
    }
  }

  /** The cue to speak now (highest severity), or null if the window is closed. */
  offer(nowMs: number, cues: FeedbackCue[]): FeedbackCue | null {
    const last = this.lastEmittedMs
    if (last !== null && nowMs - last < this.minIntervalMs) return null
    let top: FeedbackCue | null = null
    for (const c of cues) if (top === null || c.severity > top.severity) top = c
    if (top === null) return null
    this.lastEmittedMs = nowMs
    return top
  }

  /** True if a positive message may be spoken now; consumes the window if so. */
  offerPositive(nowMs: number): boolean {
    const last = this.lastEmittedMs
    if (last !== null && nowMs - last < this.maxIntervalMs) return false
    this.lastEmittedMs = nowMs
    return true
  }

  reset(): void {
    this.lastEmittedMs = null
  }
}
