package com.ttcoachai.processors

import com.ttcoachai.TTCoachApplication
import com.ttcoachai.core.logging.LandmarkData
import com.ttcoachai.models.AnalysisResult

/**
 * Handles logging of pose analysis results and raw landmarks.
 */
class PoseAnalysisLogger(
    private val application: TTCoachApplication
) {
    fun logAnalysis(sessionId: String?, frameCounter: Int, result: AnalysisResult, inferenceTime: Long) {
        sessionId?.let { id ->
            val logger = com.ttcoachai.core.logging.LogManager.getLogger(application)
            logger.logStrokeAnalysis(result, id, inferenceTime, frameCounter)
            logger.logPerformanceMetric("inference_time_ms", inferenceTime.toFloat(), id)
        }
    }

    fun logRawPose(sessionId: String?, frameCounter: Int, result: com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult, inferenceTime: Long) {
        sessionId?.let { id ->
            val logger = com.ttcoachai.core.logging.LogManager.getLogger(application)
            val landmarks = result.landmarks().firstOrNull()?.map { l ->
                LandmarkData(l.x(), l.y(), l.z(), l.visibility().orElse(0f), l.presence().orElse(0f))
            } ?: return
            
            val worldLandmarks = result.worldLandmarks().firstOrNull()?.map { l ->
                LandmarkData(l.x(), l.y(), l.z(), l.visibility().orElse(0f), l.presence().orElse(0f))
            }
            
            logger.logRawPose(id, frameCounter, inferenceTime, landmarks, worldLandmarks)
        }
    }
}
