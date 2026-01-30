package com.ttcoachai.core.logging

import android.content.Context
import com.ttcoachai.core.logging.providers.LocalFileLogger

/**
 * Singleton manager for the file logger to keep it out of the Application class
 * and avoid initialization during app start.
 */
object LogManager {
    private var logger: LocalFileLogger? = null

    fun getLogger(context: Context): LocalFileLogger {
        if (logger == null) {
            logger = LocalFileLogger(context.applicationContext)
        }
        return logger!!
    }

    /**
     * Optional: explicitly release resources if needed
     */
    fun release() {
        // LocalFileLogger doesn't have a simple release, but its internal 
        // AsyncFileLogger can be shut down.
    }
}
