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
        
        // Enable Material 3 Dynamic Colors
        com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(this)

        // Set theme mode from settings
        AppCompatDelegate.setDefaultNightMode(settingsManager.getNightMode())
        
        // Initialize async file logger (zero latency impact)
        fileLogger = LocalFileLogger(this)
        
        // Disable file logging by default unless in developer mode
        if (settingsManager.isDeveloperModeEnabled()) {
            fileLogger.setFileLoggingEnabled(true)
            fileLogger.logInfo(TAG, "Developer mode detected, enabling file logging")
        }
        
        Log.i(TAG, "Application started with async file logging (currently: ${if (settingsManager.isDeveloperModeEnabled()) "ON" else "OFF"})")
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
