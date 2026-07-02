package com.ttcoachai.views

import kotlin.math.round

data class StepperRange(
    val min: Double,
    val max: Double,
    val step: Double,
    val decimals: Int,
) {
    fun clamp(v: Double): Double = v.coerceIn(min, max)
    fun inc(v: Double): Double = clamp(snap(v + step))
    fun dec(v: Double): Double = clamp(snap(v - step))
    private fun snap(v: Double): Double = round(v / step) * step
    fun format(v: Double, suffix: String): String {
        val n = if (decimals == 0) round(v).toInt().toString()
                else "%.${decimals}f".format(clamp(v))
        return n + suffix
    }
}
