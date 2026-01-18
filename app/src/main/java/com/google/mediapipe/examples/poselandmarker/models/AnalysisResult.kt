/*
 * AI Coach for Table Tennis
 * Analysis Result Model
 */

package com.google.mediapipe.examples.poselandmarker.models

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
object TechniqueErrors {
    const val WRIST_BENT = "Зап'ястя зігнуте - випряміть його"
    const val LOW_ROTATION = "Недостатня ротація корпусу - поверніться більше"
    const val HIGH_CONTACT = "Контакт надто високо - опустіть точку удару"
    const val LOW_CONTACT = "Контакт надто низько - підніміть точку удару"
    const val NO_FOLLOW_THROUGH = "Недостатнє проведення - доведіть рух до кінця"
    const val ELBOW_FAR = "Лікоть далеко від тіла - тримайте його ближче"
    const val ELBOW_CLOSE = "Лікоть притиснутий до тіла - тримайте його вільніше"
    const val SLOW_STROKE = "Занадто повільний удар - додайте швидкості"
    const val FAST_STROKE = "Занадто швидкий удар - контролюйте швидкість"
}

/**
 * Типи рекомендацій
 */
object TechniqueRecommendations {
    const val STRAIGHTEN_WRIST = "Тримайте зап'ястя рівно під час удару"
    const val ROTATE_MORE = "Більше ротації корпусу для потужності"
    const val ADJUST_CONTACT_HEIGHT = "Контакт на рівні столу - оптимальна висота"
    const val COMPLETE_FOLLOW_THROUGH = "Завершуйте проведення вгору-вперед"
    const val KEEP_ELBOW_CLOSE = "Тримайте лікоть близько до тіла"
    const val MOVE_ELBOW_AWAY = "Не притискайте лікоть занадто сильно"
    const val INCREASE_SPEED = "Додайте швидкості для ефективності"
    const val CONTROL_SPEED = "Зменшіть швидкість для кращого контролю"
}
