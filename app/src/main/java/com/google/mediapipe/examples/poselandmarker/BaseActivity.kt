/*
 * AI Coach for Table Tennis
 * Base Activity - Handles locale for all activities
 */

package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Base activity that applies locale to all child activities
 */
abstract class BaseActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Apply locale when activity is created
        LocaleHelper.applyLocale(this)
    }
}
