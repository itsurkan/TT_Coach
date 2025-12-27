/*
 * AI Coach for Table Tennis
 * Application Class - Handles app-wide initialization including locale
 */

package com.google.mediapipe.examples.poselandmarker

import android.app.Application
import android.content.Context

class TTCoachApplication : Application() {
    
    override fun attachBaseContext(base: Context) {
        // Apply saved locale before any activity is created
        super.attachBaseContext(LocaleHelper.applyLocale(base))
    }
    
    override fun onCreate() {
        super.onCreate()
        // Any other app-wide initialization can go here
    }
}
