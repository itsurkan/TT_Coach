package com.ttcoachai.services

import com.ttcoachai.shared.models.TechniqueErrors
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Guards against missing pre-recorded voice-clip assets.
 *
 * [FeedbackGenerator.playFeedbackAudio] looks up `res/raw`(EN)/`res/raw-uk`(UK)
 * clips named `short_<key>`/`<key>_full` for every `error_*` base key declared
 * in [TechniqueErrors], via `Resources.getIdentifier`. When a clip is missing,
 * `getIdentifier` returns 0 and live voice feedback silently falls back to a
 * beep instead of speaking the correction — so a missing asset is a silent
 * regression at runtime, not a crash. This test enumerates the keys via
 * reflection (so any future key is automatically covered) and fails loudly,
 * listing every missing file, if any clip is absent.
 */
class VoiceClipAssetsTest {

    /** Same base-key naming convention as [com.ttcoachai.shared.feedback.VoiceClipSelector.clipResourceName]. */
    private fun shortName(key: String) = "short_$key.mp3"
    private fun fullName(key: String) = "${key}_full.mp3"

    @Test
    fun `every TechniqueErrors key has short and full voice clips in raw and raw-uk`() {
        // Reflect the base keys dynamically off TechniqueErrors so any newly added
        // error key is automatically covered without touching this test.
        val keys = TechniqueErrors::class.java.declaredFields
            .filter { field ->
                Modifier.isStatic(field.modifiers) &&
                    Modifier.isFinal(field.modifiers) &&
                    field.type == String::class.java
            }
            .map { field ->
                field.isAccessible = true
                field.get(null) as String
            }

        assertTrue(
            "Expected reflection to find at least 11 TechniqueErrors keys but found ${keys.size}: $keys. " +
                "The enumeration itself may be broken (e.g. field filter no longer matches).",
            keys.size >= 11
        )

        // Gradle's test working dir is the app module dir.
        val rawDir = File("src/main/res/raw")
        val rawUkDir = File("src/main/res/raw-uk")
        assertTrue(
            "Expected res/raw directory at ${rawDir.absolutePath} to exist — " +
                "test working directory may not be the app module dir as expected.",
            rawDir.isDirectory
        )
        assertTrue(
            "Expected res/raw-uk directory at ${rawUkDir.absolutePath} to exist — " +
                "test working directory may not be the app module dir as expected.",
            rawUkDir.isDirectory
        )

        val missing = mutableListOf<String>()
        for (dir in listOf(rawDir, rawUkDir)) {
            for (key in keys) {
                for (fileName in listOf(shortName(key), fullName(key))) {
                    val file = File(dir, fileName)
                    if (!file.isFile) {
                        missing.add("${dir.name}/$fileName")
                    }
                }
            }
        }

        if (missing.isNotEmpty()) {
            fail(
                "Missing voice-clip asset(s) for TechniqueErrors keys — live voice feedback " +
                    "silently degrades to a beep when Resources.getIdentifier finds no matching " +
                    "resource (FeedbackGenerator.playFeedbackAudio falls back to ToneGenerator).\n" +
                    "Missing files:\n" + missing.joinToString("\n")
            )
        }
    }
}
