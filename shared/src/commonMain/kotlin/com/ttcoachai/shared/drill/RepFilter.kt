package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Stroke2D

/**
 * Minimal stroke/non-stroke discrimination (DESIGN_LIMITATIONS L-03): keeps only
 * strokes whose peak speed AND duration lie within [median/BAND, median×BAND] of
 * the session's medians. Ball pickups and hand wipes are slower than drill strokes;
 * walking is longer — both fall outside the dominant cluster. Below
 * [MIN_STROKES_TO_FILTER] there is no cluster to trust, so input passes through.
 * Direction-based recovery-swing removal happens BEFORE this (ForwardStrokeFilter),
 * so the medians here describe forward strokes only.
 *
 * Note: private median is the module's fifth copy — consolidation is a post-merge
 * /simplify candidate, not worth re-threading earlier tasks now.
 */
object RepFilter {

    const val MIN_STROKES_TO_FILTER = 4
    const val SPEED_BAND = 2.0f
    const val DURATION_BAND = 2.0f

    fun filter(strokes: List<Stroke2D>): List<Stroke2D> {
        if (strokes.size < MIN_STROKES_TO_FILTER) return strokes
        val medSpeed = median(strokes.map { it.peakSpeed })
        val medDur = median(strokes.map { (it.endFrame - it.startFrame).toFloat() })
        return strokes.filter { s ->
            val dur = (s.endFrame - s.startFrame).toFloat()
            s.peakSpeed >= medSpeed / SPEED_BAND && s.peakSpeed <= medSpeed * SPEED_BAND &&
                dur >= medDur / DURATION_BAND && dur <= medDur * DURATION_BAND
        }
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }
}
