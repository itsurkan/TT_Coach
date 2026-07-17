package com.ttcoachai.shared.drill

/**
 * Ports the three built-in voice-style phrase sets ("preset-playful",
 * "preset-efficient", "preset-strict") from poses_viewer/src/drill2d/voiceStyle.ts
 * (the source of truth) so the live pipeline ([LiveDrillSession] /
 * [DrillRepProcessor]) can speak the SAME phrases the web viewer uses,
 * hashing identically via [VoiceClipKeys] into the pre-recorded manifests
 * under `app/src/main/assets/voice/<styleId>/manifest.json`.
 *
 * Every phrase string below is copied VERBATIM from voiceStyle.ts — not
 * paraphrased, not "fixed", not retranslated — because [VoiceClipKeys.clipKey]
 * hashes the exact text; any edit here breaks the manifest lookup for that
 * phrase and silently falls back to live TTS.
 *
 * `phaseCues` (voiceStyle.ts's per-stroke-phase phrase overrides) are
 * intentionally NOT ported: live cues in this pipeline ([FeedbackCue]) carry
 * only a metric key + direction, no stroke-phase concept, so there is nothing
 * to key `phaseCues` off of here.
 *
 * Metric-key vocabulary check (see task report for full detail): the runtime
 * metric keys this pipeline actually produces are [DrillMetrics.ALL_KEYS]
 * (`elbow_angle`, `shoulder_angle`, `knee_bend`, `torso_lean`,
 * `shoulder_tilt`) — a strict subset of voiceStyle.ts's `MetricKey` union,
 * which additionally has `hip_flexion` (a metric this pipeline never emits,
 * so its phrases are ported here for completeness/future-proofing but
 * [phraseFor] can never be asked for it via a real [FeedbackCue]).
 */
object VoicePresetCatalog {

    private data class CuePhrases(val up: String, val down: String)

    private data class Preset(
        val cues: Map<String, CuePhrases>,
        val praise: List<String>
    )

    private const val METRIC_HIP_FLEXION = "hip_flexion"

    // ---- preset-playful (voiceStyle.ts PLAYFUL_EN / PLAYFUL_UK) ----

    private val PLAYFUL_EN = Preset(
        cues = mapOf(
            DrillMetrics.METRIC_ELBOW_ANGLE to CuePhrases("give the elbow a little bend", "reach through it"),
            DrillMetrics.METRIC_SHOULDER_ANGLE to CuePhrases("drop the shoulder a touch", "open the shoulder a bit more"),
            DrillMetrics.METRIC_KNEE_BEND to CuePhrases("sit into it, legs on", "ease up, stand a little taller"),
            DrillMetrics.METRIC_TORSO_LEAN to CuePhrases("stand a bit taller", "lean into the ball"),
            DrillMetrics.METRIC_SHOULDER_TILT to CuePhrases("level the shoulders", "level the shoulders"),
            METRIC_HIP_FLEXION to CuePhrases("ease the hips up a touch", "sink into the hips"),
        ),
        praise = listOf(
            "that's the shape!", "yes — that follow-through", "clean — do that again",
            "nice, really solid", "love it, keep going"
        )
    )

    private val PLAYFUL_UK = Preset(
        cues = mapOf(
            DrillMetrics.METRIC_ELBOW_ANGLE to CuePhrases("трохи зігни лікоть", "тягнися крізь мʼяч"),
            DrillMetrics.METRIC_SHOULDER_ANGLE to CuePhrases("ледь опусти плече", "трохи відкрий плече"),
            DrillMetrics.METRIC_KNEE_BEND to CuePhrases("присядь, працюй ногами", "трохи випрямись"),
            DrillMetrics.METRIC_TORSO_LEAN to CuePhrases("тримайся трохи рівніше", "нахились до мʼяча"),
            DrillMetrics.METRIC_SHOULDER_TILT to CuePhrases("вирівняй плечі", "вирівняй плечі"),
            METRIC_HIP_FLEXION to CuePhrases("трохи вище стегнами", "присядь у стегнах"),
        ),
        praise = listOf(
            "оце форма!", "так — оце завершення", "чисто — ще раз так",
            "гарно, дуже впевнено", "клас, продовжуй"
        )
    )

    // ---- preset-strict (voiceStyle.ts STRICT_EN / STRICT_UK) ----

    private val STRICT_EN = Preset(
        cues = mapOf(
            DrillMetrics.METRIC_ELBOW_ANGLE to CuePhrases("bend the elbow", "extend more"),
            DrillMetrics.METRIC_SHOULDER_ANGLE to CuePhrases("drop the shoulder", "open the shoulder"),
            DrillMetrics.METRIC_KNEE_BEND to CuePhrases("bend the knees", "stand taller"),
            DrillMetrics.METRIC_TORSO_LEAN to CuePhrases("stand taller", "lean in"),
            DrillMetrics.METRIC_SHOULDER_TILT to CuePhrases("level the shoulders", "level the shoulders"),
            METRIC_HIP_FLEXION to CuePhrases("stand tall", "hinge forward"),
        ),
        praise = listOf("that's the shape", "clean — repeat that", "correct")
    )

    private val STRICT_UK = Preset(
        cues = mapOf(
            DrillMetrics.METRIC_ELBOW_ANGLE to CuePhrases("зігни лікоть", "більше випрями руку"),
            DrillMetrics.METRIC_SHOULDER_ANGLE to CuePhrases("опусти плече", "відкрий плече"),
            DrillMetrics.METRIC_KNEE_BEND to CuePhrases("зігни коліна", "стань вище"),
            DrillMetrics.METRIC_TORSO_LEAN to CuePhrases("тримайся рівніше", "нахились уперед"),
            DrillMetrics.METRIC_SHOULDER_TILT to CuePhrases("вирівняй плечі", "вирівняй плечі"),
            METRIC_HIP_FLEXION to CuePhrases("вище", "нахились у стегнах"),
        ),
        praise = listOf("оце форма", "чисто — повтори", "правильно")
    )

    // ---- preset-efficient (voiceStyle.ts EFFICIENT_EN / EFFICIENT_UK) ----

    private val EFFICIENT_EN = Preset(
        cues = mapOf(
            DrillMetrics.METRIC_ELBOW_ANGLE to CuePhrases("bend elbow", "extend arm"),
            DrillMetrics.METRIC_SHOULDER_ANGLE to CuePhrases("drop shoulder", "open shoulder"),
            DrillMetrics.METRIC_KNEE_BEND to CuePhrases("bend knees", "stand taller"),
            DrillMetrics.METRIC_TORSO_LEAN to CuePhrases("taller", "lean in"),
            DrillMetrics.METRIC_SHOULDER_TILT to CuePhrases("level shoulders", "level shoulders"),
            METRIC_HIP_FLEXION to CuePhrases("hips up", "hinge"),
        ),
        praise = listOf("clean", "yes", "good")
    )

    private val EFFICIENT_UK = Preset(
        cues = mapOf(
            DrillMetrics.METRIC_ELBOW_ANGLE to CuePhrases("зігни лікоть", "випрями руку"),
            DrillMetrics.METRIC_SHOULDER_ANGLE to CuePhrases("опусти плече", "відкрий плече"),
            DrillMetrics.METRIC_KNEE_BEND to CuePhrases("зігни коліна", "вище"),
            DrillMetrics.METRIC_TORSO_LEAN to CuePhrases("вище", "нахились"),
            DrillMetrics.METRIC_SHOULDER_TILT to CuePhrases("рівніше плечі", "рівніше плечі"),
            METRIC_HIP_FLEXION to CuePhrases("вище", "нахились"),
        ),
        praise = listOf("чисто", "так", "добре")
    )

    private const val STYLE_PLAYFUL = "preset-playful"
    private const val STYLE_STRICT = "preset-strict"
    private const val STYLE_EFFICIENT = "preset-efficient"

    private fun presetFor(styleId: String, lang: FeedbackLang): Preset? = when (styleId) {
        STYLE_PLAYFUL -> if (lang == FeedbackLang.EN) PLAYFUL_EN else PLAYFUL_UK
        STYLE_STRICT -> if (lang == FeedbackLang.EN) STRICT_EN else STRICT_UK
        STYLE_EFFICIENT -> if (lang == FeedbackLang.EN) EFFICIENT_EN else EFFICIENT_UK
        else -> null
    }

    /**
     * The verbatim up/down phrase for [cue]'s metric in the given preset+lang, or
     * null when [styleId] is unknown or the metric has no entry in this preset
     * (e.g. a metric [DrillFeedbackEngine] never emits, or — symmetrically — a
     * voiceStyle.ts key this pipeline doesn't produce, which is expected, not a bug).
     */
    fun phraseFor(styleId: String, lang: FeedbackLang, cue: FeedbackCue): String? {
        val preset = presetFor(styleId, lang) ?: return null
        val phrases = preset.cues[cue.metricKey] ?: return null
        return when (cue.direction) {
            CueDirection.TOO_HIGH -> phrases.up
            CueDirection.TOO_LOW -> phrases.down
        }
    }

    /**
     * A phrase from the preset+lang's praise pool, selected via the injected
     * [pickIndex] (same rotation/variety-selection convention as
     * [com.ttcoachai.shared.feedback.LiveFeedbackFormatter]'s `pickIndex: (Int) -> Int`
     * parameter — the caller supplies e.g. a rotating counter or `Random::nextInt`).
     * Null when [styleId] is unknown.
     */
    fun praise(styleId: String, lang: FeedbackLang, pickIndex: (Int) -> Int): String? {
        val preset = presetFor(styleId, lang) ?: return null
        val pool = preset.praise
        return pool[pickIndex(pool.size)]
    }

    /** "en" / "uk" (this pipeline's [FeedbackLang.UA] maps to voiceStyle.ts's "uk"). */
    fun langCode(lang: FeedbackLang): String = if (lang == FeedbackLang.EN) "en" else "uk"
}
