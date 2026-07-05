package com.ttcoachai.shared.drill

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden tests pinning [VoiceClipKeys] against real entries from
 * app/src/main/assets/voice/preset-playful/manifest.json (generated offline
 * from poses_viewer/src/drill2d/voiceClips.ts's hash). If these ever fail, the
 * Kotlin port has drifted from the JS reference and every clip lookup in the
 * app would silently miss the manifest and fall back to live TTS.
 */
class VoiceClipKeysTest {

    // ---- golden hash/key pairs, from app/src/main/assets/voice/preset-playful/manifest.json ----

    @Test
    fun ukGoldenClipKeyElbowUp() {
        // manifest key "uk__g3lqms" -> { "text": "трохи зігни лікоть", "lang": "uk" }
        assertEquals("uk__g3lqms", VoiceClipKeys.clipKey("uk", "трохи зігни лікоть"))
    }

    @Test
    fun ukGoldenClipKeyShoulderUp() {
        // manifest key "uk__2sjuuh" -> { "text": "ледь опусти плече", "lang": "uk" }
        assertEquals("uk__2sjuuh", VoiceClipKeys.clipKey("uk", "ледь опусти плече"))
    }

    @Test
    fun ukGoldenClipKeyPraise() {
        // manifest key "uk__1lgdqvz" -> { "text": "оце форма!", "lang": "uk" }
        assertEquals("uk__1lgdqvz", VoiceClipKeys.clipKey("uk", "оце форма!"))
    }

    @Test
    fun enGoldenClipKeyElbowUp() {
        // manifest key "en__fn2ioi" -> { "text": "give the elbow a little bend", "lang": "en" }
        assertEquals("en__fn2ioi", VoiceClipKeys.clipKey("en", "give the elbow a little bend"))
    }

    @Test
    fun enGoldenClipKeyElbowDown() {
        // manifest key "en__z72eny" -> { "text": "reach through it", "lang": "en" }
        assertEquals("en__z72eny", VoiceClipKeys.clipKey("en", "reach through it"))
    }

    @Test
    fun enGoldenClipKeyTorsoLeanDown() {
        // manifest key "en__c7duxx" -> { "text": "lean into the ball", "lang": "en" }
        assertEquals("en__c7duxx", VoiceClipKeys.clipKey("en", "lean into the ball"))
    }

    // ---- normalizeText ----

    @Test
    fun normalizeTextLowercasesMixedCase() {
        assertEquals("give the elbow a little bend", VoiceClipKeys.normalizeText("Give The Elbow A Little Bend"))
    }

    @Test
    fun normalizeTextCollapsesInternalWhitespaceRuns() {
        assertEquals("stand a bit taller", VoiceClipKeys.normalizeText("stand   a\tbit\n\ntaller"))
    }

    @Test
    fun normalizeTextTrimsLeadingAndTrailingWhitespace() {
        assertEquals("level the shoulders", VoiceClipKeys.normalizeText("   level the shoulders   \n"))
    }

    @Test
    fun normalizeTextIsIdempotent() {
        val once = VoiceClipKeys.normalizeText("  Mixed   CASE  \t text ")
        assertEquals(once, VoiceClipKeys.normalizeText(once))
    }
}
