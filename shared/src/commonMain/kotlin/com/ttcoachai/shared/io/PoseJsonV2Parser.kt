package com.ttcoachai.shared.io

import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.Topology

/** Thrown when input JSON is not a valid schema-v2 pose export. */
class PoseSchemaException(message: String) : IllegalArgumentException(message)

/**
 * Parser for pose JSON schema v2 (docs/pose_json_schema_v2.md), the format written by
 * scripts/poses/export_poses_rtmpose.py. Pure Kotlin, no dependencies (shared-module
 * convention). Regex-anchored extraction is safe here because the exporter controls
 * the format; all structural assumptions are validated explicitly.
 */
object PoseJsonV2Parser {

    private val SCHEMA_VERSION_RE = Regex(""""schemaVersion"\s*:\s*(\d+)""")
    private val TOPOLOGY_RE = Regex(""""topology"\s*:\s*"([^"]+)"""")
    private val MODEL_RE = Regex(""""model"\s*:\s*"([^"]+)"""")
    private val VIDEO_NAME_RE = Regex(""""videoName"\s*:\s*"([^"]+)"""")
    private val INTERVAL_RE = Regex(""""intervalMs"\s*:\s*(\d+)""")
    private val TOTAL_FRAMES_RE = Regex(""""totalFrames"\s*:\s*(\d+)""")
    private val DURATION_RE = Regex(""""videoDurationMs"\s*:\s*(\d+)""")
    private val WIDTH_RE = Regex(""""videoWidth"\s*:\s*(\d+)""")
    private val HEIGHT_RE = Regex(""""videoHeight"\s*:\s*(\d+)""")
    private val FRAME_INDEX_RE = Regex(""""frameIndex"\s*:\s*(\d+)""")
    private val TIMESTAMP_RE = Regex(""""timestampMs"\s*:\s*(\d+)""")
    private val LANDMARK_RE = Regex(
        """"index"\s*:\s*(\d+)\s*,\s*"x"\s*:\s*([-\d.Ee]+)\s*,\s*"y"\s*:\s*([-\d.Ee]+)\s*,\s*"score"\s*:\s*([-\d.Ee]+)"""
    )

    fun parse(json: String): PoseSequence2D {
        val version = SCHEMA_VERSION_RE.find(json)?.groupValues?.get(1)?.toInt()
            ?: throw PoseSchemaException(
                "Missing schemaVersion — this looks like a legacy v1 (MediaPipe-33) export, " +
                    "which PoseJsonV2Parser does not read"
            )
        if (version != 2) throw PoseSchemaException("Unsupported schemaVersion $version, expected 2")

        val topologyName = TOPOLOGY_RE.find(json)?.groupValues?.get(1)
            ?: throw PoseSchemaException("Missing topology field")
        val topology = Topology.fromJsonName(topologyName)
            ?: throw PoseSchemaException(
                "Unknown topology \"$topologyName\", expected one of: " +
                    Topology.entries.joinToString { it.jsonName }
            )

        val intervalMs = requireLong(json, INTERVAL_RE, "intervalMs")
        if (intervalMs <= 0) throw PoseSchemaException("intervalMs must be > 0, got $intervalMs")
        val videoWidth = requireLong(json, WIDTH_RE, "videoWidth").toInt()
        val videoHeight = requireLong(json, HEIGHT_RE, "videoHeight").toInt()
        if (videoWidth <= 0 || videoHeight <= 0) {
            throw PoseSchemaException("videoWidth/videoHeight must be > 0, got ${videoWidth}x$videoHeight")
        }

        val frames = parseFrames(json, topology)

        return PoseSequence2D(
            topology = topology,
            model = MODEL_RE.find(json)?.groupValues?.get(1) ?: "",
            videoName = VIDEO_NAME_RE.find(json)?.groupValues?.get(1) ?: "",
            intervalMs = intervalMs,
            totalFrames = TOTAL_FRAMES_RE.find(json)?.groupValues?.get(1)?.toInt() ?: frames.size,
            videoDurationMs = DURATION_RE.find(json)?.groupValues?.get(1)?.toLong() ?: 0L,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            frames = frames
        )
    }

    private fun requireLong(json: String, re: Regex, field: String): Long =
        re.find(json)?.groupValues?.get(1)?.toLong()
            ?: throw PoseSchemaException("Missing required field $field")

    private fun parseFrames(json: String, topology: Topology): List<PoseFrame2D> {
        val frames = mutableListOf<PoseFrame2D>()
        val anchors = FRAME_INDEX_RE.findAll(json).toList()
        for ((i, anchor) in anchors.withIndex()) {
            val sectionEnd = if (i + 1 < anchors.size) anchors[i + 1].range.first else json.length
            val section = json.substring(anchor.range.first, sectionEnd)
            val frameIndex = anchor.groupValues[1].toInt()
            val timestampMs = TIMESTAMP_RE.find(section)?.groupValues?.get(1)?.toLong() ?: 0L

            val keypoints = LANDMARK_RE.findAll(section).map { m ->
                Keypoint2D(
                    x = m.groupValues[2].toFloat(),
                    y = m.groupValues[3].toFloat(),
                    score = m.groupValues[4].toFloat()
                )
            }.toList()

            if (keypoints.isNotEmpty() && keypoints.size != topology.keypointCount) {
                throw PoseSchemaException(
                    "Frame $frameIndex has ${keypoints.size} landmarks, " +
                        "expected ${topology.keypointCount} for ${topology.jsonName} (or 0 for no person)"
                )
            }

            frames.add(PoseFrame2D(frameIndex, timestampMs, keypoints))
        }
        return frames
    }
}
