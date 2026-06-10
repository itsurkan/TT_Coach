package com.ttcoachai.shared.io

import com.ttcoachai.shared.models.Topology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PoseJsonV2ParserTest {

    private fun landmarksJson(n: Int): String =
        (0 until n).joinToString(",") { """{ "index": $it, "x": 0.5, "y": 0.25, "score": 0.9 }""" }

    private fun v2Json(
        topology: String = "coco17",
        landmarkCount: Int = 17,
        secondFrameLandmarks: String = ""
    ): String = """
        {
          "schemaVersion": 2,
          "topology": "$topology",
          "model": "rtmpose-m",
          "videoName": "clip.mp4",
          "intervalMs": 100,
          "totalFrames": 2,
          "videoDurationMs": 200,
          "videoWidth": 712,
          "videoHeight": 1280,
          "exportTimestamp": 1781085099336,
          "frames": [
            { "frameIndex": 0, "timestampMs": 0, "landmarks": [ ${landmarksJson(landmarkCount)} ] },
            { "frameIndex": 1, "timestampMs": 100, "landmarks": [ $secondFrameLandmarks ] }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesCoco17HappyPath() {
        val seq = PoseJsonV2Parser.parse(v2Json())
        assertEquals(Topology.COCO17, seq.topology)
        assertEquals("rtmpose-m", seq.model)
        assertEquals("clip.mp4", seq.videoName)
        assertEquals(100L, seq.intervalMs)
        assertEquals(2, seq.totalFrames)
        assertEquals(712, seq.videoWidth)
        assertEquals(1280, seq.videoHeight)
        assertEquals(2, seq.frames.size)
        assertEquals(17, seq.frames[0].keypoints.size)
        assertEquals(0.5f, seq.frames[0].keypoints[3].x, 1e-6f)
        assertEquals(0.25f, seq.frames[0].keypoints[3].y, 1e-6f)
        assertEquals(0.9f, seq.frames[0].keypoints[3].score, 1e-6f)
        assertEquals(100L, seq.frames[1].timestampMs)
    }

    @Test
    fun emptyLandmarksFrameIsValid() {
        val seq = PoseJsonV2Parser.parse(v2Json())
        assertTrue(seq.frames[1].keypoints.isEmpty(), "no-person frame must parse to empty keypoints")
    }

    @Test
    fun parsesHalpe26() {
        val seq = PoseJsonV2Parser.parse(v2Json(topology = "halpe26", landmarkCount = 26))
        assertEquals(Topology.HALPE26, seq.topology)
        assertEquals(26, seq.frames[0].keypoints.size)
    }

    @Test
    fun rejectsLegacyV1WithoutSchemaVersion() {
        val legacy = """{ "frames": [ { "frameIndex": 0, "timestampMs": 0, "landmarks": [] } ] }"""
        val ex = assertFailsWith<PoseSchemaException> { PoseJsonV2Parser.parse(legacy) }
        assertTrue(ex.message!!.contains("legacy"), "error must name the legacy v1 case: ${ex.message}")
    }

    @Test
    fun rejectsUnknownTopology() {
        assertFailsWith<PoseSchemaException> {
            PoseJsonV2Parser.parse(v2Json(topology = "mediapipe33"))
        }
    }

    @Test
    fun rejectsWrongLandmarkCount() {
        val ex = assertFailsWith<PoseSchemaException> {
            PoseJsonV2Parser.parse(v2Json(landmarkCount = 12))
        }
        assertTrue(ex.message!!.contains("12"), "error must report the bad count: ${ex.message}")
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        assertFailsWith<PoseSchemaException> {
            PoseJsonV2Parser.parse(v2Json().replace("\"schemaVersion\": 2", "\"schemaVersion\": 3"))
        }
    }

    @Test
    fun rejectsReorderedLandmarkFields() {
        // Field-order drift must throw, never silently parse as "no person" frames
        val reordered = (0 until 17).joinToString(",") {
            """{ "index": $it, "y": 0.25, "x": 0.5, "score": 0.9 }"""
        }
        val json = v2Json().replace(landmarksJson(17), reordered)
        val ex = assertFailsWith<PoseSchemaException> { PoseJsonV2Parser.parse(json) }
        assertTrue(ex.message!!.contains("drifted"), "must report format drift: ${ex.message}")
    }

    @Test
    fun parsesRealExporterMultilineFormat() {
        // json.dump(indent=2) puts every field on its own line — \s* must absorb it
        val json = """
            {
              "schemaVersion": 2,
              "topology": "coco17",
              "model": "rtmpose-m",
              "videoName": "clip.mp4",
              "intervalMs": 100,
              "totalFrames": 1,
              "videoDurationMs": 100,
              "videoWidth": 712,
              "videoHeight": 1280,
              "frames": [
                {
                  "frameIndex": 0,
                  "timestampMs": 0,
                  "landmarks": [
            ${(0 until 17).joinToString(",\n") { """        {
                      "index": $it,
                      "x": 0.5,
                      "y": 0.25,
                      "score": 0.9
                    }""" }}
                  ]
                }
              ]
            }
        """.trimIndent()
        val seq = PoseJsonV2Parser.parse(json)
        assertEquals(17, seq.frames[0].keypoints.size)
    }

    @Test
    fun parsesNegativeAndExponentCoordinates() {
        // RTMPose can place keypoints slightly off-frame
        val custom = landmarksJson(17).replaceFirst(""""x": 0.5""", """"x": -1.25E-2""")
        val json = v2Json().replace(landmarksJson(17), custom)
        val seq = PoseJsonV2Parser.parse(json)
        assertEquals(-0.0125f, seq.frames[0].keypoints[0].x, 1e-6f)
    }
}
