/*
 * AI Coach for Table Tennis
 * User Repository - Firestore operations for user profiles
 */

package com.ttcoachai.repository

import android.util.Log
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ttcoachai.models.UserProfile
import kotlinx.coroutines.tasks.await

/**
 * Repository for user profile operations in Firestore.
 */
class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "UserRepository"
    }

    /**
     * Create or update user profile from Firebase Auth user.
     * Uses merge to preserve existing fields.
     */
    suspend fun createOrUpdateProfile(firebaseUser: FirebaseUser): Result<UserProfile> {
        return try {
            val uid = firebaseUser.uid
            val existingProfile = getUserProfile(uid)

            val profile = if (existingProfile != null) {
                // Update last login
                existingProfile.copy(
                    lastLoginAt = System.currentTimeMillis(),
                    email = firebaseUser.email ?: existingProfile.email,
                    displayName = firebaseUser.displayName ?: existingProfile.displayName,
                    photoUrl = firebaseUser.photoUrl?.toString() ?: existingProfile.photoUrl
                )
            } else {
                // Create new profile
                UserProfile(
                    uid = uid,
                    email = firebaseUser.email,
                    displayName = firebaseUser.displayName,
                    photoUrl = firebaseUser.photoUrl?.toString(),
                    createdAt = System.currentTimeMillis(),
                    lastLoginAt = System.currentTimeMillis()
                )
            }

            firestore.collection(UserProfile.COLLECTION)
                .document(uid)
                .set(profile.toMap(), SetOptions.merge())
                .await()

            Log.d(TAG, "Profile saved for user: $uid")
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save profile", e)
            Result.failure(e)
        }
    }

    /**
     * Get user profile by UID.
     */
    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            val doc = firestore.collection(UserProfile.COLLECTION)
                .document(uid)
                .get()
                .await()

            if (doc.exists()) {
                doc.toObject(UserProfile::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get profile for $uid", e)
            null
        }
    }

    /**
     * Update subscription status.
     */
    suspend fun updateSubscriptionStatus(
        uid: String,
        status: String,
        expiresAt: Long?
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                UserProfile.FIELD_SUBSCRIPTION_STATUS to status,
                UserProfile.FIELD_SUBSCRIPTION_EXPIRES to expiresAt
            )

            firestore.collection(UserProfile.COLLECTION)
                .document(uid)
                .update(updates)
                .await()

            Log.d(TAG, "Subscription updated for $uid: $status")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update subscription for $uid", e)
            Result.failure(e)
        }
    }

    /**
     * Check if user has premium subscription.
     */
    suspend fun isPremium(uid: String): Boolean {
        val profile = getUserProfile(uid) ?: return false
        return profile.isPremium()
    }
}
