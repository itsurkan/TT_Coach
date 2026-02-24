/*
 * AI Coach for Table Tennis
 * TestFixtures — JVM test utility to load and parse JSON fixture files into List<PoseFrame>
 *
 * Uses ClassLoader resource loading (JVM-only, hence lives in jvmTest source set).
 * Resources are in commonTest/resources/fixtures/ which is on the JVM test classpath.
 */

package com.ttcoachai.shared

import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.PoseFrame

/**
 * Loads and parses pose fixture JSON files into List<PoseFrame>.
 * Uses a simple string-based parser for the known fixture format — no external JSON library needed.
 */
object TestFixtures {

    fun loadForehandDrive(): List<PoseFrame> = parseFrames(loadResource("fixtures/forehand_drive.json"))

    fun loadForehandDriveWrong(): List<PoseFrame> = parseFrames(loadResource("fixtures/forehand_drive_wrong.json"))

    fun loadForehandDrive2(): List<PoseFrame> = parseFrames(loadResource("fixtures/forehand_drive2.json"))

    // ── Resource loading ──────────────────────────────────────────────────────

    private fun loadResource(path: String): String {
        val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
            ?: ClassLoader.getSystemResourceAsStream(path)
            ?: throw IllegalStateException("Test resource not found on classpath: $path")
        return stream.bufferedReader(Charsets.UTF_8).readText()
    }

    // ── JSON parsing ──────────────────────────────────────────────────────────

    private val FRAME_INDEX_RE = Regex(""""frameIndex"\s*:\s*(\d+)""")
    private val TIMESTAMP_RE   = Regex(""""timestampMs"\s*:\s*(\d+)""")
    private val LANDMARK_RE    = Regex(
        """"x"\s*:\s*([-\d.Ee]+)\s*,\s*"y"\s*:\s*([-\d.Ee]+)\s*,\s*"z"\s*:\s*([-\d.Ee]+)\s*,\s*"visibility"\s*:\s*([-\d.Ee]+)\s*,\s*"presence"\s*:\s*([-\d.Ee]+)"""
    )

    private fun parseFrames(json: String): List<PoseFrame> {
        val frames = mutableListOf<PoseFrame>()

        // Collect all "frameIndex" match positions
        val frameIndexMatches = FRAME_INDEX_RE.findAll(json).toList()

        for ((idx, frameMatch) in frameIndexMatches.withIndex()) {
            val frameIndex = frameMatch.groupValues[1].toInt()
            val sectionStart = frameMatch.range.first
            val sectionEnd = if (idx + 1 < frameIndexMatches.size) {
                frameIndexMatches[idx + 1].range.first
            } else {
                json.length
            }
            val section = json.substring(sectionStart, sectionEnd)

            val timestampMs = TIMESTAMP_RE.find(section)?.groupValues?.get(1)?.toLong() ?: 0L

            val landmarksKeyPos = section.indexOf("\"landmarks\"")
            val landmarks = if (landmarksKeyPos >= 0) {
                parseLandmarks(section.substring(landmarksKeyPos))
            } else {
                emptyList()
            }

            frames.add(PoseFrame(frameIndex = frameIndex, timestampMs = timestampMs, landmarks = landmarks))
        }

        return frames
    }

    private fun parseLandmarks(landmarksSection: String): List<Landmark3D> {
        return LANDMARK_RE.findAll(landmarksSection).map { match ->
            val (x, y, z, visibility, presence) = match.destructured
            Landmark3D(
                x = x.toFloat(),
                y = y.toFloat(),
                z = z.toFloat(),
                visibility = visibility.toFloat(),
                presence = presence.toFloat()
            )
        }.toList()
    }
}
