/*
 * AI Coach for Table Tennis
 * Application Class - Handles app-wide initialization including locale
 */

package com.ttcoachai

import android.app.Application
import android.content.Context
import android.util.Log
import com.ttcoachai.managers.SettingsManager
import com.ttcoachai.core.logging.providers.LocalFileLogger
import kotlinx.coroutines.runBlocking

import androidx.appcompat.app.AppCompatDelegate

class TTCoachApplication : Application() {
    
    private lateinit var fileLogger: LocalFileLogger
    val settingsManager: SettingsManager by lazy { SettingsManager(this) }
    val authRepository: com.ttcoachai.repository.AuthRepository by lazy { 
        com.ttcoachai.repository.AuthRepository(this) 
    }
    
    override fun attachBaseContext(base: Context) {
        // Apply saved locale before any activity is created
        super.attachBaseContext(LocaleHelper.applyLocale(base))
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Enable Material 3 Dynamic Colors (apply as early as possible)
        com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(this)

        // Set theme mode from settings
        AppCompatDelegate.setDefaultNightMode(settingsManager.getNightMode())
        
        // Initialize async file logger
        fileLogger = LocalFileLogger(this)
        
        if (settingsManager.isDeveloperModeEnabled()) {
            fileLogger.setFileLoggingEnabled(true)
            fileLogger.logInfo(TAG, "Application initialized in developer mode")
        }
        
        Log.i(TAG, "TT Coach Application started [SDK: ${android.os.Build.VERSION.SDK_INT}, DevMode: ${settingsManager.isDeveloperModeEnabled()}]")
    }
    
    override fun onTerminate() {
        // Graceful shutdown (flush pending events)
        runBlocking {
            fileLogger.shutdown()
        }
        super.onTerminate()
    }
    
    fun getFileLogger(): LocalFileLogger = fileLogger
    
    companion object {
        private const val TAG = "TTCoachApplication"
    }
}
