package com.ttcoachai.pose

import android.content.Context
import android.util.Log
import com.ttcoachai.shared.drill.VoiceClipKeys
import org.json.JSONObject

/**
 * Parsed `voice/<styleId>/manifest.json` from app assets — the pre-recorded
 * clip index for one voice-style preset (see [VoicePresetCatalog] /
 * [VoiceClipKeys] in `shared`, and `poses_viewer/src/drill2d/voiceClips.ts`,
 * the source-of-truth TS sibling this class mirrors).
 *
 * Schema:
 * ```json
 * {
 *   "styleId": "preset-playful",
 *   "clips": {
 *     "<clipKey>": { "file": "uk/uk__g3lqms.mp3", "durationMs": 812, "text": "...", "lang": "uk" }
 *   }
 * }
 * ```
 *
 * Uses `org.json` (not kotlinx-serialization) — the app-module convention,
 * see [com.ttcoachai.db.BaselineConverters].
 */
class VoiceClipManifest private constructor(
    val styleId: String,
    private val clips: Map<String, Entry>,
) {

    data class Entry(
        val file: String,
        val durationMs: Long,
        val text: String,
        val lang: String,
    )

    /**
     * Looks up the clip for (langCode, text) if present AND fresh. Freshness =
     * the stored text still normalizes to the same string as [text] — guards
     * against hash collisions and any future key-scheme drift. Mirrors
     * `lookupClip` in voiceClips.ts.
     */
    fun lookup(langCode: String, text: String): Entry? {
        val key = VoiceClipKeys.clipKey(langCode, text)
        val entry = clips[key] ?: return null
        if (VoiceClipKeys.normalizeText(entry.text) != VoiceClipKeys.normalizeText(text)) return null
        return entry
    }

    /** Asset path for [entry], relative to the assets root — pass to `context.assets.openFd(...)`. */
    fun assetPath(entry: Entry): String = "voice/$styleId/${entry.file}"

    companion object {
        private const val TAG = "VoiceClipManifest"

        /**
         * Reads and parses `voice/<styleId>/manifest.json` from [context]'s assets.
         * Returns null (logging the cause) on any failure: missing style directory,
         * missing manifest file, or malformed JSON — callers treat null as
         * "no recorded clips for this style", falling back to live TTS entirely.
         */
        fun load(context: Context, styleId: String): VoiceClipManifest? {
            return try {
                val json = context.assets.open("voice/$styleId/manifest.json")
                    .bufferedReader()
                    .use { it.readText() }
                parse(styleId, json)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load voice manifest for styleId=$styleId", e)
                null
            }
        }

        /** Parses manifest JSON already read into a string. Extracted for testability. */
        fun parse(styleId: String, json: String): VoiceClipManifest? {
            return try {
                val root = JSONObject(json)
                val clipsObj = root.getJSONObject("clips")
                val clips = LinkedHashMap<String, Entry>(clipsObj.length())
                for (key in clipsObj.keys()) {
                    val entryObj = clipsObj.getJSONObject(key)
                    clips[key] = Entry(
                        file = entryObj.getString("file"),
                        durationMs = entryObj.getLong("durationMs"),
                        text = entryObj.getString("text"),
                        lang = entryObj.getString("lang"),
                    )
                }
                VoiceClipManifest(styleId = root.optString("styleId", styleId), clips = clips)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse voice manifest for styleId=$styleId", e)
                null
            }
        }
    }
}
