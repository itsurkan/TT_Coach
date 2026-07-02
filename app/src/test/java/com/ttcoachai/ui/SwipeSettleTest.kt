package com.ttcoachai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeSettleTest {

    private val w = 88f          // button/panel width (px in tests)
    private val fling = DEFAULT_FLING_VELOCITY

    // --- decideSwipeSettle: threshold (no fling) ---

    @Test fun `small left drag below half closes`() {
        assertEquals(SwipeState.CLOSED, decideSwipeSettle(-20f, 0f, w, deleteEnabled = true))
    }

    @Test fun `left drag past half opens clone`() {
        assertEquals(SwipeState.OPEN_CLONE, decideSwipeSettle(-50f, 0f, w, deleteEnabled = true))
    }

    @Test fun `small right drag below half closes`() {
        assertEquals(SwipeState.CLOSED, decideSwipeSettle(20f, 0f, w, deleteEnabled = true))
    }

    @Test fun `right drag past half opens delete when enabled`() {
        assertEquals(SwipeState.OPEN_DELETE, decideSwipeSettle(50f, 0f, w, deleteEnabled = true))
    }

    @Test fun `exactly half opens that side`() {
        assertEquals(SwipeState.OPEN_CLONE, decideSwipeSettle(-44f, 0f, w, deleteEnabled = true))
        assertEquals(SwipeState.OPEN_DELETE, decideSwipeSettle(44f, 0f, w, deleteEnabled = true))
    }

    // --- decideSwipeSettle: delete-disabled lock ---

    @Test fun `right drag past half stays closed when delete disabled`() {
        assertEquals(SwipeState.CLOSED, decideSwipeSettle(80f, 0f, w, deleteEnabled = false))
    }

    @Test fun `left drag still opens clone when delete disabled`() {
        assertEquals(SwipeState.OPEN_CLONE, decideSwipeSettle(-80f, 0f, w, deleteEnabled = false))
    }

    // --- decideSwipeSettle: fling overrides a below-half offset ---

    @Test fun `fast fling left opens clone despite small offset`() {
        assertEquals(SwipeState.OPEN_CLONE, decideSwipeSettle(-5f, -fling, w, deleteEnabled = true))
    }

    @Test fun `fast fling right opens delete despite small offset when enabled`() {
        assertEquals(SwipeState.OPEN_DELETE, decideSwipeSettle(5f, fling, w, deleteEnabled = true))
    }

    @Test fun `fast fling right stays closed when delete disabled`() {
        assertEquals(SwipeState.CLOSED, decideSwipeSettle(5f, fling, w, deleteEnabled = false))
    }

    @Test fun `zero offset zero velocity closes`() {
        assertEquals(SwipeState.CLOSED, decideSwipeSettle(0f, 0f, w, deleteEnabled = true))
    }

    // --- clampSwipeOffset ---

    @Test fun `clamp allows both sides when delete enabled`() {
        assertEquals(-88f, clampSwipeOffset(-200f, w, deleteEnabled = true), 0f)
        assertEquals(88f, clampSwipeOffset(200f, w, deleteEnabled = true), 0f)
    }

    @Test fun `clamp blocks right side when delete disabled`() {
        assertEquals(-88f, clampSwipeOffset(-200f, w, deleteEnabled = false), 0f)
        assertEquals(0f, clampSwipeOffset(200f, w, deleteEnabled = false), 0f)
    }

    @Test fun `clamp passes through in-range value`() {
        assertEquals(-30f, clampSwipeOffset(-30f, w, deleteEnabled = true), 0f)
    }
}
