package com.ttcoachai.pose

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.drill.SpokenFeedback
import com.ttcoachai.shared.drill.VoicePresetCatalog

/**
 * Voice-preset controller for the RTMPose live drill path: prefers pre-recorded
 * clips for the chosen [styleId]/[lang], falling back to on-device TTS when a
 * clip is missing or fails to play. [DrillTtsController] (plain always-TTS) is
 * left untouched — this class is a parallel implementation, not a refactor of it.
 *
 * ## Clip-first, TTS-fallback
 * Recorded clips carry the coach's actual voice/performance for the preset
 * ("preset-playful" / "preset-strict" / "preset-efficient"); live TTS is a
 * lower-quality fallback used only when the manifest has no fresh entry for
 * the phrase (see [VoiceClipManifest.lookup]) or clip playback errors out.
 * Mirrors `speakNow` in `poses_viewer/src/components/useSpokenFeedback.ts`.
 *
 * ## Barge-in: newest correction wins
 * A new [speak] call always wins over whatever audio is in flight: any playing
 * clip is stopped/released, and any pending/speaking TTS utterance is flushed,
 * before the new phrase is dispatched — on both the clip path and the TTS
 * fallback path. This mirrors `cancelPlayback()` in useSpokenFeedback.ts, which
 * stops both channels unconditionally before speaking the next item.
 *
 * ## Display text != spoken phrase
 * [onScreen] always receives [SpokenFeedback.message] — the richer, precision-
 * aware text produced by the shared feedback catalog (it may carry an exact
 * degree number per the trust rule, see `MetricPrecisionPolicy`). The *spoken*
 * phrase (clip or TTS) is instead the shorter, plain-imperative preset phrase
 * from [VoicePresetCatalog] ("bend the elbow", not "elbow angle is 12° short
 * of your baseline") — that's the phrase the clips were recorded against, and
 * it reads naturally aloud. When no preset phrase exists (e.g. unknown style,
 * or [FeedbackCue.metricKey] absent from every preset), [feedback.message]
 * itself is spoken instead, so audio feedback still happens.
 */
class PresetVoiceController(
    private val context: Context,
    private val styleId: String,
    private val lang: FeedbackLang,
    private val onScreen: (String) -> Unit,
) {
    private var tts: TextToSpeech? = null
    @Volatile private var speechAvailable: Boolean = false
    private var muted: Boolean = false
    private var utteranceCounter: Long = 0

    @Volatile private var manifest: VoiceClipManifest? = null
    private var clipPlayer: MediaPlayer? = null

    /**
     * Creates the TextToSpeech engine and resolves the locale via [DrillTtsLocale]
     * (same pattern as [DrillTtsController.init]). Never throws. The clip manifest
     * is preloaded here on a plain background thread (asset read is blocking I/O)
     * so init() itself stays cheap and [speak] never blocks the UI thread on the
     * manifest read; [manifest] is `@Volatile` so the background write is visible
     * to later reads on the UI thread without extra handoff.
     */
    fun init() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                if (engine != null) {
                    val locale = DrillTtsLocale.resolve(lang) { candidate ->
                        engine.isLanguageAvailable(candidate) >= TextToSpeech.LANG_AVAILABLE
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
        Thread({ manifest = VoiceClipManifest.load(context, styleId) }, "PresetVoiceManifestLoad").start()
    }

    /**
     * Delivers feedback. [onScreen] is always invoked first with [SpokenFeedback.message]
     * (guaranteed channel, unaffected by mute). If muted, no audio is played.
     *
     * Otherwise: selects a preset phrase — [VoicePresetCatalog.phraseFor] for a
     * correction ([SpokenFeedback.cue] non-null), or [VoicePresetCatalog.praise]
     * (via [pickIndex]) for positive reinforcement (cue null) — and plays the
     * matching recorded clip if the manifest has a fresh entry, else speaks the
     * phrase (or the message, if no phrase was found) via TTS.
     */
    fun speak(feedback: SpokenFeedback, pickIndex: (Int) -> Int = { 0 }) {
        onScreen(feedback.message)

        if (muted) return

        val cue = feedback.cue
        val phrase = if (cue != null) {
            VoicePresetCatalog.phraseFor(styleId, lang, cue)
        } else {
            VoicePresetCatalog.praise(styleId, lang, pickIndex)
        }

        val langCode = VoicePresetCatalog.langCode(lang)
        val activeManifest = manifest
        val entry = if (phrase != null && activeManifest != null) activeManifest.lookup(langCode, phrase) else null

        if (entry != null && activeManifest != null && playClip(activeManifest, entry)) {
            return
        }

        speakTts(phrase ?: feedback.message)
    }

    /**
     * Plays [entry] from assets. Barge-in: stops/releases any in-flight clip AND
     * cancels any pending/speaking TTS first (mirrors `cancelPlayback()` stopping
     * both channels before dispatching the next item). Playback is prepared
     * asynchronously ([MediaPlayer.prepareAsync] + [MediaPlayer.setOnPreparedListener])
     * so this call never blocks the UI thread; returns true if the clip was
     * successfully handed to the player to prepare (not whether playback has
     * actually started yet — that happens later, off this call, on the prepared
     * listener).
     */
    private fun playClip(manifest: VoiceClipManifest, entry: VoiceClipManifest.Entry): Boolean {
        stopClip()
        stopTts()

        var afd: AssetFileDescriptor? = null
        var player: MediaPlayer? = null
        return try {
            afd = context.assets.openFd(manifest.assetPath(entry))
            player = MediaPlayer()
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            afd = null
            player.setOnCompletionListener {
                it.release()
                if (clipPlayer === it) clipPlayer = null
            }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (clipPlayer === mp) clipPlayer = null
                true
            }
            player.setOnPreparedListener {
                if (clipPlayer === it) it.start()
            }
            clipPlayer = player
            player.prepareAsync()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play voice clip for styleId=$styleId file=${entry.file}", e)
            player?.release()
            clipPlayer = null
            false
        } finally {
            try {
                afd?.close()
            } catch (_: Exception) {
                // best-effort close; nothing further to do if this fails
            }
        }
    }

    /**
     * Speaks [text] via TTS with QUEUE_FLUSH (newest correction wins). Barge-in:
     * stops/releases any in-flight clip first (mirrors `cancelPlayback()`).
     * No-op if TTS is unavailable.
     */
    private fun speakTts(text: String) {
        stopClip()

        if (!speechAvailable) return
        val engine = tts ?: return
        utteranceCounter += 1
        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "preset-voice-$utteranceCounter"
        )
    }

    private fun stopClip() {
        try {
            clipPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop in-flight voice clip", e)
        } finally {
            clipPlayer = null
        }
    }

    private fun stopTts() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop in-flight TTS utterance", e)
        }
    }

    /** Mutes/unmutes audio (both clip and TTS channels). On-screen text is unaffected. */
    fun setMuted(muted: Boolean) {
        this.muted = muted
    }

    /** Stops and releases both the clip player and the TTS engine. Null-safe and idempotent. */
    fun shutdown() {
        stopClip()
        tts?.stop()
        tts?.shutdown()
        tts = null
        speechAvailable = false
    }

    companion object {
        private const val TAG = "PresetVoiceController"
    }
}
