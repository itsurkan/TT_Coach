package com.ttcoachai.managers

import android.graphics.Color
import com.ttcoachai.shared.models.StrokePhase
import java.util.Locale

class FeedbackLogManager(private val adapter: FeedbackLogAdapter) {
    private val entries = mutableListOf<FeedbackLogEntry>()
    private var lastStroke = -1
    private var lastPhase: StrokePhase? = null
    private val strokeColors = listOf(0xFFE91E63.toInt(), 0xFF2196F3.toInt(), 0xFF4CAF50.toInt(), 0xFFFF9800.toInt(), 0xFF9C27B0.toInt(), 0xFF00BCD4.toInt())

    fun append(strokeNum: Int, phase: StrokePhase, velocity: Float) {
        if (strokeNum == lastStroke && phase == lastPhase) return
        lastStroke = strokeNum
        lastPhase = phase

        val feedbackMsg = when (phase) {
            StrokePhase.READY -> "Ready position"
            StrokePhase.BACKSWING -> "Start rotation"
            StrokePhase.FORWARD_SWING -> "Accelerate (v=%.3f)".format(Locale.US, velocity)
            StrokePhase.CONTACT -> "Ball contact"
            StrokePhase.FOLLOW_THROUGH -> "Complete swing"
            StrokePhase.RECOVERY -> "Return to ready"
        }

        val name = phase.name.split("_").joinToString("") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        val label = "${if (strokeNum > 0) strokeNum else "RT"}-$name:"
        val color = if (strokeNum > 0) strokeColors[(strokeNum - 1) % strokeColors.size] else Color.BLACK

        entries.add(FeedbackLogEntry(label, feedbackMsg, color))
        adapter.updateList(entries)
    }

    fun clear() {
        entries.clear()
        adapter.updateList(entries)
        lastStroke = -1
        lastPhase = null
    }
}
