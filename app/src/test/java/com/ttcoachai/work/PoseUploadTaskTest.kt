package com.ttcoachai.work

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PoseUploadTaskTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun success_sets_pose_data_path_and_deletes_file() = runBlocking {
        val file = tempFolder.newFile("s1.json.gz").apply { writeText("data") }
        var savedSessionId: String? = null
        var savedPath: String? = null
        val task = PoseUploadTask(
            uploadPoseFile = { _, _, _ -> Result.success("poses/u1/s1.json.gz") },
            setPoseDataPath = { sid, path -> savedSessionId = sid; savedPath = path }
        )

        val outcome = task.run("u1", "s1", file)

        assertTrue(outcome is PoseUploadTask.Outcome.Success)
        assertEquals("poses/u1/s1.json.gz", (outcome as PoseUploadTask.Outcome.Success).path)
        assertEquals("s1", savedSessionId)
        assertEquals("poses/u1/s1.json.gz", savedPath)
        assertTrue("uploaded file should be deleted locally", !file.exists())
    }

    @Test
    fun failure_returns_retry_and_keeps_file() = runBlocking {
        val file = tempFolder.newFile("s2.json.gz").apply { writeText("data") }
        val task = PoseUploadTask(
            uploadPoseFile = { _, _, _ -> Result.failure(RuntimeException("network")) },
            setPoseDataPath = { _, _ -> }
        )

        val outcome = task.run("u1", "s2", file)

        assertEquals(PoseUploadTask.Outcome.Retry, outcome)
        assertTrue("file must survive a retry so WorkManager can try again", file.exists())
    }

    @Test
    fun missing_file_returns_missing_file_outcome() = runBlocking {
        val file = File(tempFolder.root, "does-not-exist.json.gz")
        val task = PoseUploadTask(
            uploadPoseFile = { _, _, _ -> Result.success("unused") },
            setPoseDataPath = { _, _ -> }
        )

        assertEquals(PoseUploadTask.Outcome.MissingFile, task.run("u1", "s3", file))
    }
}
