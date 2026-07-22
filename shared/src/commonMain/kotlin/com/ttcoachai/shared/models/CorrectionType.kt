package com.ttcoachai.shared.models

enum class CorrectionType {
    WRIST,
    BODY_ROTATION,
    FOLLOW_THROUGH,
    CONTACT_HEIGHT,
    ELBOW_POSITION,
    ELBOW_BEND,       // elbow flexion (shoulder-elbow-wrist), RTM-only
    STROKE_SPEED,
    KNEE_BEND,
    POSTURE,          // torso lean / spine vs vertical, RTM-only
    GENERAL
}
