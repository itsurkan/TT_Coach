package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SignalMathTest {

    // ---- median(Float) ----

    @Test
    fun medianFloatOddCountReturnsMiddleElement() {
        assertEquals(2f, SignalMath.median(listOf(3f, 1f, 2f)))
    }

    @Test
    fun medianFloatEvenCountReturnsAverageOfMiddleTwo() {
        assertEquals(2.5f, SignalMath.median(listOf(1f, 2f, 3f, 4f)))
    }

    @Test
    fun medianFloatSingleElementReturnsThatElement() {
        assertEquals(7f, SignalMath.median(listOf(7f)))
    }

    @Test
    fun medianFloatEmptyListThrows() {
        assertFailsWith<IllegalArgumentException> { SignalMath.median(emptyList<Float>()) }
    }

    // ---- median(Double) ----

    @Test
    fun medianDoubleOddCountReturnsMiddleElement() {
        assertEquals(2.0, SignalMath.median(listOf(3.0, 1.0, 2.0)))
    }

    @Test
    fun medianDoubleEvenCountReturnsAverageOfMiddleTwo() {
        assertEquals(2.5, SignalMath.median(listOf(1.0, 2.0, 3.0, 4.0)))
    }

    @Test
    fun medianDoubleSingleElementReturnsThatElement() {
        assertEquals(7.0, SignalMath.median(listOf(7.0)))
    }

    @Test
    fun medianDoubleEmptyListThrows() {
        assertFailsWith<IllegalArgumentException> { SignalMath.median(emptyList<Double>()) }
    }

    // ---- medianTorsoLength ----

    private fun frameWithTorso(shoulderY: Float, hipY: Float, score: Float = 1f): PoseFrame2D {
        val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
        kp[Coco17.LEFT_SHOULDER] = Keypoint2D(0.49f, shoulderY, score)
        kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(0.51f, shoulderY, score)
        kp[Coco17.LEFT_HIP] = Keypoint2D(0.49f, hipY, score)
        kp[Coco17.RIGHT_HIP] = Keypoint2D(0.51f, hipY, score)
        return PoseFrame2D(frameIndex = 0, timestampMs = 0, keypoints = kp)
    }

    @Test
    fun medianTorsoLengthComputesMedianAcrossFrames() {
        val frames = listOf(
            frameWithTorso(shoulderY = 0.30f, hipY = 0.50f), // len 0.20
            frameWithTorso(shoulderY = 0.30f, hipY = 0.55f), // len 0.25
            frameWithTorso(shoulderY = 0.30f, hipY = 0.60f)  // len 0.30
        )
        val result = SignalMath.medianTorsoLength(frames, xScale = 1f, minScore = 0.3f)
        assertEquals(0.25f, result)
    }

    @Test
    fun medianTorsoLengthNullOnEmptyFrames() {
        assertNull(SignalMath.medianTorsoLength(emptyList(), xScale = 1f, minScore = 0.3f))
    }

    @Test
    fun medianTorsoLengthNullWhenAllFramesLowScore() {
        val frames = listOf(frameWithTorso(shoulderY = 0.30f, hipY = 0.55f, score = 0.1f))
        assertNull(SignalMath.medianTorsoLength(frames, xScale = 1f, minScore = 0.3f))
    }
}
