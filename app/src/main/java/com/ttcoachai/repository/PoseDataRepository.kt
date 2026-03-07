/*
 * AI Coach for Table Tennis
 * Pose Data Repository - Cloud Storage operations for large pose files
 */

package com.ttcoachai.repository

import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

/**
 * Repository for pose data storage using Firebase Cloud Storage.
 * Used for storing large pose JSON files that exceed Firestore document limits.
 */
class PoseDataRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    companion object {
        private const val TAG = "PoseDataRepository"
        private const val POSES_FOLDER = "poses"
        private const val MAX_DOWNLOAD_SIZE: Long = 10 * 1024 * 1024 // 10MB
    }

    /**
     * Upload pose data to Cloud Storage.
     *
     * @param userId User ID
     * @param sessionId Training session ID
     * @param data Pose data as byte array (JSON)
     * @return Cloud Storage path on success
     */
    suspend fun uploadPoseData(
        userId: String,
        sessionId: String,
        data: ByteArray
    ): Result<String> {
        return try {
            val path = "$POSES_FOLDER/$userId/$sessionId.json"
            val ref = storage.reference.child(path)

            ref.putBytes(data).await()

            Log.d(TAG, "Pose data uploaded: $path (${data.size} bytes)")
            Result.success(path)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload pose data", e)
            Result.failure(e)
        }
    }

    /**
     * Upload pose data from JSON string.
     */
    suspend fun uploadPoseDataJson(
        userId: String,
        sessionId: String,
        jsonData: String
    ): Result<String> {
        return uploadPoseData(userId, sessionId, jsonData.toByteArray(Charsets.UTF_8))
    }

    /**
     * Download pose data from Cloud Storage.
     *
     * @param path Cloud Storage path
     * @return Pose data as byte array, or null if not found
     */
    suspend fun downloadPoseData(path: String): ByteArray? {
        return try {
            val ref = storage.reference.child(path)
            val data = ref.getBytes(MAX_DOWNLOAD_SIZE).await()

            Log.d(TAG, "Pose data downloaded: $path (${data.size} bytes)")
            data
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download pose data: $path", e)
            null
        }
    }

    /**
     * Download pose data as JSON string.
     */
    suspend fun downloadPoseDataJson(path: String): String? {
        val data = downloadPoseData(path) ?: return null
        return String(data, Charsets.UTF_8)
    }

    /**
     * Delete pose data from Cloud Storage.
     */
    suspend fun deletePoseData(path: String): Result<Unit> {
        return try {
            val ref = storage.reference.child(path)
            ref.delete().await()

            Log.d(TAG, "Pose data deleted: $path")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete pose data: $path", e)
            Result.failure(e)
        }
    }

    /**
     * Delete all pose data for a session.
     */
    suspend fun deleteSessionPoseData(userId: String, sessionId: String): Result<Unit> {
        val path = "$POSES_FOLDER/$userId/$sessionId.json"
        return deletePoseData(path)
    }

    /**
     * Check if pose data exists.
     */
    suspend fun poseDataExists(path: String): Boolean {
        return try {
            val ref = storage.reference.child(path)
            ref.metadata.await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get download URL for pose data (for sharing or direct access).
     */
    suspend fun getPoseDataUrl(path: String): String? {
        return try {
            val ref = storage.reference.child(path)
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get download URL: $path", e)
            null
        }
    }
}
