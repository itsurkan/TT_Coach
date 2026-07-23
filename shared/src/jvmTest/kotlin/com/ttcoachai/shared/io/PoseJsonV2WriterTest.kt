package com.ttcoachai.shared.io

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Topology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoseJsonV2WriterTest {

    @Test
    fun round4MatchesPythonRoundPlusJsonDumps() {
        val cases = listOf(
            0f to "0.0",
            1f to "1.0",
            -1f to "-1.0",
            0.5999f to "0.5999",
            0.896f to "0.896",       // trailing zero trimmed — not "0.8960"
            -0.0125f to "-0.0125",
            0.99995f to "1.0",       // rounds up across the integer boundary
            -0.99995f to "-1.0",
            0.1f to "0.1",
        )
        for ((input, expected) in cases) {
            assertEquals(expected, PoseJsonV2Writer.round4(input), "round4($input)")
        }
    }

    @Test
    fun landmarkFieldOrderIsIndexXYScore() {
        // Field-order tripwire: PoseJsonV2Parser.LANDMARK_RE matches this exact literal order.
        val frame = PoseFrame2D(frameIndex = 0, timestampMs = 0L, keypoints = listOf(Keypoint2D(0.5f, 0.25f, 0.9f)))
        val line = PoseJsonV2Writer.frameLine(frame, isFirst = true)
        assertTrue(
            line.contains("\"index\":0,\"x\":0.5,\"y\":0.25,\"score\":0.9"),
            "expected literal index,x,y,score order, got: $line"
        )
    }

    @Test
    fun roundTripsVideo2FixtureThroughParser() {
        val original = TestFixturesV2.loadVideo2Rtm()
        val sb = StringBuilder()
        sb.append(
            PoseJsonV2Writer.header(
                topology = original.topology,
                model = original.model,
                videoName = original.videoName,
                intervalMs = original.intervalMs,
                totalFrames = original.totalFrames,
                videoDurationMs = original.videoDurationMs,
                videoWidth = original.videoWidth,
                videoHeight = original.videoHeight
            )
        )
        original.frames.forEachIndexed { i, frame ->
            sb.append(PoseJsonV2Writer.frameLine(frame, isFirst = i == 0))
        }
        sb.append(PoseJsonV2Writer.footer())

        val reparsed = PoseJsonV2Parser.parse(sb.toString())
        assertEquals(original, reparsed)
    }

    @Test
    fun headerAndFrameWrapperOutputRemainCompact() {
        // Compactness (no spaces after : or ,) is only asserted for landmarks in the existing tests.
        // This test ensures the header and frame wrapper do not regress to pretty-printed JSON.
        val headerOutput = PoseJsonV2Writer.header(
            topology = Topology.COCO17,
            model = "rtmpose-m",
            videoName = "test.mp4",
            intervalMs = 20,
            totalFrames = 100,
            videoDurationMs = 2000,
            videoWidth = 1280,
            videoHeight = 720
        )
        assertTrue(
            headerOutput.contains("\"schemaVersion\":2,\"topology\":"),
            "header must be compact (no space after ':' or ','), got: $headerOutput"
        )

        val frame = PoseFrame2D(frameIndex = 0, timestampMs = 0L, keypoints = listOf(Keypoint2D(0.5f, 0.25f, 0.9f)))
        val frameLineOutput = PoseJsonV2Writer.frameLine(frame, isFirst = true)
        assertTrue(
            frameLineOutput.contains("\"frameIndex\":0,\"timestampMs\":0,\"landmarks\":["),
            "frameLine must be compact (no space after ':' or ','), got: $frameLineOutput"
        )
    }
}
