/*
 * AI Coach for Table Tennis
 * Application Class - Handles app-wide initialization including locale
 */

package com.google.mediapipe.examples.poselandmarker

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.mediapipe.examples.poselandmarker.core.logging.providers.LocalFileLogger
import kotlinx.coroutines.runBlocking

class TTCoachApplication : Application() {
    
    private lateinit var fileLogger: LocalFileLogger
    
    override fun attachBaseContext(base: Context) {
        // Apply saved locale before any activity is created
        super.attachBaseContext(LocaleHelper.applyLocale(base))
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize async file logger (zero latency impact)
        fileLogger = LocalFileLogger(this)
        
        Log.i(TAG, "Application started with async file logging")
        
        // Log storage info
        val storageInfo = fileLogger.getStorageInfo()
        Log.i(TAG, "Log storage: ${String.format("%.2f", storageInfo.sizeMB)} MB at ${storageInfo.directory}")
        
        // Log app launch
        fileLogger.logEvent("app_launched", mapOf(
            "timestamp" to System.currentTimeMillis()
        ))
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
