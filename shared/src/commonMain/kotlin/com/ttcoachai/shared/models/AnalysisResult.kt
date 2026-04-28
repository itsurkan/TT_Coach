package com.ttcoachai.shared.models

data class AnalysisResult(
    val timestamp: Long = 0L,

    // Виміряні значення
    val wristAngle: Float? = null,
    val bodyRotation: Float? = null,
    val followThroughAngle: Float? = null,
    val contactHeight: Float? = null,
    val strokeSpeed: Float? = null,
    val elbowBodyDistance: Float? = null,

    // Оцінки валідності
    val isWristAngleValid: Boolean = false,
    val isBodyRotationValid: Boolean = false,
    val isFollowThroughValid: Boolean = false,
    val isContactHeightValid: Boolean = false,
    val isStrokeSpeedValid: Boolean = false,
    val isElbowPositionValid: Boolean = false,

    // Загальна оцінка
    val overallScore: Float = 0f, // 0-100%

    // Фази руху
    val phase: StrokePhase = StrokePhase.READY,

    // Помилки та рекомендації
    val errors: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val feedbackItems: List<FeedbackItem> = emptyList()
) {
    /**
     * Чи успішний удар (всі параметри валідні)
     */
    fun isSuccessful(): Boolean {
        return overallScore >= 80f
    }

    /**
     * Отримати текстовий опис результату
     */
    fun getSummary(): String {
        return when {
            overallScore >= 90f -> "✅ Відмінно! Ідеальна техніка!"
            overallScore >= 80f -> "✅ Добре! Продовжуйте в тому ж дусі!"
            overallScore >= 70f -> "⚠️ Непогано, але є над чим попрацювати"
            overallScore >= 60f -> "⚠️ Потрібні покращення"
            else -> "❌ Потрібна робота над технікою"
        }
    }

    /**
     * Отримати основну помилку (найважливішу)
     */
    fun getPrimaryError(): String? {
        return errors.firstOrNull()
    }

    /**
     * Отримати основну рекомендацію
     */
    fun getPrimaryRecommendation(): String? {
        return recommendations.firstOrNull()
    }
}
