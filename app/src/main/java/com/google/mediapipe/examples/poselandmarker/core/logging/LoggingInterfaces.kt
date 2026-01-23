package com.ttcoachai.core.logging

/**
 * Base logging interface.
 */
interface Logger {
    fun logEvent(eventName: String, params: Map<String, Any> = emptyMap())
    fun logError(throwable: Throwable, context: String? = null)
    fun logInfo(tag: String, message: String)
    fun logDebug(tag: String, message: String)
}

/**
 * Analytics tracking interface.
 */
interface AnalyticsProvider {
    fun trackEvent(eventName: String, properties: Map<String, Any>)
    fun trackScreen(screenName: String)
    fun setUserId(userId: String)
    fun setUserProperty(key: String, value: String)
}

/**
 * Crash reporting interface.
 */
interface CrashReporter {
    fun logException(throwable: Throwable)
    fun logMessage(message: String, level: LogLevel)
    fun setCustomKey(key: String, value: String)
}

/**
 * Log severity levels.
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    FATAL
}
