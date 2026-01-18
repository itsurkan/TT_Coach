/*
 * AI Coach for Table Tennis
 * Exercise Parameters Model
 */

package com.google.mediapipe.examples.poselandmarker.models

/**
 * Параметри техніки для конкретної вправи
 */
data class ExerciseParameters(
    val exerciseId: String,
    
    // Параметри кутів (в градусах)
    val idealWristAngle: Float = 180f,
    val wristAngleTolerance: Float = 10f,
    
    val minBodyRotation: Float = 45f,
    val bodyRotationTolerance: Float = 10f,
    
    val followThroughAngle: Float = 120f,
    val followThroughTolerance: Float = 20f,
    
    // Параметри висоти контакту
    val contactHeightMin: Float = 0.7f, // відносна висота від підлоги
    val contactHeightMax: Float = 1.1f,
    
    // Параметри швидкості руху
    val minStrokeSpeed: Float = 1.0f, // м/с
    val maxStrokeSpeed: Float = 5.0f,
    
    // Параметри позиції лікоть-тіло
    val maxElbowBodyDistance: Float = 0.3f, // метри
    val minElbowBodyDistance: Float = 0.1f,
    
    // Порогові значення для детекції руху
    val movementStartThreshold: Float = 0.5f, // м/с
    val movementEndThreshold: Float = 0.3f,
    
    // Часові параметри
    val minStrokeDuration: Long = 300, // мс
    val maxStrokeDuration: Long = 2000
) {
    companion object {
        /**
         * Дефолтні параметри для "Накат справа" (Forehand Drive)
         */
        fun forehandDrive(): ExerciseParameters {
            return ExerciseParameters(
                exerciseId = "forehand_drive",
                idealWristAngle = 180f,
                wristAngleTolerance = 10f,
                minBodyRotation = 45f,
                bodyRotationTolerance = 10f,
                followThroughAngle = 120f,
                followThroughTolerance = 20f,
                contactHeightMin = 0.8f,
                contactHeightMax = 1.0f,
                minStrokeSpeed = 1.5f,
                maxStrokeSpeed = 4.0f,
                maxElbowBodyDistance = 0.25f,
                minElbowBodyDistance = 0.12f,
                movementStartThreshold = 0.5f,
                movementEndThreshold = 0.3f,
                minStrokeDuration = 400,
                maxStrokeDuration = 1500
            )
        }

        /**
         * Дефолтні параметри для "Накат зліва" (Backhand Drive)
         */
        fun backhandDrive(): ExerciseParameters {
            return ExerciseParameters(
                exerciseId = "backhand_drive",
                idealWristAngle = 175f,
                wristAngleTolerance = 10f,
                minBodyRotation = 40f,
                bodyRotationTolerance = 10f,
                followThroughAngle = 130f,
                followThroughTolerance = 20f,
                contactHeightMin = 0.8f,
                contactHeightMax = 1.0f,
                minStrokeSpeed = 1.5f,
                maxStrokeSpeed = 4.0f,
                maxElbowBodyDistance = 0.2f,
                minElbowBodyDistance = 0.1f,
                movementStartThreshold = 0.5f,
                movementEndThreshold = 0.3f,
                minStrokeDuration = 400,
                maxStrokeDuration = 1500
            )
        }

        /**
         * Параметри для "Накат справа" (Beginner) - з меншими вимогами
         */
        fun forehandDriveBeginner(): ExerciseParameters {
            return ExerciseParameters(
                exerciseId = "forehand_drive_beginner",
                idealWristAngle = 180f,
                wristAngleTolerance = 60f, // Дуже вільний кут
                minBodyRotation = 10f,     // Мінімальна ротація
                bodyRotationTolerance = 30f,
                followThroughAngle = 90f,
                followThroughTolerance = 90f, // Range [0, 180] - covers all possible calculateAngle results
                contactHeightMin = -2.0f,     // Allow very low contact
                contactHeightMax = 2.0f,      // Allow very high contact
                minStrokeSpeed = 0.5f,
                maxStrokeSpeed = 10.0f,
                maxElbowBodyDistance = 0.8f, // Лікоть може бути дуже далеко
                minElbowBodyDistance = 0.0f, // No minimum for beginner
                movementStartThreshold = 0.3f,
                movementEndThreshold = 0.2f,
                minStrokeDuration = 300,
                maxStrokeDuration = 2000
            )
        }

        /**
         * Завантажити параметри з SharedPreferences
         */
        fun fromSharedPreferences(
            exerciseId: String,
            idealWristAngle: Int,
            minBodyRotation: Int,
            followThroughAngle: Int
        ): ExerciseParameters {
            return ExerciseParameters(
                exerciseId = exerciseId,
                idealWristAngle = idealWristAngle.toFloat(),
                minBodyRotation = minBodyRotation.toFloat(),
                followThroughAngle = followThroughAngle.toFloat()
            )
        }
    }

    /**
     * Перевірити чи кут зап'ястя в межах норми
     */
    fun isWristAngleValid(angle: Float): Boolean {
        return angle in (idealWristAngle - wristAngleTolerance)..(idealWristAngle + wristAngleTolerance)
    }

    /**
     * Перевірити чи ротація корпусу достатня
     */
    fun isBodyRotationValid(rotation: Float): Boolean {
        return rotation >= (minBodyRotation - bodyRotationTolerance)
    }

    /**
     * Перевірити чи follow-through правильний
     */
    fun isFollowThroughValid(angle: Float): Boolean {
        return angle in (followThroughAngle - followThroughTolerance)..(followThroughAngle + followThroughTolerance)
    }

    /**
     * Перевірити чи висота контакту правильна
     */
    fun isContactHeightValid(height: Float): Boolean {
        return height in contactHeightMin..contactHeightMax
    }

    /**
     * Перевірити чи швидкість удару в межах норми
     */
    fun isStrokeSpeedValid(speed: Float): Boolean {
        return speed in minStrokeSpeed..maxStrokeSpeed
    }

    /**
     * Перевірити чи лікоть близько до тіла
     */
    fun isElbowPositionValid(distance: Float): Boolean {
        return distance <= maxElbowBodyDistance
    }

    /**
     * Перевірити чи лікоть не занадто близько
     */
    fun isElbowNotTooClose(distance: Float): Boolean {
        return distance >= minElbowBodyDistance
    }
}
