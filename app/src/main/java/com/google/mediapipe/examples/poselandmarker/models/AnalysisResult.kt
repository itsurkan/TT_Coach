/*
 * AI Coach for Table Tennis
 * Analysis Result Model
 */

package com.ttcoachai.models

/**
 * Типи корекції для фідбеку
 */
enum class CorrectionType {
    WRIST,
    BODY_ROTATION,
    FOLLOW_THROUGH,
    CONTACT_HEIGHT,
    ELBOW_POSITION,
    STROKE_SPEED,
    GENERAL
}

/**
 * Елемент фідбеку з типом корекції
 */
data class FeedbackItem(
    val message: String,
    val type: CorrectionType,
    val isPositive: Boolean = false
)

/**
 * Результат аналізу техніки удару
 */
data class AnalysisResult(
    val timestamp: Long = System.currentTimeMillis(),
    
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

/**
 * Фази удару в настільному тенісі
 */
enum class StrokePhase {
    READY,          // Готова позиція
    BACKSWING,      // Замах
    FORWARD_SWING,  // Рух вперед
    CONTACT,        // Контакт з м'ячем
    FOLLOW_THROUGH, // Проведення
    RECOVERY        // Повернення в готову позицію
}

/**
 * Типи помилок техніки
 */
/**
 * Коди помилок техніки (відповідають назвам у strings.xml)
 */
object TechniqueErrors {
    const val WRIST_BENT = "error_wrist_bent"
    const val LOW_ROTATION = "error_low_rotation"
    const val HIGH_CONTACT = "error_high_contact"
    const val LOW_CONTACT = "error_low_contact"
    const val NO_FOLLOW_THROUGH = "error_no_follow_through"
    const val ELBOW_FAR = "error_elbow_far"
    const val ELBOW_CLOSE = "error_elbow_close"
    const val SLOW_STROKE = "error_slow_stroke"
    const val FAST_STROKE = "error_fast_stroke"
}

/**
 * Коди рекомендацій (відповідають назвам у strings.xml)
 */
object TechniqueRecommendations {
    const val STRAIGHTEN_WRIST = "rec_straighten_wrist"
    const val ROTATE_MORE = "rec_rotate_more"
    const val ADJUST_CONTACT_HEIGHT = "rec_contact_height"
    const val COMPLETE_FOLLOW_THROUGH = "rec_complete_follow"
    const val KEEP_ELBOW_CLOSE = "rec_keep_elbow_close"
    const val MOVE_ELBOW_AWAY = "rec_move_elbow_away"
    const val INCREASE_SPEED = "rec_increase_speed"
    const val CONTROL_SPEED = "rec_control_speed"
}
