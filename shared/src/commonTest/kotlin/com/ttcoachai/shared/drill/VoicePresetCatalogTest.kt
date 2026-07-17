package com.ttcoachai.shared.drill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies [VoicePresetCatalog] against poses_viewer/src/drill2d/voiceStyle.ts
 * (the source of truth for phrase text) and, via [VoiceClipKeys], against real
 * entries in app/src/main/assets/voice/<preset>/manifest.json (the source of
 * truth for what's actually recorded). A mismatch in either direction means a
 * live cue would either say the wrong thing or silently miss its clip.
 */
class VoicePresetCatalogTest {

    private fun cue(metricKey: String, direction: CueDirection) = FeedbackCue(
        metricKey = metricKey,
        direction = direction,
        deltaFromMean = 0.0,
        severity = 1.0,
        precision = MetricPrecision.QUALITATIVE
    )

    // ---- phraseFor: spot-check verbatim strings from voiceStyle.ts ----

    @Test
    fun playfulEnElbowTooHighMatchesVoiceStyleTs() {
        // voiceStyle.ts PLAYFUL_EN.cues.elbow_angle.up = 'give the elbow a little bend'
        assertEquals(
            "give the elbow a little bend",
            VoicePresetCatalog.phraseFor("preset-playful", FeedbackLang.EN, cue(DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_HIGH))
        )
    }

    @Test
    fun playfulEnElbowTooLowMatchesVoiceStyleTs() {
        // voiceStyle.ts PLAYFUL_EN.cues.elbow_angle.down = 'reach through it'
        assertEquals(
            "reach through it",
            VoicePresetCatalog.phraseFor("preset-playful", FeedbackLang.EN, cue(DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_LOW))
        )
    }

    @Test
    fun playfulUkTorsoLeanTooHighMatchesVoiceStyleTs() {
        // voiceStyle.ts PLAYFUL_UK.cues.torso_lean.up = 'тримайся трохи рівніше'
        assertEquals(
            "тримайся трохи рівніше",
            VoicePresetCatalog.phraseFor("preset-playful", FeedbackLang.UA, cue(DrillMetrics.METRIC_TORSO_LEAN, CueDirection.TOO_HIGH))
        )
    }

    @Test
    fun strictEnShoulderTiltMatchesVoiceStyleTs() {
        // voiceStyle.ts STRICT_EN.cues.shoulder_tilt = { up/down: 'level the shoulders' } (same both ways)
        assertEquals(
            "level the shoulders",
            VoicePresetCatalog.phraseFor("preset-strict", FeedbackLang.EN, cue(DrillMetrics.METRIC_SHOULDER_TILT, CueDirection.TOO_HIGH))
        )
        assertEquals(
            "level the shoulders",
            VoicePresetCatalog.phraseFor("preset-strict", FeedbackLang.EN, cue(DrillMetrics.METRIC_SHOULDER_TILT, CueDirection.TOO_LOW))
        )
    }

    @Test
    fun efficientUkKneeBendMatchesVoiceStyleTs() {
        // voiceStyle.ts EFFICIENT_UK.cues.knee_bend = { up: 'зігни коліна', down: 'вище' }
        assertEquals(
            "зігни коліна",
            VoicePresetCatalog.phraseFor("preset-efficient", FeedbackLang.UA, cue(DrillMetrics.METRIC_KNEE_BEND, CueDirection.TOO_HIGH))
        )
        assertEquals(
            "вище",
            VoicePresetCatalog.phraseFor("preset-efficient", FeedbackLang.UA, cue(DrillMetrics.METRIC_KNEE_BEND, CueDirection.TOO_LOW))
        )
    }

    // ---- every metric key DrillFeedbackEngine actually emits (DrillMetrics.ALL_KEYS),
    //      for every preset/lang/direction, must resolve to a non-null phrase ----

    @Test
    fun everyRuntimeMetricKeyResolvesForEveryPresetLangDirection() {
        val styleIds = listOf("preset-playful", "preset-strict", "preset-efficient")
        for (styleId in styleIds) {
            for (lang in FeedbackLang.entries) {
                for (metricKey in DrillMetrics.ALL_KEYS) {
                    for (direction in CueDirection.entries) {
                        val phrase = VoicePresetCatalog.phraseFor(styleId, lang, cue(metricKey, direction))
                        assertNotNull(phrase, "expected a phrase for $styleId/$lang/$metricKey/$direction")
                        assertTrue(phrase.isNotBlank())
                    }
                }
            }
        }
    }

    @Test
    fun unknownStyleIdReturnsNull() {
        assertNull(VoicePresetCatalog.phraseFor("preset-does-not-exist", FeedbackLang.EN, cue(DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_HIGH)))
    }

    @Test
    fun unknownMetricKeyReturnsNull() {
        // Rhythm-rule metric keys (phase names) are never routed here by DrillFeedbackEngine,
        // but phraseFor must still fail closed rather than throw for an unmapped key.
        assertNull(VoicePresetCatalog.phraseFor("preset-playful", FeedbackLang.EN, cue("backswing", CueDirection.TOO_HIGH)))
    }

    // ---- praise ----

    @Test
    fun praiseIsNonEmptyForEveryPresetAndLang() {
        val styleIds = listOf("preset-playful", "preset-strict", "preset-efficient")
        for (styleId in styleIds) {
            for (lang in FeedbackLang.entries) {
                val phrase = VoicePresetCatalog.praise(styleId, lang) { 0 }
                assertNotNull(phrase, "expected praise for $styleId/$lang")
                assertTrue(phrase.isNotBlank())
            }
        }
    }

    @Test
    fun praiseUsesInjectedPickIndex() {
        // voiceStyle.ts STRICT_EN.praise = ["that's the shape", 'clean — repeat that', 'correct']
        assertEquals("that's the shape", VoicePresetCatalog.praise("preset-strict", FeedbackLang.EN) { 0 })
        assertEquals("clean — repeat that", VoicePresetCatalog.praise("preset-strict", FeedbackLang.EN) { 1 })
        assertEquals("correct", VoicePresetCatalog.praise("preset-strict", FeedbackLang.EN) { 2 })
    }

    @Test
    fun praiseUnknownStyleIdReturnsNull() {
        assertNull(VoicePresetCatalog.praise("preset-does-not-exist", FeedbackLang.EN) { 0 })
    }

    // ---- langCode ----

    @Test
    fun langCodeMapsEnAndUa() {
        assertEquals("en", VoicePresetCatalog.langCode(FeedbackLang.EN))
        assertEquals("uk", VoicePresetCatalog.langCode(FeedbackLang.UA))
    }

    // ---- golden integration: ported phrases must hash to REAL manifest keys ----
    // Source manifests: app/src/main/assets/voice/{preset-playful,preset-efficient,preset-strict}/manifest.json

    @Test
    fun playfulEnElbowUpHashesToRealManifestKey() {
        // app/src/main/assets/voice/preset-playful/manifest.json: "en__fn2ioi" -> "give the elbow a little bend"
        val phrase = VoicePresetCatalog.phraseFor("preset-playful", FeedbackLang.EN, cue(DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_HIGH))!!
        assertEquals("en__fn2ioi", VoiceClipKeys.clipKey(VoicePresetCatalog.langCode(FeedbackLang.EN), phrase))
    }

    @Test
    fun playfulUkElbowDownHashesToRealManifestKey() {
        // app/src/main/assets/voice/preset-playful/manifest.json: "uk__11f4n9r" -> "тягнися крізь мʼяч"
        val phrase = VoicePresetCatalog.phraseFor("preset-playful", FeedbackLang.UA, cue(DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_LOW))!!
        assertEquals("uk__11f4n9r", VoiceClipKeys.clipKey(VoicePresetCatalog.langCode(FeedbackLang.UA), phrase))
    }

    @Test
    fun strictEnElbowUpHashesToRealManifestKey() {
        // app/src/main/assets/voice/preset-strict/manifest.json: "en__8d7a1u" -> "bend the elbow"
        val phrase = VoicePresetCatalog.phraseFor("preset-strict", FeedbackLang.EN, cue(DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_HIGH))!!
        assertEquals("en__8d7a1u", VoiceClipKeys.clipKey(VoicePresetCatalog.langCode(FeedbackLang.EN), phrase))
    }

    @Test
    fun efficientUkPraiseHashesToRealManifestKey() {
        // app/src/main/assets/voice/preset-efficient/manifest.json: "uk__1hdpfsb" -> "добре"
        val phrase = VoicePresetCatalog.praise("preset-efficient", FeedbackLang.UA) { 2 }!! // EFFICIENT_UK.praise = ['чисто', 'так', 'добре']
        assertEquals("uk__1hdpfsb", VoiceClipKeys.clipKey(VoicePresetCatalog.langCode(FeedbackLang.UA), phrase))
    }
}
