/*
 * AI Coach for Table Tennis
 * Cloud Settings Repository - Firestore operations for user settings
 */

package com.ttcoachai.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ttcoachai.managers.SettingsManager
import com.ttcoachai.models.UserProfile
import com.ttcoachai.models.UserSettings
import kotlinx.coroutines.tasks.await

/**
 * Repository for user settings sync with Firestore.
 */
class CloudSettingsRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "CloudSettingsRepository"
        private const val SETTINGS_SUBCOLLECTION = "data"
    }

    /**
     * Save user settings to Firestore.
     * Stored as a subcollection under the user document.
     */
    suspend fun saveSettings(userId: String, settings: UserSettings): Result<Unit> {
        return try {
            val settingsWithUser = settings.copy(
                userId = userId,
                lastSyncedAt = System.currentTimeMillis()
            )

            firestore.collection(UserProfile.COLLECTION)
                .document(userId)
                .collection(SETTINGS_SUBCOLLECTION)
                .document(UserSettings.DOCUMENT_ID)
                .set(settingsWithUser.toMap(), SetOptions.merge())
                .await()

            Log.d(TAG, "Settings saved for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save settings", e)
            Result.failure(e)
        }
    }

    /**
     * Get user settings from Firestore.
     */
    suspend fun getSettings(userId: String): UserSettings? {
        return try {
            val doc = firestore.collection(UserProfile.COLLECTION)
                .document(userId)
                .collection(SETTINGS_SUBCOLLECTION)
                .document(UserSettings.DOCUMENT_ID)
                .get()
                .await()

            if (doc.exists()) {
                doc.toObject(UserSettings::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get settings for $userId", e)
            null
        }
    }

    /**
     * Sync settings between local SettingsManager and cloud.
     * Uses last-write-wins strategy.
     *
     * @param userId User ID
     * @param localManager Local SettingsManager
     * @param forceUpload If true, always upload local settings
     * @return The final synced settings
     */
    suspend fun syncSettings(
        userId: String,
        localManager: SettingsManager,
        forceUpload: Boolean = false
    ): UserSettings {
        val cloudSettings = getSettings(userId)
        val localSettings = UserSettings.fromSettingsManager(userId, localManager)

        return when {
            cloudSettings == null || forceUpload -> {
                // No cloud settings, upload local
                Log.d(TAG, "Uploading local settings to cloud")
                saveSettings(userId, localSettings)
                localSettings
            }
            cloudSettings.lastSyncedAt > localSettings.lastSyncedAt -> {
                // Cloud is newer, apply to local
                Log.d(TAG, "Applying cloud settings to local")
                cloudSettings.applyToSettingsManager(localManager)
                cloudSettings
            }
            else -> {
                // Local is newer or same, upload to cloud
                Log.d(TAG, "Uploading local settings (newer)")
                saveSettings(userId, localSettings)
                localSettings
            }
        }
    }
}
