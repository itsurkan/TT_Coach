package com.ttcoachai.pose

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ttcoachai.shared.drill.CueDirection
import com.ttcoachai.shared.drill.DrillMetrics
import com.ttcoachai.shared.drill.FeedbackCue
import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.drill.MetricPrecision
import com.ttcoachai.shared.drill.SpokenFeedback
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for [DrillTtsController] (Phase 3, task D2).
 *
 * Asserts only the deterministic, engine-independent channel: [DrillTtsController.speak]
 * ALWAYS invokes the on-screen callback exactly once per call, regardless of TTS engine
 * availability — and muting (`setMuted(true)`) silences only the spoken channel, never
 * on-screen text (per the class doc: "On-screen text is the guaranteed channel").
 *
 * Actual TTS audio output is NOT asserted here: whether `TextToSpeech` initializes
 * successfully and which locales are reported available depends on the device/emulator
 * image (system TTS engine presence, installed language packs) — that is a manual/device
 * check, not something this suite can assert deterministically off-device.
 */
@RunWith(AndroidJUnit4::class)
class DrillTtsControllerTest {

    private lateinit var controller: DrillTtsController
    private val onScreenCollected = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        controller = DrillTtsController(context, FeedbackLang.EN, onScreen = { onScreenCollected += it })
        controller.init()
    }

    @After
    fun tearDown() {
        controller.shutdown()
    }

    @Test
    fun speak_always_delivers_on_screen_text_exactly_once() {
        val feedback = SpokenFeedback(
            timestampMs = 1_000L,
            message = "Extend your follow-through",
            cue = FeedbackCue(
                metricKey = DrillMetrics.METRIC_ELBOW_ANGLE,
                direction = CueDirection.TOO_LOW,
                deltaFromMean = -8.0,
                severity = 2.1,
                precision = MetricPrecision.PRECISE_DEGREES
            )
        )

        controller.speak(feedback)

        assertEquals(listOf(feedback.message), onScreenCollected)
    }

    @Test
    fun muting_silences_tts_but_on_screen_still_fires() {
        val feedback = SpokenFeedback(
            timestampMs = 2_000L,
            message = "Keep your elbow steady",
            cue = null
        )

        controller.setMuted(true)
        controller.speak(feedback)

        // Muting only affects the TTS engine channel (untestable off-device); the
        // on-screen callback is the guaranteed channel and must still fire.
        assertEquals(listOf(feedback.message), onScreenCollected)
    }
}
