/*
 * AI Coach for Table Tennis
 * Settings Manager - Handles SharedPreferences operations
 */

package com.ttcoachai.managers

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_coach_prefs", Context.MODE_PRIVATE)
    
    // Pose Landmarker specific prefs (matching ActivitySettingsActivity)
    private val posePrefs: SharedPreferences = context.getSharedPreferences("PoseLandmarkerPreferences", Context.MODE_PRIVATE)

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
    fun isCorrectionTypeEnabled(type: com.ttcoachai.shared.models.CorrectionType): Boolean {
        return prefs.getBoolean("correction_enabled_${type.name}", true)
    }
    
    fun setCorrectionTypeEnabled(type: com.ttcoachai.shared.models.CorrectionType, enabled: Boolean) {
        prefs.edit().putBoolean("correction_enabled_${type.name}", enabled).apply()
    }
    
    // Camera settings
    fun getCameraResolution(): Int = prefs.getInt("camera_resolution", 1)
    fun setCameraResolution(resolution: Int) = prefs.edit().putInt("camera_resolution", resolution).apply()
    
    fun getTargetFps(): Int = prefs.getInt("target_fps", 30).coerceIn(15, 60)
    fun setTargetFps(fps: Int) = prefs.edit().putInt("target_fps", fps).apply()
    
    fun isShowSkeleton(): Boolean = prefs.getBoolean("show_skeleton", true)
    fun setShowSkeleton(show: Boolean) = prefs.edit().putBoolean("show_skeleton", show).apply()
    
    // Ball detection FPS (how often to run ball detector)
    fun getBallDetectionFps(): Int = prefs.getInt("ball_detection_fps", 30).coerceIn(10, 120)
    fun setBallDetectionFps(fps: Int) = prefs.edit().putInt("ball_detection_fps", fps).apply()

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
    fun getCoachingStyle(): com.ttcoachai.models.CoachingStyle {
        val ordinal = prefs.getInt("coaching_style", 0)
        return com.ttcoachai.models.CoachingStyle.fromOrdinal(ordinal)
    }
    
    fun setCoachingStyle(style: com.ttcoachai.models.CoachingStyle) {
        prefs.edit().putInt("coaching_style", style.ordinal).apply()
    }

    /** Voice-clip pack id for the selected coaching style (assets/voice/<styleId>/). */
    fun getVoiceStyleId(): String = getCoachingStyle().styleId

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

    // Skill Level
    fun getSkillLevel(): String = prefs.getString("skill_level", "Intermediate") ?: "Intermediate"
    fun setSkillLevel(level: String) = prefs.edit().putString("skill_level", level).apply()

    // Language
    fun getLanguageCode(): String = prefs.getString("app_language", "") ?: ""
    fun setLanguageCode(code: String) = prefs.edit().putString("app_language", code).apply()

    // Activity Settings (Pose Detection)
    fun getDetectionThreshold(): Float = posePrefs.getFloat("detection_confidence", 0.5f)
    fun setDetectionThreshold(value: Float) = posePrefs.edit().putFloat("detection_confidence", value).apply()

    fun getTrackingThreshold(): Float = posePrefs.getFloat("tracking_confidence", 0.5f)
    fun setTrackingThreshold(value: Float) = posePrefs.edit().putFloat("tracking_confidence", value).apply()

    fun getPresenceThreshold(): Float = posePrefs.getFloat("presence_confidence", 0.5f)
    fun setPresenceThreshold(value: Float) = posePrefs.edit().putFloat("presence_confidence", value).apply()

    fun getPoseModel(): Int = posePrefs.getInt("model", 0)
    fun setPoseModel(model: Int) = posePrefs.edit().putInt("model", model).apply()

    fun getPoseDelegate(): Int = posePrefs.getInt("delegate", 0)
    fun setPoseDelegate(delegate: Int) = posePrefs.edit().putInt("delegate", delegate).apply()

    // Theme Mode
    fun getNightMode(): Int = prefs.getInt("night_mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    fun setNightMode(mode: Int) = prefs.edit().putInt("night_mode", mode).apply()

    fun resetToDefaults() {
        prefs.edit().clear().apply()
        posePrefs.edit().clear().apply()
    }

    // --- Detection tuning (persist-only; real defaults from StrokeDetector2D / CameraAngleEstimator / LocomotionFilter) ---
    fun getDetCameraAngle(): Int = prefs.getInt("det_camera_angle", 0)
    fun setDetCameraAngle(value: Int) = prefs.edit().putInt("det_camera_angle", value).apply()

    fun getDetPeakSpeed(): Float = prefs.getFloat("det_peak_speed", 1.0f)
    fun setDetPeakSpeed(value: Float) = prefs.edit().putFloat("det_peak_speed", value).apply()

    fun getDetMinPeakIntervalMs(): Int = prefs.getInt("det_min_peak_interval", 500)
    fun setDetMinPeakIntervalMs(value: Int) = prefs.edit().putInt("det_min_peak_interval", value).apply()

    fun getDetSpeedSmoothingMs(): Int = prefs.getInt("det_speed_smoothing", 300)
    fun setDetSpeedSmoothingMs(value: Int) = prefs.edit().putInt("det_speed_smoothing", value).apply()

    fun getDetWalkGate(): Float = prefs.getFloat("det_walk_gate", 0.4f)
    fun setDetWalkGate(value: Float) = prefs.edit().putFloat("det_walk_gate", value).apply()

    fun isDetSkipStaleReps(): Boolean = prefs.getBoolean("det_skip_stale", true)
    fun setDetSkipStaleReps(value: Boolean) = prefs.edit().putBoolean("det_skip_stale", value).apply()

    fun getDetPreStrokeBufferMs(): Int = prefs.getInt("det_prestroke_buffer", 1000)
    fun setDetPreStrokeBufferMs(value: Int) = prefs.edit().putInt("det_prestroke_buffer", value).apply()

    // --- Feedback tuning (real defaults from feedbackSettings.ts DEFAULT_FEEDBACK_SETTINGS) ---
    fun isPlayingHandRight(): Boolean = prefs.getBoolean("fb_playing_hand_right", true)
    fun setPlayingHandRight(value: Boolean) = prefs.edit().putBoolean("fb_playing_hand_right", value).apply()

    fun getFbZoneWidth(): Float = prefs.getFloat("fb_zone_width", 1.4f)
    fun setFbZoneWidth(value: Float) = prefs.edit().putFloat("fb_zone_width", value).apply()

    fun getFbSignificanceDeg(): Int = prefs.getInt("fb_significance", 7)
    fun setFbSignificanceDeg(value: Int) = prefs.edit().putInt("fb_significance", value).apply()

    fun getFbReminderIntervalMs(): Int = prefs.getInt("fb_reminder_ms", 10000)
    fun setFbReminderIntervalMs(value: Int) = prefs.edit().putInt("fb_reminder_ms", value).apply()

    fun isFbAlternateCues(): Boolean = prefs.getBoolean("fb_alternate_cues", true)
    fun setFbAlternateCues(value: Boolean) = prefs.edit().putBoolean("fb_alternate_cues", value).apply()

    fun getFbPauseBetweenMs(): Int = prefs.getInt("fb_pause_between_ms", 5000)
    fun setFbPauseBetweenMs(value: Int) = prefs.edit().putInt("fb_pause_between_ms", value).apply()

    fun getFbSilenceBeforePraiseMs(): Int = prefs.getInt("fb_silence_before_praise_ms", 10000)
    fun setFbSilenceBeforePraiseMs(value: Int) = prefs.edit().putInt("fb_silence_before_praise_ms", value).apply()

    fun getFbPauseAfterStrokeMs(): Int = prefs.getInt("fb_pause_after_stroke_ms", 300)
    fun setFbPauseAfterStrokeMs(value: Int) = prefs.edit().putInt("fb_pause_after_stroke_ms", value).apply()

    fun isPraiseEnabled(): Boolean = prefs.getBoolean("fb_praise_enabled", true)
    fun setPraiseEnabled(value: Boolean) = prefs.edit().putBoolean("fb_praise_enabled", value).apply()

    fun isPraiseOnCorrections(): Boolean = prefs.getBoolean("fb_praise_on_corrections", true)
    fun setPraiseOnCorrections(value: Boolean) = prefs.edit().putBoolean("fb_praise_on_corrections", value).apply()

    fun isPraiseOnStreak(): Boolean = prefs.getBoolean("fb_praise_on_streak", false)
    fun setPraiseOnStreak(value: Boolean) = prefs.edit().putBoolean("fb_praise_on_streak", value).apply()

    fun getPraiseStreakLen(): Int = prefs.getInt("fb_praise_streak_len", 3)
    fun setPraiseStreakLen(value: Int) = prefs.edit().putInt("fb_praise_streak_len", value).apply()

    // --- Language (coach voice-cue language; distinct from interface language `app_language`) ---
    fun getCoachLanguage(): String = prefs.getString("coach_language", "en") ?: "en"
    fun setCoachLanguage(value: String) = prefs.edit().putString("coach_language", value).apply()
}
