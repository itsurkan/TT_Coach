package com.ttcoachai.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DrillReferenceResolverTest {

    private val parsed = mapOf("knee_bend" to 100.0..140.0)
    private val shippedDefaults = mapOf(
        "elbow_angle" to 30.0..70.0,
        "knee_bend" to 120.0..160.0
    )

    @Test
    fun fallsBackToShippedDefaultsWhenReferenceTypeExtraAbsent() {
        val result = DrillReferenceResolver.resolveMetricBands(
            referenceTypeExtraPresent = false,
            parsedBands = parsed,
            shippedDefaultBands = shippedDefaults
        )
        assertEquals(shippedDefaults, result)
    }

    @Test
    fun usesParsedBandsAsIsWhenReferenceTypeExtraPresent() {
        val result = DrillReferenceResolver.resolveMetricBands(
            referenceTypeExtraPresent = true,
            parsedBands = parsed,
            shippedDefaultBands = shippedDefaults
        )
        assertEquals(parsed, result)
    }

    @Test
    fun emptyParsedBandsStaySilentWhenExtraPresent() {
        val result = DrillReferenceResolver.resolveMetricBands(
            referenceTypeExtraPresent = true,
            parsedBands = emptyMap(),
            shippedDefaultBands = shippedDefaults
        )
        assertEquals(emptyMap<String, ClosedRange<Double>>(), result)
    }
}
