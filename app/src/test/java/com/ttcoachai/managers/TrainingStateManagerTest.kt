package com.ttcoachai.managers

import android.content.Context
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.StrokePhase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Unit tests for TrainingStateManager
 */
class TrainingStateManagerTest {

    private lateinit var stateManager: TrainingStateManager
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockContext = mock(Context::class.java)
        stateManager = TrainingStateManager(mockContext)
    }

    @Test
    fun testInitialStateIsStopped() {
        assertFalse(stateManager.isTrainingActive)
    }

    @Test
    fun testStartTrainingChangesState() {
        stateManager.startTraining()
        assertTrue(stateManager.isTrainingActive)
    }

    @Test
    fun testStopTrainingChangesState() {
        stateManager.startTraining()
        stateManager.stopTraining()
        assertFalse(stateManager.isTrainingActive)
    }

    @Test
    fun testStrokeCountStartsAtZero() {
        assertEquals(0, stateManager.getStrokeCount())
    }

    @Test
    fun testAddingStrokeIncreasesCount() {
        val result = AnalysisResult(
            overallScore = 85.0f,
            phase = StrokePhase.CONTACT
        )
        
        stateManager.addAnalysisResult(result)
        assertEquals(1, stateManager.getStrokeCount())
    }

    @Test
    fun testAverageScoreWithNoStrokesReturnsZero() {
        assertEquals(0.0, stateManager.getAverageScore(), 0.01)
    }

    @Test
    fun testAverageScoreCalculation() {
        stateManager.addAnalysisResult(AnalysisResult(overallScore = 80.0f))
        stateManager.addAnalysisResult(AnalysisResult(overallScore = 90.0f))
        stateManager.addAnalysisResult(AnalysisResult(overallScore = 70.0f))
        
        assertEquals(80.0, stateManager.getAverageScore(), 0.01)
    }

    @Test
    fun testResetClearsAllAnalysisResults() {
        stateManager.addAnalysisResult(AnalysisResult(overallScore = 85.0f))
        stateManager.addAnalysisResult(AnalysisResult(overallScore = 75.0f))
        
        assertEquals(2, stateManager.getStrokeCount())
        
        stateManager.reset()
        
        assertEquals(0, stateManager.getStrokeCount())
        assertEquals(0.0, stateManager.getAverageScore(), 0.01)
    }

    @Test
    fun testResetAlsoClearsTrainingState() {
        stateManager.startTraining()
        stateManager.reset()
        assertFalse(stateManager.isTrainingActive)
    }

    @Test
    fun testGetAllResultsReturnsCorrectList() {
        val result1 = AnalysisResult(overallScore = 85.0f)
        val result2 = AnalysisResult(overallScore = 75.0f)
        
        stateManager.addAnalysisResult(result1)
        stateManager.addAnalysisResult(result2)
        
        val results = stateManager.getAnalysisResults()
        assertEquals(2, results.size)
        assertEquals(85.0f, results[0].overallScore, 0.01f)
        assertEquals(75.0f, results[1].overallScore, 0.01f)
    }

    @Test
    fun testGoodStrokesCount() {
        stateManager.addAnalysisResult(AnalysisResult(overallScore = 85.0f))
        stateManager.addAnalysisResult(AnalysisResult(overallScore = 75.0f))
        stateManager.addAnalysisResult(AnalysisResult(overallScore = 90.0f))
        
        assertEquals(2, stateManager.getGoodStrokesCount())
    }

    @Test
    fun testConsecutiveGoodStrokes() {
        stateManager.addAnalysisResult(AnalysisResult(overallScore = 85.0f))
        assertEquals(1, stateManager.consecutiveGoodStrokes)
        
        stateManager.addAnalysisResult(AnalysisResult(overallScore = 90.0f))
        assertEquals(2, stateManager.consecutiveGoodStrokes)
        
        stateManager.addAnalysisResult(AnalysisResult(overallScore = 70.0f))
        assertEquals(0, stateManager.consecutiveGoodStrokes)
    }

    @Test
    fun testFeedbackHistory() {
        stateManager.addFeedback("Good stroke")
        stateManager.addFeedback("Improve follow-through")
        
        val history = stateManager.getFeedbackHistory()
        assertEquals(2, history.size)
        assertEquals("Good stroke", history[0])
        assertEquals("Improve follow-through", history[1])
    }

    @Test
    fun testFeedbackHistoryLimitedToTenItems() {
        for (i in 1..15) {
            stateManager.addFeedback("Feedback $i")
        }
        
        val history = stateManager.getFeedbackHistory()
        assertEquals(10, history.size)
        assertEquals("Feedback 6", history[0]) // First item should be 6, not 1
    }

    // ---- rep-pose ring buffer (RTM feedback-explanation snapshot data layer) ----

    private fun kp(x: Float) = listOf(Keypoint2D(x, 0.5f, 1f))

    @Test
    fun testAddRepPosesStoresCapture() {
        val start = kp(0.1f)
        val end = kp(0.9f)

        stateManager.addRepPoses(atMs = 1234L, start = start, end = end)

        val poses = stateManager.getRepPoses()
        assertEquals(1, poses.size)
        assertEquals(1234L, poses[0].atMs)
        assertEquals(start, poses[0].start)
        assertEquals(end, poses[0].end)
        assertTrue(poses[0].flaggedTypes.isEmpty())
    }

    @Test
    fun testRepPosesLimitedToTenItems() {
        for (i in 1..15) {
            stateManager.addRepPoses(atMs = i.toLong(), start = kp(i.toFloat()), end = kp(i.toFloat()))
        }

        val poses = stateManager.getRepPoses()
        assertEquals(10, poses.size)
        assertEquals(6L, poses[0].atMs) // first surviving capture should be #6, not #1
        assertEquals(15L, poses[9].atMs)
    }

    @Test
    fun testFlagLatestRepPoseMarksOnlyTheLastCapture() {
        stateManager.addRepPoses(atMs = 1L, start = kp(0f), end = kp(0f))
        stateManager.addRepPoses(atMs = 2L, start = kp(0f), end = kp(0f))

        stateManager.flagLatestRepPose(CorrectionType.ELBOW_BEND)

        val poses = stateManager.getRepPoses()
        assertTrue(poses[0].flaggedTypes.isEmpty())
        assertEquals(setOf(CorrectionType.ELBOW_BEND), poses[1].flaggedTypes)
    }

    @Test
    fun testFlagLatestRepPoseIsNoOpWhenHistoryEmpty() {
        stateManager.flagLatestRepPose(CorrectionType.POSTURE)
        assertTrue(stateManager.getRepPoses().isEmpty())
    }
}

