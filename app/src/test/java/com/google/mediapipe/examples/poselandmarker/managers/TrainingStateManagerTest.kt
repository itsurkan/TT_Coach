package com.google.mediapipe.examples.poselandmarker.managers

import android.content.Context
import com.google.mediapipe.examples.poselandmarker.models.AnalysisResult
import com.google.mediapipe.examples.poselandmarker.models.StrokePhase
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
}

