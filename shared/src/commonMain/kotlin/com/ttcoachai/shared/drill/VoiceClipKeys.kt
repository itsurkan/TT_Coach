package com.ttcoachai.shared.drill

/**
 * Deterministic clip-key hashing, ported byte-identically from
 * poses_viewer/src/drill2d/voiceClips.ts (the source of truth). The key is a
 * cache lookup into pre-recorded voice-clip manifests (`app/src/main/assets/voice/
 * <styleId>/manifest.json`) that are generated once (offline, via the web
 * tooling) and consumed both by the web viewer and by this shared/app
 * pipeline. If the hash here drifted from voiceClips.ts, a phrase ported into
 * [VoicePresetCatalog] would produce a key that misses every real manifest
 * entry — silently falling back to live TTS instead of the recorded clip.
 * Hence: same algorithm, same normalization, same base, verified against real
 * manifest entries in VoiceClipKeysTest.
 */
object VoiceClipKeys {

    /**
     * Canonical form the hash is computed over: trim, lowercase, collapse
     * internal whitespace runs to a single space. Mirrors
     * `normalizeText` in voiceClips.ts exactly (`text.trim().toLowerCase().replace(/\s+/g, ' ')`).
     */
    fun normalizeText(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")

    /**
     * djb2 (xor variant), unsigned per step, base-36. Mirrors voiceClips.ts's
     * `hashText`:
     * ```
     * h=5381; for each char: h=((h*33)^code)>>>0; return h.toString(36)
     * ```
     * JS's `>>> 0` forces an unsigned 32-bit result after each step; we get
     * the same effect by carrying the accumulator in a [Long] and masking
     * with `0xFFFFFFFFL` after every multiply-xor. `Long.toString(36)` uses
     * lowercase a-z for digits above 9, matching JS `Number.prototype.toString(36)`.
     */
    fun hashText(text: String): String {
        var h = 5381L
        for (ch in text) {
            h = ((h * 33L) xor ch.code.toLong()) and 0xFFFFFFFFL
        }
        return h.toString(36)
    }

    /** `"${lang}__${hashText(normalizeText(text))}"` — mirrors voiceClips.ts's `clipKey`. */
    fun clipKey(lang: String, text: String): String = "${lang}__${hashText(normalizeText(text))}"
}
