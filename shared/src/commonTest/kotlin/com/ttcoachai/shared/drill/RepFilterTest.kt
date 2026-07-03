package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Stroke2D
import kotlin.test.Test
import kotlin.test.assertEquals

class RepFilterTest {

    private fun stroke(i: Int, peakSpeed: Float, durFrames: Int) = Stroke2D(
        strokeIndex = i,
        startFrame = i * 30,
        peakFrame = i * 30 + durFrames / 2,
        endFrame = i * 30 + durFrames,
        peakSpeed = peakSpeed
    )

    @Test
    fun uniformStrokesAllKept() {
        val s = List(6) { stroke(it, 2.4f, 6) }
        assertEquals(s, RepFilter.filter(s))
    }

    @Test
    fun slowAndOverlongJunkDropped() {
        val good = List(6) { stroke(it, 2.4f, 6) }
        val slow = stroke(6, 1.1f, 6)    // ball pickup: above detector threshold, half the cluster speed
        val smear = stroke(7, 2.4f, 20)  // walking: long movement, plausible peak
        assertEquals(good, RepFilter.filter(good + slow + smear))
    }

    @Test
    fun tooFewStrokesAreNotFiltered() {
        val s = listOf(stroke(0, 2.4f, 6), stroke(1, 0.5f, 30), stroke(2, 5f, 2))
        assertEquals(s, RepFilter.filter(s), "below 4 strokes there is no cluster to trust")
    }
}
