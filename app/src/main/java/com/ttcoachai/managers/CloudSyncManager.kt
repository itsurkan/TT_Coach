/*
 * AI Coach for Table Tennis
 * Cloud Sync Manager - Orchestrates cloud data synchronization
 */

package com.ttcoachai.managers

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.ttcoachai.models.TrainingSession
import com.ttcoachai.models.UserProgress
import com.ttcoachai.models.UserSettings
import com.ttcoachai.repository.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Central manager for orchestrating cloud sync operations.
 * Handles auth state changes, settings sync, and training data upload.
 */
class CloudSyncManager(
    private val settingsManager: SettingsManager,
    private val userRepository: UserRepository = UserRepository(),
    private val trainingRepository: TrainingRepository = TrainingRepository(),
    private val cloudSettingsRepository: CloudSettingsRepository = CloudSettingsRepository(),
    private val poseDataRepository: PoseDataRepository = PoseDataRepository(),
    private val progressRepository: ProgressRepository = ProgressRepository()
) {
    companion object {
        private const val TAG = "CloudSyncManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val auth = FirebaseAuth.getInstance()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val _lastSyncTime = MutableStateFlow<Long>(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime

    /**
     * Current authenticated user ID, or null if not logged in.
     */
    val currentUserId: String?
        get() = auth.currentUser?.uid

    /**
     * Check if user is authenticated.
     */
    val isAuthenticated: Boolean
        get() = auth.currentUser != null

    /**
     * Initialize sync manager and listen for auth state changes.
     */
    fun initialize() {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                Log.d(TAG, "User signed in: ${user.uid}")
                scope.launch {
                    onUserSignedIn(user)
                }
            } else {
                Log.d(TAG, "User signed out")
                _syncState.value = SyncState.Idle
            }
        }
    }

    /**
     * Called when user signs in. Syncs profile and settings.
     */
    private suspend fun onUserSignedIn(user: FirebaseUser) {
        _syncState.value = SyncState.Syncing("Syncing profile...")

        try {
            // Create or update user profile
            val profileResult = userRepository.createOrUpdateProfile(user)
            if (profileResult.isFailure) {
                Log.e(TAG, "Failed to sync profile", profileResult.exceptionOrNull())
            }

            // Sync settings
            _syncState.value = SyncState.Syncing("Syncing settings...")
            cloudSettingsRepository.syncSettings(user.uid, settingsManager)

            // Initialize progress if needed
            progressRepository.initializeProgress(user.uid)

            _lastSyncTime.value = System.currentTimeMillis()
            _syncState.value = SyncState.Success

            Log.d(TAG, "Initial sync completed for ${user.uid}")
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            _syncState.value = SyncState.Error(e.message ?: "Sync failed")
        }
    }

    /**
     * Manually trigger a full sync.
     */
    fun triggerSync() {
        val user = auth.currentUser ?: return

        scope.launch {
            onUserSignedIn(user)
        }
    }

    /**
     * Upload settings to cloud.
     */
    fun uploadSettings() {
        val userId = currentUserId ?: return

        scope.launch {
            try {
                val settings = UserSettings.fromSettingsManager(userId, settingsManager)
                cloudSettingsRepository.saveSettings(userId, settings)
                Log.d(TAG, "Settings uploaded")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload settings", e)
            }
        }
    }

    /**
     * Save a training session to cloud.
     *
     * @param session Training session data
     * @param poseDataJson Optional pose data JSON (will be uploaded to Cloud Storage)
     */
    suspend fun saveTrainingSession(
        session: TrainingSession,
        poseDataJson: String? = null
    ): Result<String> {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "Cannot save session: user not authenticated")
            return Result.failure(Exception("User not authenticated"))
        }

        return try {
            var sessionToSave = session.copy(userId = userId)

            // Upload pose data if provided
            if (poseDataJson != null) {
                val sessionId = if (session.id.isNotEmpty()) session.id else TrainingSession.generateId()
                val uploadResult = poseDataRepository.uploadPoseDataJson(userId, sessionId, poseDataJson)
                if (uploadResult.isSuccess) {
                    sessionToSave = sessionToSave.copy(
                        id = sessionId,
                        poseDataPath = uploadResult.getOrNull()
                    )
                }
            }

            // Save session to Firestore
            val saveResult = trainingRepository.saveSession(sessionToSave)
            if (saveResult.isSuccess) {
                // Update user progress
                progressRepository.updateProgressWithSession(userId, sessionToSave)
            }

            saveResult
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save training session", e)
            Result.failure(e)
        }
    }

    /**
     * Get recent training sessions.
     */
    suspend fun getRecentSessions(limit: Int = 20): List<TrainingSession> {
        val userId = currentUserId ?: return emptyList()
        return trainingRepository.getSessions(userId, limit)
    }

    /**
     * Get user progress.
     */
    suspend fun getUserProgress(): UserProgress? {
        val userId = currentUserId ?: return null
        return progressRepository.getProgress(userId)
    }

    /**
     * Check if user has premium subscription.
     */
    suspend fun isPremium(): Boolean {
        val userId = currentUserId ?: return false
        return userRepository.isPremium(userId)
    }

    /**
     * Save training session from TrainingStateManager data.
     * Convenience method for TrainingActivity.
     */
    fun saveTrainingFromState(
        exerciseId: String,
        exerciseName: String,
        startTime: Long,
        endTime: Long,
        durationSeconds: Int,
        strokeCount: Int,
        correctStrokes: Int,
        averageScore: Double,
        appVersion: String
    ) {
        if (!isAuthenticated) {
            android.util.Log.d(TAG, "User not authenticated, skipping cloud sync")
            return
        }

        val session = com.ttcoachai.models.TrainingSession(
            id = com.ttcoachai.models.TrainingSession.generateId(),
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            startTime = startTime,
            endTime = endTime,
            durationSeconds = durationSeconds,
            strokeCount = strokeCount,
            correctStrokes = correctStrokes,
            accuracy = (averageScore / 100).toFloat(),
            appVersion = appVersion
        )

        scope.launch {
            val result = saveTrainingSession(session)
            if (result.isSuccess) {
                android.util.Log.d(TAG, "Training session saved to cloud: ${result.getOrNull()}")
            } else {
                android.util.Log.e(TAG, "Failed to save session to cloud", result.exceptionOrNull())
            }
        }
    }
}

/**
 * Represents the current sync state.
 */
sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val message: String) : SyncState()
    object Success : SyncState()
    data class Error(val message: String) : SyncState()
}
