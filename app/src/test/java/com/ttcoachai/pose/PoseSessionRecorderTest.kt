package com.ttcoachai.pose

import com.ttcoachai.shared.io.PoseJsonV2Parser
import com.ttcoachai.shared.models.Keypoint2D
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.GZIPInputStream

class PoseSessionRecorderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun coco17Frame(seed: Float = 0.5f): List<Keypoint2D> =
        (0 until 17).map { Keypoint2D(x = seed, y = seed, score = 0.9f) }

    private fun gunzip(file: File): String =
        GZIPInputStream(file.inputStream()).use { it.bufferedReader(Charsets.UTF_8).readText() }

    @Test
    fun framesWrittenThenFinishProducesValidFile() = runBlocking {
        val recorder = PoseSessionRecorder(tempFolder.newFolder())
        recorder.start(videoWidth = 640, videoHeight = 480)
        for (i in 0 until 5) {
            recorder.onFrame(coco17Frame(), timestampMs = i * 20L)
        }
        val finalFile = recorder.finish()
        requireNotNull(finalFile)
        assertTrue(finalFile.name.endsWith(".json.gz"))
        val seq = PoseJsonV2Parser.parse(gunzip(finalFile))
        assertEquals(5, seq.totalFrames)
        assertEquals(5, seq.frames.size)
        assertEquals(640, seq.videoWidth)
        assertEquals(480, seq.videoHeight)
    }

    @Test
    fun capStopsAcceptingFramesButFinishStillWorks() = runBlocking {
        val recorder = PoseSessionRecorder(tempFolder.newFolder())
        recorder.start(videoWidth = 640, videoHeight = 480)
        for (i in 0 until PoseSessionRecorder.MAX_FRAMES + 5) {
            recorder.onFrame(coco17Frame(), timestampMs = i * 20L)
        }
        val finalFile = recorder.finish()
        requireNotNull(finalFile)
        val seq = PoseJsonV2Parser.parse(gunzip(finalFile))
        assertEquals(PoseSessionRecorder.MAX_FRAMES, seq.totalFrames)
    }

    @Test
    fun abortDeletesTempFileAndLeavesNothingBehind() = runBlocking {
        val dir = tempFolder.newFolder()
        val recorder = PoseSessionRecorder(dir)
        recorder.start(videoWidth = 640, videoHeight = 480)
        recorder.onFrame(coco17Frame(), timestampMs = 0L)
        recorder.abort()
        assertTrue(dir.listFiles()?.isEmpty() ?: true)
    }
}
