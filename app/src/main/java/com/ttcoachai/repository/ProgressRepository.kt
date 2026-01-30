/*
 * AI Coach for Table Tennis
 * Progress Repository - Firestore operations for user progress
 */

package com.ttcoachai.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ttcoachai.models.TrainingSession
import com.ttcoachai.models.UserProfile
import com.ttcoachai.models.UserProgress
import kotlinx.coroutines.tasks.await

/**
 * Repository for user progress operations in Firestore.
 */
class ProgressRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "ProgressRepository"
        private const val PROGRESS_SUBCOLLECTION = "data"
    }

    /**
     * Get user progress from Firestore.
     */
    suspend fun getProgress(userId: String): UserProgress? {
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
            Log.e(TAG, "Failed to get progress for $userId", e)
            null
        }
    }

    /**
     * Save user progress to Firestore.
     */
    suspend fun saveProgress(userId: String, progress: UserProgress): Result<Unit> {
        return try {
            val progressWithUser = progress.copy(
                userId = userId,
                lastUpdatedAt = System.currentTimeMillis()
            )

            firestore.collection(UserProfile.COLLECTION)
                .document(userId)
                .collection(PROGRESS_SUBCOLLECTION)
                .document(UserProgress.DOCUMENT_ID)
                .set(progressWithUser.toMap(), SetOptions.merge())
                .await()

            Log.d(TAG, "Progress saved for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save progress", e)
            Result.failure(e)
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

            // Save to Firestore
            saveProgress(userId, updatedProgress)

            Log.d(TAG, "Progress updated with session ${session.id}")
            Result.success(updatedProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update progress with session", e)
            Result.failure(e)
        }
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
