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
    
    fun getFeedbackType(): Int = prefs.getInt("feedback_type", 1) // Default: STANDARD (1)
    fun setFeedbackType(type: Int) = prefs.edit().putInt("feedback_type", type).apply()
    
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
    
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
}
