package com.ttcoachai.shared.drill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedbackCadencePolicyTest {

    private val elbowHigh = FeedbackCue(
        DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_HIGH, 15.0, 3.0, MetricPrecision.PRECISE_DEGREES
    )
    private val kneeLow = FeedbackCue(
        DrillMetrics.METRIC_KNEE_BEND, CueDirection.TOO_LOW, -10.0, 2.0, MetricPrecision.PRECISE_DEGREES
    )

    @Test
    fun firstCueIsEmittedImmediately() {
        val policy = FeedbackCadencePolicy()
        assertEquals(elbowHigh, policy.offer(nowMs = 0L, cues = listOf(kneeLow, elbowHigh)))
    }

    @Test
    fun secondCueWithinMinIntervalIsSuppressed() {
        val policy = FeedbackCadencePolicy(minIntervalMs = 3000, maxIntervalMs = 5000)
        policy.offer(0L, listOf(elbowHigh))
        assertNull(policy.offer(2999L, listOf(kneeLow)))
        assertEquals(kneeLow, policy.offer(3000L, listOf(kneeLow)))
    }

    @Test
    fun emptyCuesEmitNothingAndDoNotResetTheClock() {
        val policy = FeedbackCadencePolicy()
        policy.offer(0L, listOf(elbowHigh))
        assertNull(policy.offer(4000L, emptyList()))
        assertEquals(kneeLow, policy.offer(4001L, listOf(kneeLow)), "empty offer must not consume the window")
    }

    @Test
    fun highestSeverityWins() {
        val policy = FeedbackCadencePolicy()
        assertEquals(elbowHigh, policy.offer(0L, listOf(kneeLow, elbowHigh)))
    }

    @Test
    fun positiveOnlyAfterMaxInterval() {
        val policy = FeedbackCadencePolicy(minIntervalMs = 3000, maxIntervalMs = 5000)
        policy.offer(0L, listOf(elbowHigh))
        assertFalse(policy.offerPositive(4000L), "positive must wait the full maxInterval")
        assertTrue(policy.offerPositive(5000L))
        assertFalse(policy.offerPositive(5001L), "positive consumes the window too")
    }

    @Test
    fun positiveAllowedImmediatelyWhenNothingSpokenYet() {
        assertTrue(FeedbackCadencePolicy().offerPositive(0L))
    }

    @Test
    fun resetClearsTheClock() {
        val policy = FeedbackCadencePolicy()
        policy.offer(0L, listOf(elbowHigh))
        policy.reset()
        assertEquals(kneeLow, policy.offer(1L, listOf(kneeLow)))
    }
}
