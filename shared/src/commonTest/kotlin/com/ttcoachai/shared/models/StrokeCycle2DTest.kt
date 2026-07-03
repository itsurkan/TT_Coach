package com.ttcoachai.shared.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StrokeCycle2DTest {

    private fun stroke(i: Int, start: Int, peak: Int, end: Int, speed: Float = 2.4f) = Stroke2D(
        strokeIndex = i,
        startFrame = start,
        peakFrame = peak,
        endFrame = end,
        peakSpeed = speed
    )

    @Test
    fun pairedCycleForwardsFieldsFromDrive() {
        val backswing = stroke(0, start = 10, peak = 20, end = 30)
        val drive = stroke(1, start = 30, peak = 45, end = 60, speed = 3.1f)
        val cycle = StrokeCycle2D.of(backswing, drive)

        assertEquals(drive.peakFrame, cycle.peakFrame)
        assertEquals(drive.peakSpeed, cycle.peakSpeed)
        assertEquals(backswing.startFrame, cycle.startFrame)
        assertEquals(drive.endFrame, cycle.endFrame)
    }

    @Test
    fun unpairedCycleUsesDriveStartFrame() {
        val drive = stroke(0, start = 30, peak = 45, end = 60, speed = 2.8f)
        val cycle = StrokeCycle2D.of(null, drive)

        assertNull(cycle.backswing)
        assertEquals(drive.peakFrame, cycle.peakFrame)
        assertEquals(drive.peakSpeed, cycle.peakSpeed)
        assertEquals(drive.startFrame, cycle.startFrame)
        assertEquals(drive.endFrame, cycle.endFrame)
    }
}
