package com.ttcoachai.shared

import com.ttcoachai.shared.io.PoseJsonV2Parser
import com.ttcoachai.shared.models.PoseSequence2D

/** Loads schema-v2 RTMPose fixtures from commonTest resources (JVM classpath). */
object TestFixturesV2 {

    fun loadAndriiRtm(): PoseSequence2D = parse("fixtures/andrii_1_rtm.json")

    fun loadVideo2Rtm(): PoseSequence2D = parse("fixtures/video_2_rtm.json")

    fun loadVideo4Rtm(): PoseSequence2D = parse("fixtures/video_4_rtm.json")

    private fun parse(path: String): PoseSequence2D = PoseJsonV2Parser.parse(loadResource(path))

    private fun loadResource(path: String): String {
        val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
            ?: ClassLoader.getSystemResourceAsStream(path)
            ?: throw IllegalStateException("Test resource not found on classpath: $path")
        return stream.bufferedReader(Charsets.UTF_8).readText()
    }
}
