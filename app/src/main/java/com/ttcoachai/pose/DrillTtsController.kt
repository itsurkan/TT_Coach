package com.ttcoachai.pose

import android.content.Context
import android.speech.tts.TextToSpeech
import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.drill.SpokenFeedback

/**
 * Thin Android TextToSpeech wrapper for spoken drill feedback.
 *
 * On-screen text ([onScreen]) is the guaranteed channel — always invoked on [speak].
 * TTS is a best-effort secondary channel: only spoken when the engine initialized
 * successfully, the resolved locale is available, and the controller is not muted.
 *
 * Locale resolution delegates entirely to [DrillTtsLocale] (D1); this class does not
 * reimplement language→Locale mapping. Cadence throttling is the caller's
 * responsibility (upstream `FeedbackCadencePolicy`) — this class never re-throttles.
 */
class DrillTtsController(
    private val context: Context,
    private val lang: FeedbackLang,
    private val onScreen: (String) -> Unit,
) {
    private var tts: TextToSpeech? = null
    private var speechAvailable: Boolean = false
    private var muted: Boolean = false
    private var utteranceCounter: Long = 0

    /** Creates the TextToSpeech engine and resolves the locale via [DrillTtsLocale]. Never throws. */
    fun init() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                if (engine != null) {
                    val locale = DrillTtsLocale.resolve(lang) { candidate ->
                        val availability = engine.isLanguageAvailable(candidate)
                        availability >= TextToSpeech.LANG_AVAILABLE
                    }
                    if (locale != null) {
                        engine.language = locale
                        speechAvailable = true
                    } else {
                        speechAvailable = false
                    }
                } else {
                    speechAvailable = false
                }
            } else {
                speechAvailable = false
            }
        }
    }

    /**
     * Delivers feedback. On-screen text is always shown first (guaranteed channel).
     * TTS is spoken only when available and not muted, using QUEUE_FLUSH so the
     * newest correction always wins.
     */
    fun speak(feedback: SpokenFeedback) {
        onScreen(feedback.message)

        if (!speechAvailable || muted) return
        val engine = tts ?: return
        utteranceCounter += 1
        engine.speak(
            feedback.message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "drill-feedback-$utteranceCounter"
        )
    }

    /** Mutes/unmutes the TTS channel. On-screen text is unaffected. */
    fun setMuted(muted: Boolean) {
        this.muted = muted
    }

    /** Stops and releases the engine. Null-safe and idempotent. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        speechAvailable = false
    }
}
