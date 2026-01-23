/*
 * AI Coach for Table Tennis
 * Settings Manager - Handles SharedPreferences operations
 */

package com.google.mediapipe.examples.poselandmarker.managers

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_coach_prefs", Context.MODE_PRIVATE)
    
    // Exercise parameters
    fun getIdealWristAngle(): Int = prefs.getInt("ideal_wrist_angle", 180)
    fun setIdealWristAngle(value: Int) = prefs.edit().putInt("ideal_wrist_angle", value).apply()
    
    fun getMinBodyRotation(): Int = prefs.getInt("min_body_rotation", 45)
    fun setMinBodyRotation(value: Int) = prefs.edit().putInt("min_body_rotation", value).apply()
    
    fun getFollowThroughAngle(): Int = prefs.getInt("follow_through_angle", 120)
    fun setFollowThroughAngle(value: Int) = prefs.edit().putInt("follow_through_angle", value).apply()
    
    // Audio settings
    fun isAudioFeedbackEnabled(): Boolean = prefs.getBoolean("audio_feedback_enabled", true)
    fun setAudioFeedbackEnabled(enabled: Boolean) = prefs.edit().putBoolean("audio_feedback_enabled", enabled).apply()
    
    fun getFeedbackVolume(): Int = prefs.getInt("feedback_volume", 80)
    fun setFeedbackVolume(volume: Int) = prefs.edit().putInt("feedback_volume", volume).apply()
    
    fun getSpeechRate(): Int = prefs.getInt("speech_rate", 50)
    fun setSpeechRate(rate: Int) = prefs.edit().putInt("speech_rate", rate).apply()
    
    fun getFeedbackType(): Int = prefs.getInt("feedback_type", 0) // Default: SHORT (0)
    fun setFeedbackType(type: Int) = prefs.edit().putInt("feedback_type", type).apply()
    
    // Frequency: every N strokes (default: 3)
    fun getFeedbackFrequency(): Int = prefs.getInt("feedback_frequency", 3)
    fun setFeedbackFrequency(value: Int) = prefs.edit().putInt("feedback_frequency", value).apply()
    
    // Correction type filtering
    fun isCorrectionTypeEnabled(type: com.google.mediapipe.examples.poselandmarker.models.CorrectionType): Boolean {
        return prefs.getBoolean("correction_enabled_${type.name}", true)
    }
    
    fun setCorrectionTypeEnabled(type: com.google.mediapipe.examples.poselandmarker.models.CorrectionType, enabled: Boolean) {
        prefs.edit().putBoolean("correction_enabled_${type.name}", enabled).apply()
    }
    
    // Camera settings
    fun getCameraResolution(): Int = prefs.getInt("camera_resolution", 1)
    fun setCameraResolution(resolution: Int) = prefs.edit().putInt("camera_resolution", resolution).apply()
    
    fun getTargetFps(): Int = prefs.getInt("target_fps", 30).coerceIn(15, 60)
    fun setTargetFps(fps: Int) = prefs.edit().putInt("target_fps", fps).apply()
    
    fun isShowSkeleton(): Boolean = prefs.getBoolean("show_skeleton", true)
    fun setShowSkeleton(show: Boolean) = prefs.edit().putBoolean("show_skeleton", show).apply()
    
    // Bulk operations
    fun saveAll(
        wristAngle: Int,
        bodyRotation: Int,
        followThrough: Int,
        audioEnabled: Boolean,
        volume: Int,
        speechRate: Int,
        cameraResolution: Int,
        fps: Int,
        showSkeleton: Boolean,
        feedbackType: Int
    ) {
        prefs.edit().apply {
            putInt("ideal_wrist_angle", wristAngle)
            putInt("min_body_rotation", bodyRotation)
            putInt("follow_through_angle", followThrough)
            putBoolean("audio_feedback_enabled", audioEnabled)
            putInt("feedback_volume", volume)
            putInt("speech_rate", speechRate)
            putInt("camera_resolution", cameraResolution)
            putInt("target_fps", fps)
            putBoolean("show_skeleton", showSkeleton)
            putInt("feedback_type", feedbackType)
            apply()
        }
    }
    
    // Developer Mode
    fun isDeveloperModeEnabled(): Boolean = prefs.getBoolean("developer_mode_enabled", false)
    fun setDeveloperModeEnabled(enabled: Boolean) = prefs.edit().putBoolean("developer_mode_enabled", enabled).apply()
    
    // Subscription Simulation (for testing)
    fun isSubscriptionActive(): Boolean = prefs.getBoolean("subscription_active", false)
    fun setSubscriptionActive(active: Boolean) = prefs.edit().putBoolean("subscription_active", active).apply()
    
    // Coaching Style
    fun getCoachingStyle(): com.google.mediapipe.examples.poselandmarker.models.CoachingStyle {
        val ordinal = prefs.getInt("coaching_style", 0)
        return com.google.mediapipe.examples.poselandmarker.models.CoachingStyle.fromOrdinal(ordinal)
    }
    
    fun setCoachingStyle(style: com.google.mediapipe.examples.poselandmarker.models.CoachingStyle) {
        prefs.edit().putInt("coaching_style", style.ordinal).apply()
    }

    // Login State
    fun isLoggedIn(): Boolean = prefs.getBoolean("is_logged_in", false)
    fun setLoggedIn(loggedIn: Boolean) = prefs.edit().putBoolean("is_logged_in", loggedIn).apply()
    
    // Training Goals
    fun getWeeklySessionsGoal(): Int = prefs.getInt("weekly_sessions_goal", 7)
    fun setWeeklySessionsGoal(days: Int) = prefs.edit().putInt("weekly_sessions_goal", days.coerceIn(1, 7)).apply()
    
    fun getSkillTarget(): Int = prefs.getInt("skill_target", 90)
    fun setSkillTarget(target: Int) = prefs.edit().putInt("skill_target", target.coerceIn(50, 100)).apply()
        
    // Distance Mode
    fun isDistanceModeEnabled(): Boolean = prefs.getBoolean("distance_mode_enabled", false)
    fun setDistanceModeEnabled(enabled: Boolean) = prefs.edit().putBoolean("distance_mode_enabled", enabled).apply()

    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
}
