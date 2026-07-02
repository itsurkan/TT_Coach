package com.ttcoachai.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import com.ttcoachai.TTCoachApplication
import com.ttcoachai.models.SessionAnalyticsEntity
import com.ttcoachai.models.TrainingSession
import com.ttcoachai.shared.analysis.SessionAnalyticsBuilder
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.FeedbackItem

/**
 * Inserts a few representative sessions + matching analytics so the History/Review
 * screens can be eyeballed without recording a real session. FLAG_DEBUGGABLE-gated
 * (mirrors DesignSystemPreviewActivity); a no-op on release builds.
 */
object SessionAnalyticsSeeder {

    suspend fun seed(context: Context) {
        val app = context.applicationContext as TTCoachApplication
        if (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val userId = app.cloudSyncManager.currentUserId ?: "debug_user"
        val trainingDao = app.database.trainingDao()
        val analyticsDao = app.database.sessionAnalyticsDao()
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000

        val specs = listOf(
            Triple("Forehand drive", 0.87f, 1L),
            Triple("Forehand drive", 0.79f, 3L),
            Triple("Backhand drive", 0.83f, 9L),
            Triple("Topspin", 0.72f, 32L),
        )

        specs.forEachIndexed { i, (name, acc, daysAgo) ->
            val id = "seed_${i}_${now}"
            val start = now - daysAgo * day
            val duration = 600 + i * 120
            val session = TrainingSession(
                id = id, userId = userId, exerciseId = "seed", exerciseName = name,
                startTime = start, endTime = start + duration * 1000L,
                durationSeconds = duration, strokeCount = 120, correctStrokes = (120 * acc).toInt(),
                accuracy = acc, isSynced = true
            )
            trainingDao.insertSession(session)

            val results = (1..120).map { k ->
                val score = if (k <= (120 * acc).toInt()) 90f else 60f
                AnalysisResult(timestamp = start + k * 1000L, overallScore = score)
            }
            val feedback = listOf(
                FeedbackItem("m", CorrectionType.WRIST),
                FeedbackItem("m", CorrectionType.WRIST),
                FeedbackItem("m", CorrectionType.BODY_ROTATION),
                FeedbackItem("m", CorrectionType.ELBOW_POSITION),
                FeedbackItem("m", CorrectionType.FOLLOW_THROUGH),
                FeedbackItem("m", CorrectionType.CONTACT_HEIGHT),
            )
            val analytics = SessionAnalyticsBuilder.build(id, results, feedback)
            analyticsDao.upsert(SessionAnalyticsEntity.fromDomain(analytics, now))
        }
    }
}
