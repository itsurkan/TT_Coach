package com.ttcoachai.work

import com.google.firebase.storage.StorageException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

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

    @Test
    fun firestore_write_failure_returns_retry_and_keeps_file() = runBlocking {
        val file = tempFolder.newFile("s4.json.gz").apply { writeText("data") }
        val task = PoseUploadTask(
            uploadPoseFile = { _, _, _ -> Result.success("poses/u1/s4.json.gz") },
            setPoseDataPath = { _, _ -> throw RuntimeException("firestore unavailable") }
        )

        val outcome = task.run("u1", "s4", file)

        assertEquals(PoseUploadTask.Outcome.Retry, outcome)
        assertTrue(
            "blob already uploaded; local file must survive so a retry re-uploads/re-writes idempotently",
            file.exists()
        )
    }

    @Test
    fun permanent_storage_failure_returns_permanent_failure_and_keeps_file() = runBlocking {
        val file = tempFolder.newFile("s5.json.gz").apply { writeText("data") }
        val permissionDenied = StorageException.fromExceptionAndHttpCode(RuntimeException("denied"), 403)!!
        val task = PoseUploadTask(
            uploadPoseFile = { _, _, _ -> Result.failure(permissionDenied) },
            setPoseDataPath = { _, _ -> }
        )

        val outcome = task.run("u1", "s5", file)

        assertEquals(PoseUploadTask.Outcome.PermanentFailure, outcome)
        assertTrue(
            "storage.rules is not yet deployed, so ERROR_NOT_AUTHORIZED is an expected-today " +
                "denial, not proof of an unrecoverable file — must not delete local data",
            file.exists()
        )
    }

    @Test
    fun transient_storage_failure_returns_retry_and_keeps_file() = runBlocking {
        val file = tempFolder.newFile("s6.json.gz").apply { writeText("data") }
        val serverError = StorageException.fromExceptionAndHttpCode(IOException("timeout"), 503)!!
        val task = PoseUploadTask(
            uploadPoseFile = { _, _, _ -> Result.failure(serverError) },
            setPoseDataPath = { _, _ -> }
        )

        val outcome = task.run("u1", "s6", file)

        assertEquals(PoseUploadTask.Outcome.Retry, outcome)
        assertTrue(file.exists())
    }

    @Test
    fun network_exception_without_http_code_returns_retry() = runBlocking {
        val file = tempFolder.newFile("s7.json.gz").apply { writeText("data") }
        val networkError = StorageException.fromException(IOException("no connectivity"))
        val task = PoseUploadTask(
            uploadPoseFile = { _, _, _ -> Result.failure(networkError) },
            setPoseDataPath = { _, _ -> }
        )

        val outcome = task.run("u1", "s7", file)

        assertEquals(PoseUploadTask.Outcome.Retry, outcome)
        assertTrue(file.exists())
    }

    @Test
    fun classifyStorageFailure_maps_permanent_error_codes() {
        assertEquals(
            PoseUploadTask.Outcome.PermanentFailure,
            PoseUploadTask.classifyStorageFailure(StorageException.ERROR_NOT_AUTHORIZED, -2)
        )
        assertEquals(
            PoseUploadTask.Outcome.PermanentFailure,
            PoseUploadTask.classifyStorageFailure(StorageException.ERROR_QUOTA_EXCEEDED, -2)
        )
    }

    @Test
    fun classifyStorageFailure_keeps_retry_limit_and_5xx_as_retry() {
        assertEquals(
            PoseUploadTask.Outcome.Retry,
            PoseUploadTask.classifyStorageFailure(StorageException.ERROR_RETRY_LIMIT_EXCEEDED, 429)
        )
        assertEquals(
            PoseUploadTask.Outcome.Retry,
            PoseUploadTask.classifyStorageFailure(StorageException.ERROR_UNKNOWN, 500)
        )
    }

    @Test
    fun classifyFailure_treats_non_storage_exceptions_as_retry() {
        assertEquals(PoseUploadTask.Outcome.Retry, PoseUploadTask.classifyFailure(RuntimeException("boom")))
        assertEquals(PoseUploadTask.Outcome.Retry, PoseUploadTask.classifyFailure(IOException("no net")))
    }
}
