package com.ttcoachai.shared.drill

/**
 * 3–5 s feedback cadence (the product's definition of "real-time" — context doc §1).
 * At most one corrective cue per [minIntervalMs]; positive reinforcement only after
 * [maxIntervalMs] of silence so corrections always have priority for the voice channel.
 *
 * Stateful and single-session: create one per drill run, [reset] between runs.
 */
class FeedbackCadencePolicy(
    private val minIntervalMs: Long = 3000,
    private val maxIntervalMs: Long = 5000
) {

    private var lastEmittedMs: Long? = null

    /** Returns the cue to speak now (highest severity), or null if the window is closed. */
    fun offer(nowMs: Long, cues: List<FeedbackCue>): FeedbackCue? {
        val last = lastEmittedMs
        if (last != null && nowMs - last < minIntervalMs) return null
        val top = cues.maxByOrNull { it.severity } ?: return null
        lastEmittedMs = nowMs
        return top
    }

    /** True if a positive message may be spoken now; consumes the window if so. */
    fun offerPositive(nowMs: Long): Boolean {
        val last = lastEmittedMs
        if (last != null && nowMs - last < maxIntervalMs) return false
        lastEmittedMs = nowMs
        return true
    }

    fun reset() {
        lastEmittedMs = null
    }
}
