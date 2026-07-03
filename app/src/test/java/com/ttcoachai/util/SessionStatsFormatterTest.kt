package com.ttcoachai.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStatsFormatterTest {
    @Test fun formatDuration_pads_minutes_and_seconds() {
        assertEquals("00:00", SessionStatsFormatter.formatDuration(0))
        assertEquals("00:09", SessionStatsFormatter.formatDuration(9))
        assertEquals("01:05", SessionStatsFormatter.formatDuration(65))
        assertEquals("12:03", SessionStatsFormatter.formatDuration(723))
    }

    @Test fun cleanPercent_computes_rounded_percentage() {
        assertEquals(80, SessionStatsFormatter.cleanPercent(goodStrokes = 8, totalStrokes = 10))
        assertEquals(100, SessionStatsFormatter.cleanPercent(goodStrokes = 3, totalStrokes = 3))
        assertEquals(67, SessionStatsFormatter.cleanPercent(goodStrokes = 2, totalStrokes = 3))
    }

    @Test fun cleanPercent_guards_divide_by_zero() {
        assertEquals(0, SessionStatsFormatter.cleanPercent(goodStrokes = 0, totalStrokes = 0))
    }
}
