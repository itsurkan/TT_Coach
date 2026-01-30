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
        // com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(this)

        // Set theme mode from settings
        AppCompatDelegate.setDefaultNightMode(settingsManager.getNightMode())
        
        Log.i(TAG, "TT Coach Application started [SDK: ${android.os.Build.VERSION.SDK_INT}, DevMode: ${settingsManager.isDeveloperModeEnabled()}]")
    }
    
    override fun onTerminate() {
        super.onTerminate()
    }
    
    companion object {
        private const val TAG = "TTCoachApplication"
    }
}
