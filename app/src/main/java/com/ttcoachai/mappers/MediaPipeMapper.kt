package com.ttcoachai.mappers

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.ttcoachai.shared.models.Landmark3D

/**
 * Converts MediaPipe types to shared Landmark3D/PoseFrame types.
 * This is the boundary between the Android platform and the shared module.
 */
object MediaPipeMapper {

    /**
     * Convert a MediaPipe NormalizedLandmark to shared Landmark3D.
     * Maps x→x, y→y, z→z, visibility→visibility, presence→presence with no transformation.
     */
    fun toLandmark3D(landmark: NormalizedLandmark): Landmark3D = Landmark3D(
        x = landmark.x(),
        y = landmark.y(),
        z = landmark.z(),
        visibility = landmark.visibility().orElse(0f),
        presence = landmark.presence().orElse(0f)
    )

    /**
     * Convert a full PoseLandmarkerResult to a list of Landmark3D (normalized screen coordinates).
     * Returns empty list if the result has no landmarks.
     * Preserves MediaPipe landmark ordering (index 0-32).
     */
    fun toLandmarkList(result: PoseLandmarkerResult): List<Landmark3D> {
        val landmarkList = result.landmarks().firstOrNull() ?: return emptyList()
        return landmarkList.map { toLandmark3D(it) }
    }

    /**
     * Convert world landmarks from a PoseLandmarkerResult to a list of Landmark3D.
     * World landmarks are in metric units (meters), preferred for phase detection.
     * Falls back to normalized landmarks if world landmarks are unavailable.
     * Returns empty list if no landmarks available.
     */
    fun toWorldLandmarkList(result: PoseLandmarkerResult): List<Landmark3D> {
        val worldList = result.worldLandmarks().firstOrNull()
        if (!worldList.isNullOrEmpty()) {
            return worldList.map { lm ->
                Landmark3D(
                    x = lm.x(),
                    y = lm.y(),
                    z = lm.z(),
                    visibility = lm.visibility().orElse(0f),
                    presence = lm.presence().orElse(0f)
                )
            }
        }
        return toLandmarkList(result)
    }
}
