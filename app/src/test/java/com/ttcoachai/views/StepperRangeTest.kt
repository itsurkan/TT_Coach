package com.ttcoachai.views

import org.junit.Assert.assertEquals
import org.junit.Test

class StepperRangeTest {
    @Test fun inc_and_clamp_at_max() {
        val r = StepperRange(min = 0.0, max = 2.0, step = 0.1, decimals = 1)
        assertEquals(1.1, r.inc(1.0), 1e-9)
        assertEquals(2.0, r.inc(2.0), 1e-9)   // clamps
    }
    @Test fun dec_and_clamp_at_min() {
        val r = StepperRange(0.0, 2.0, 0.1, 1)
        assertEquals(0.0, r.dec(0.0), 1e-9)
    }
    @Test fun format_int_vs_decimal_with_suffix() {
        assertEquals("500ms", StepperRange(0.0, 2000.0, 50.0, 0).format(500.0, "ms"))
        assertEquals("1.0", StepperRange(0.0, 2.0, 0.1, 1).format(1.0, ""))
        assertEquals("0°", StepperRange(-45.0, 45.0, 5.0, 0).format(0.0, "°"))
    }
}
