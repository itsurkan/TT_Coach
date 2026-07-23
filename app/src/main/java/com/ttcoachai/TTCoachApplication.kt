/*
 * AI Coach for Table Tennis
 * Application Class - Handles app-wide initialization including locale
 */

package com.ttcoachai

import android.app.Application
import android.content.Context
import android.util.Log
import com.ttcoachai.managers.SettingsManager
import com.ttcoachai.managers.CloudSyncManager
import com.ttcoachai.core.logging.providers.LocalFileLogger
import com.ttcoachai.work.PoseUploadQueue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow

import androidx.appcompat.app.AppCompatDelegate

class TTCoachApplication : Application() {
    
    val settingsManager: SettingsManager by lazy { SettingsManager(this) }
    val authRepository: com.ttcoachai.repository.AuthRepository by lazy { 
        com.ttcoachai.repository.AuthRepository(this) 
    }
    
    val database: com.ttcoachai.db.AppDatabase by lazy { 
        com.ttcoachai.db.AppDatabase.getDatabase(this) 
    }
    
    // Cloud sync manager for Firestore operations
    val cloudSyncManager: CloudSyncManager by lazy {
        CloudSyncManager(
            settingsManager = settingsManager,
            trainingRepository = com.ttcoachai.repository.TrainingRepository(trainingDao = database.trainingDao()),
            progressRepository = com.ttcoachai.repository.ProgressRepository(progressDao = database.progressDao())
        )
    }

    val sessionAnalyticsRecorder: com.ttcoachai.managers.SessionAnalyticsRecorder by lazy {
        com.ttcoachai.managers.SessionAnalyticsRecorder(database.sessionAnalyticsDao())
    }

    // One-shot signal: set by TrainingActivity's async save-completion callback, consumed by
    // MainActivity to navigate to SessionReviewFragment exactly once. See
    // docs/superpowers/specs/2026-07-03-finish-to-session-summary-flow-design.md.
    val pendingReviewSessionId = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()

        // Apply the saved interface language. AppCompat owns locale application from here on
        // (it re-creates every AppCompatActivity when the locale changes, which a manual
        // attachBaseContext override cannot do). On API < 33 this is the documented way to
        // restore per-app locales without `autoStoreLocales` — our SharedPreferences value
        // stays the single source of truth.
        LocaleHelper.applyStoredLocale(settingsManager.getLanguageCode())

        // Enable Material 3 Dynamic Colors (apply as early as possible)
        // com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(this)

        // Set theme mode from settings
        AppCompatDelegate.setDefaultNightMode(settingsManager.getNightMode())
        
        // Initialize cloud sync (listens for auth state changes)
        cloudSyncManager.initialize()

        // Pose-upload cache maintenance (component C's app-start sweep, see
        // docs/superpowers/specs/2026-07-23-pose-upload-firebase-design.md). Touches disk
        // (directory listing, stat, delete) so it must not run on the main thread.
        Thread {
            PoseUploadQueue.sweepOrphans(this)
            PoseUploadQueue.evictOldCache(this)
        }.start()

        Log.i(TAG, "TT Coach Application started [SDK: ${android.os.Build.VERSION.SDK_INT}, DevMode: ${settingsManager.isDeveloperModeEnabled()}]")
    }
    
    override fun onTerminate() {
        super.onTerminate()
    }
    
    companion object {
        private const val TAG = "TTCoachApplication"
    }
}

