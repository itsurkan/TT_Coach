package com.ttcoachai.views

import kotlin.math.max
import kotlin.math.min

/**
 * Pure, framework-free coordinate + segment math for the session-review custom Views.
 * Uses a 0..130 normalized vertical space (gridlines at 40/60/80); callers scale the
 * returned Y to the actual view height. Extracted so the geometry is unit-testable.
 */
object ChartGeometry {

    private const val DEFAULT_LO = 40f
    private const val DEFAULT_HI = 80f
    private const val TOP_PAD = 10f     // Y at hi
    private const val SPAN = 110f       // TOP_PAD + SPAN = 120 = Y at lo

    /** Auto-expanding domain: default [40,80], widened to include any out-of-range data. */
    fun domain(values: List<Float>): Pair<Float, Float> {
        if (values.isEmpty()) return DEFAULT_LO to DEFAULT_HI
        val lo = min(DEFAULT_LO, values.min())
        val hi = max(DEFAULT_HI, values.max())
        return lo to hi
    }

    /** value -> normalized Y (0..130 space); v=hi -> 10, v=lo -> 120. */
    fun valueToY(value: Float, lo: Float, hi: Float): Float {
        val range = if (hi - lo <= 0f) 1f else hi - lo
        return TOP_PAD + (hi - value) / range * SPAN
    }

    fun cleanFraction(clean: Int, error: Int): Float {
        val total = clean + error
        return if (total <= 0) 0f else clean.toFloat() / total
    }

    fun errorFraction(clean: Int, error: Int): Float {
        val total = clean + error
        return if (total <= 0) 0f else error.toFloat() / total
    }
}
