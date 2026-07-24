package com.ttcoachai.pose

// FpsTracker.kt — THROWAWAY PROTOTYPE utility (FPS A/B bench only). Plain rolling-average
// timer, no dependencies. Call [tick] once per completed inference with the elapsed wall-clock
// milliseconds; read [averageFps] for a live smoothed value over the last [windowSize] frames.

class FpsTracker(private val windowSize: Int = 30) {

    private val samplesMs = ArrayDeque<Long>()

    fun tick(elapsedMs: Long) {
        samplesMs.addLast(elapsedMs)
        while (samplesMs.size > windowSize) {
            samplesMs.removeFirst()
        }
    }

    val averageFps: Float
        get() {
            if (samplesMs.isEmpty()) return 0f
            val avgMs = samplesMs.sum().toFloat() / samplesMs.size
            return if (avgMs <= 0f) 0f else 1000f / avgMs
        }

    fun reset() {
        samplesMs.clear()
    }
}
