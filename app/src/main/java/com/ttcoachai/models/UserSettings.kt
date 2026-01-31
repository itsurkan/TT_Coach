/*
 * AI Coach for Table Tennis
 * User Settings Model - Cloud synced user preferences
 */

package com.ttcoachai.models

import com.google.firebase.firestore.DocumentId

/**
 * User settings and preferences synced to cloud.
 * Stored as a subcollection under user document.
 */
data class UserSettings(
    @DocumentId
    val id: String = "settings", // Single document per user
    val userId: String = "",
    // Training Goals
    val weeklySessionsGoal: Int = 7,
    val skillTarget: Int = 90,
    // Audio Settings
    val audioFeedbackEnabled: Boolean = true,
    val feedbackVolume: Int = 80,
    val speechRate: Int = 50,
    val feedbackType: Int = 0, // 0=SHORT, 1=DETAILED
    val feedbackFrequency: Int = 3, // Every N strokes
    // Camera Settings
    val cameraResolution: Int = 1,
    val targetFps: Int = 30,
    val showSkeleton: Boolean = true,
    // Preferences
    val coachingStyle: Int = 0,
    val distanceModeEnabled: Boolean = false,
    val nightMode: Int = -1, // AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    val developerModeEnabled: Boolean = false,
    val skillLevel: String = "Intermediate",
    val languageCode: String = "",
    val subscriptionActive: Boolean = false,
    // Activity Settings (Pose detection)
    val detectionThreshold: Float = 0.5f,
    val trackingThreshold: Float = 0.5f,
    val presenceThreshold: Float = 0.5f,
    val poseModel: Int = 0,
    val poseDelegate: Int = 0,
    // Enabled correction types (stored as comma-separated string)
    val enabledCorrectionTypes: String = "",
    // Sync metadata
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    /**
     * No-arg constructor required for Firestore deserialization
     */
    constructor() : this(id = "settings")

    companion object {
        const val DOCUMENT_ID = "settings"

        fun fromSettingsManager(
            userId: String,
            manager: com.ttcoachai.managers.SettingsManager
        ): UserSettings {
            return UserSettings(
                userId = userId,
                weeklySessionsGoal = manager.getWeeklySessionsGoal(),
                skillTarget = manager.getSkillTarget(),
                audioFeedbackEnabled = manager.isAudioFeedbackEnabled(),
                feedbackVolume = manager.getFeedbackVolume(),
                speechRate = manager.getSpeechRate(),
                feedbackType = manager.getFeedbackType(),
                feedbackFrequency = manager.getFeedbackFrequency(),
                cameraResolution = manager.getCameraResolution(),
                targetFps = manager.getTargetFps(),
                showSkeleton = manager.isShowSkeleton(),
                coachingStyle = manager.getCoachingStyle().ordinal,
                distanceModeEnabled = manager.isDistanceModeEnabled(),
                nightMode = manager.getNightMode(),
                developerModeEnabled = manager.isDeveloperModeEnabled(),
                skillLevel = manager.getSkillLevel(),
                languageCode = manager.getLanguageCode(),
                subscriptionActive = manager.isSubscriptionActive(),
                detectionThreshold = manager.getDetectionThreshold(),
                trackingThreshold = manager.getTrackingThreshold(),
                presenceThreshold = manager.getPresenceThreshold(),
                poseModel = manager.getPoseModel(),
                poseDelegate = manager.getPoseDelegate(),
                lastSyncedAt = System.currentTimeMillis()
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "weeklySessionsGoal" to weeklySessionsGoal,
        "skillTarget" to skillTarget,
        "audioFeedbackEnabled" to audioFeedbackEnabled,
        "feedbackVolume" to feedbackVolume,
        "speechRate" to speechRate,
        "feedbackType" to feedbackType,
        "feedbackFrequency" to feedbackFrequency,
        "cameraResolution" to cameraResolution,
        "targetFps" to targetFps,
        "showSkeleton" to showSkeleton,
        "coachingStyle" to coachingStyle,
        "distanceModeEnabled" to distanceModeEnabled,
        "nightMode" to nightMode,
        "developerModeEnabled" to developerModeEnabled,
        "skillLevel" to skillLevel,
        "languageCode" to languageCode,
        "subscriptionActive" to subscriptionActive,
        "detectionThreshold" to detectionThreshold,
        "trackingThreshold" to trackingThreshold,
        "presenceThreshold" to presenceThreshold,
        "poseModel" to poseModel,
        "poseDelegate" to poseDelegate,
        "enabledCorrectionTypes" to enabledCorrectionTypes,
        "lastSyncedAt" to lastSyncedAt
    )

    /**
     * Apply these cloud settings to local SettingsManager
     */
    fun applyToSettingsManager(manager: com.ttcoachai.managers.SettingsManager) {
        manager.setWeeklySessionsGoal(weeklySessionsGoal)
        manager.setSkillTarget(skillTarget)
        manager.setAudioFeedbackEnabled(audioFeedbackEnabled)
        manager.setFeedbackVolume(feedbackVolume)
        manager.setSpeechRate(speechRate)
        manager.setFeedbackType(feedbackType)
        manager.setFeedbackFrequency(feedbackFrequency)
        manager.setCameraResolution(cameraResolution)
        manager.setTargetFps(targetFps)
        manager.setShowSkeleton(showSkeleton)
        manager.setCoachingStyle(CoachingStyle.fromOrdinal(coachingStyle))
        manager.setDistanceModeEnabled(distanceModeEnabled)
        manager.setNightMode(nightMode)
        manager.setDeveloperModeEnabled(developerModeEnabled)
        manager.setSkillLevel(skillLevel)
        manager.setLanguageCode(languageCode)
        manager.setSubscriptionActive(subscriptionActive)
        manager.setDetectionThreshold(detectionThreshold)
        manager.setTrackingThreshold(trackingThreshold)
        manager.setPresenceThreshold(presenceThreshold)
        manager.setPoseModel(poseModel)
        manager.setPoseDelegate(poseDelegate)
    }
}
