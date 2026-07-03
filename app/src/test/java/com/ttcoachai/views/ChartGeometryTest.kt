package com.ttcoachai.views

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartGeometryTest {

    @Test
    fun domain_defaultsTo40to80_whenDataInside() {
        val (lo, hi) = ChartGeometry.domain(listOf(50f, 60f, 78f))
        assertEquals(40f, lo, 0.001f)
        assertEquals(80f, hi, 0.001f)
    }

    @Test
    fun domain_expandsBelow40AndAbove80() {
        val (lo, hi) = ChartGeometry.domain(listOf(20f, 95f))
        assertEquals(20f, lo, 0.001f)
        assertEquals(95f, hi, 0.001f)
    }

    @Test
    fun domain_emptyData_defaults() {
        val (lo, hi) = ChartGeometry.domain(emptyList())
        assertEquals(40f, lo, 0.001f)
        assertEquals(80f, hi, 0.001f)
    }

    @Test
    fun valueToY_mapsHighValueToSmallerY() {
        // higher accuracy must sit higher on screen (smaller Y)
        val yHigh = ChartGeometry.valueToY(80f, 40f, 80f)
        val yLow = ChartGeometry.valueToY(40f, 40f, 80f)
        assertEquals(true, yHigh < yLow)
    }

    @Test
    fun valueToY_atTopOfDomain_isTenNormalized() {
        // spec: y = 10 + (hi - v)/(hi-lo) * 110 in a 0..130 space; v=hi -> 10
        val y = ChartGeometry.valueToY(80f, 40f, 80f)
        assertEquals(10f, y, 0.001f)
    }

    @Test
    fun valueToY_atBottomOfDomain_is120Normalized() {
        val y = ChartGeometry.valueToY(40f, 40f, 80f)
        assertEquals(120f, y, 0.001f)
    }

    @Test
    fun cleanFraction_andErrorFraction() {
        assertEquals(0.75f, ChartGeometry.cleanFraction(3, 1), 0.001f)
        assertEquals(0.25f, ChartGeometry.errorFraction(3, 1), 0.001f)
    }

    @Test
    fun fractions_emptyCountsAreZero() {
        assertEquals(0f, ChartGeometry.cleanFraction(0, 0), 0.001f)
        assertEquals(0f, ChartGeometry.errorFraction(0, 0), 0.001f)
    }
}
