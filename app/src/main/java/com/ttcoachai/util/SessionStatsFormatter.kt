package com.ttcoachai.util

import kotlin.math.roundToInt

/**
 * Pure presentation-layer transforms for the end-session and session-summary sheets
 * (Dialogs 14b/14c). Does not re-derive raw counts — callers fetch those from
 * TrainingStateManager and pass primitives in here.
 */
object SessionStatsFormatter {

    fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }

    fun cleanPercent(goodStrokes: Int, totalStrokes: Int): Int {
        if (totalStrokes <= 0) return 0
        return (goodStrokes.toDouble() / totalStrokes * 100).roundToInt()
    }
}
