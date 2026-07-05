package com.ttcoachai.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceClipManifestTest {

    private val sampleJson = """
        {
          "styleId": "preset-playful",
          "clips": {
            "uk__g3lqms": {
              "file": "uk/uk__g3lqms.mp3",
              "durationMs": 812,
              "text": "трохи зігни лікоть",
              "lang": "uk"
            },
            "en__fn2ioi": {
              "file": "en/en__fn2ioi.mp3",
              "durationMs": 640,
              "text": "give the elbow a little bend",
              "lang": "en"
            }
          }
        }
    """.trimIndent()

    @Test
    fun parse_validJson_returnsManifestWithStyleId() {
        val manifest = VoiceClipManifest.parse("preset-playful", sampleJson)
        assertEquals("preset-playful", manifest?.styleId)
    }

    @Test
    fun lookup_freshEntry_returnsEntry() {
        val manifest = VoiceClipManifest.parse("preset-playful", sampleJson)
        val entry = manifest?.lookup("uk", "трохи зігни лікоть")
        assertEquals("uk/uk__g3lqms.mp3", entry?.file)
        assertEquals(812L, entry?.durationMs)
    }

    @Test
    fun lookup_normalizedTextStillMatches() {
        val manifest = VoiceClipManifest.parse("preset-playful", sampleJson)
        // extra whitespace + different case should still normalize to the same key/text
        val entry = manifest?.lookup("uk", "  Трохи Зігни Лікоть  ".lowercase())
        assertEquals("uk/uk__g3lqms.mp3", entry?.file)
    }

    @Test
    fun lookup_staleText_returnsNull() {
        val manifest = VoiceClipManifest.parse("preset-playful", sampleJson)
        // Different text won't hash to a key present in the manifest.
        val entry = manifest?.lookup("uk", "щось зовсім інше")
        assertNull(entry)
    }

    @Test
    fun lookup_missingLang_returnsNull() {
        val manifest = VoiceClipManifest.parse("preset-playful", sampleJson)
        val entry = manifest?.lookup("en", "трохи зігни лікоть")
        assertNull(entry)
    }

    @Test
    fun assetPath_buildsStyleScopedPath() {
        val manifest = VoiceClipManifest.parse("preset-playful", sampleJson)
        val entry = manifest?.lookup("uk", "трохи зігни лікоть")
        val path = entry?.let { manifest.assetPath(it) }
        assertEquals("voice/preset-playful/uk/uk__g3lqms.mp3", path)
    }

    @Test
    fun parse_malformedJson_returnsNull() {
        val manifest = VoiceClipManifest.parse("preset-playful", "not valid json")
        assertNull(manifest)
    }

    @Test
    fun parse_missingClipsKey_returnsNull() {
        val manifest = VoiceClipManifest.parse("preset-playful", """{"styleId":"preset-playful"}""")
        assertNull(manifest)
    }
}
