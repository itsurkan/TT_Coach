/*
 * AI Coach for Table Tennis
 * Progress Repository - Firestore operations for user progress
 */

package com.ttcoachai.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ttcoachai.db.ProgressDao
import com.ttcoachai.models.TrainingSession
import com.ttcoachai.models.UserProfile
import com.ttcoachai.models.UserProgress
import kotlinx.coroutines.tasks.await

/**
 * Repository for user progress operations, using Room as local cache
 * and Firestore for cloud sync.
 */
class ProgressRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val progressDao: ProgressDao? = null
) {
    companion object {
        private const val TAG = "ProgressRepository"
        private const val PROGRESS_SUBCOLLECTION = "data"
    }

    /**
     * Get user progress from Room.
     */
    suspend fun getProgress(userId: String): UserProgress? {
        return progressDao?.getProgress(userId)
    }

    /**
     * Save user progress locally and push to Firestore.
     */
    suspend fun saveProgress(userId: String, progress: UserProgress): Result<Unit> {
        val progressWithUser = progress.copy(
            userId = userId,
            lastUpdatedAt = System.currentTimeMillis()
        )

        // 1. Save to local Room
        progressDao?.insertProgress(progressWithUser.copy(isSynced = false))

        return try {
            // 2. Push to Firestore
            firestore.collection(UserProfile.COLLECTION)
                .document(userId)
                .collection(PROGRESS_SUBCOLLECTION)
                .document(UserProgress.DOCUMENT_ID)
                .set(progressWithUser.toMap(), SetOptions.merge())
                .await()

            // 3. Mark as synced locally
            progressDao?.markAsSynced(userId)

            Log.d(TAG, "Progress saved and synced: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync progress, will retry later", e)
            // Still success for local save
            Result.success(Unit)
        }
    }

    /**
     * Update progress after completing a training session.
     */
    suspend fun updateProgressWithSession(
        userId: String,
        session: TrainingSession
    ): Result<UserProgress> {
        return try {
            // Get current progress or create new
            val currentProgress = getProgress(userId) ?: UserProgress(userId = userId)

            // Calculate updated progress
            val updatedProgress = currentProgress.withNewSession(session)

            // Save locally and push to cloud
            saveProgress(userId, updatedProgress)

            Log.d(TAG, "Progress updated with session ${session.id}")
            Result.success(updatedProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update progress with session", e)
            Result.failure(e)
        }
    }

    /**
     * Get progress directly from Firestore.
     */
    suspend fun getProgressFromCloud(userId: String): UserProgress? {
        return try {
            val doc = firestore.collection(UserProfile.COLLECTION)
                .document(userId)
                .collection(PROGRESS_SUBCOLLECTION)
                .document(UserProgress.DOCUMENT_ID)
                .get()
                .await()

            if (doc.exists()) {
                doc.toObject(UserProgress::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get progress from cloud", e)
            null
        }
    }

    /**
     * Save progress to local Room.
     */
    suspend fun saveProgressLocally(progress: UserProgress) {
        progressDao?.insertProgress(progress)
    }

    /**
     * Push unsynced progress to Firestore.
     */
    suspend fun pushUnsyncedProgress(userId: String) {
        val unsynced = progressDao?.getUnsyncedProgress()?.filter { it.userId == userId } ?: return
        for (progress in unsynced) {
            try {
                firestore.collection(UserProfile.COLLECTION)
                    .document(userId)
                    .collection(PROGRESS_SUBCOLLECTION)
                    .document(UserProgress.DOCUMENT_ID)
                    .set(progress.toMap(), SetOptions.merge())
                    .await()
                progressDao.markAsSynced(userId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push progress for $userId", e)
            }
        }
    }

    /**
     * Clear local progress data for a user.
     */
    suspend fun clearLocalData(userId: String) {
        progressDao?.clearAllForUser(userId)
    }

    /**
     * Initialize progress for a new user.
     */
    suspend fun initializeProgress(userId: String): Result<UserProgress> {
        val existingProgress = getProgress(userId)
        if (existingProgress != null) {
            return Result.success(existingProgress)
        }

        val newProgress = UserProgress(userId = userId)
        return saveProgress(userId, newProgress).map { newProgress }
    }
}
