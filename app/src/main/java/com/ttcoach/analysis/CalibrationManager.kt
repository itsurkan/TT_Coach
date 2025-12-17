package com.ttcoach.analysis

import android.content.Context
import android.graphics.PointF
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlin.math.sqrt

/**
 * Manages table calibration (4 corners) and coordinate conversion
 */
class CalibrationManager(private val context: Context) {
    
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "calibration")
        
        private val CORNER_1_X = floatPreferencesKey("corner_1_x")
        private val CORNER_1_Y = floatPreferencesKey("corner_1_y")
        private val CORNER_2_X = floatPreferencesKey("corner_2_x")
        private val CORNER_2_Y = floatPreferencesKey("corner_2_y")
        private val CORNER_3_X = floatPreferencesKey("corner_3_x")
        private val CORNER_3_Y = floatPreferencesKey("corner_3_y")
        private val CORNER_4_X = floatPreferencesKey("corner_4_x")
        private val CORNER_4_Y = floatPreferencesKey("corner_4_y")
        
        // Standard table tennis table dimensions (meters)
        private const val TABLE_LENGTH = 2.74f
        private const val TABLE_WIDTH = 1.525f
    }
    
    private var corners: List<PointF>? = null
    
    /**
     * Set calibration corners (in order: top-left, top-right, bottom-right, bottom-left)
     */
    suspend fun setCorners(corner1: PointF, corner2: PointF, corner3: PointF, corner4: PointF) {
        corners = listOf(corner1, corner2, corner3, corner4)
        
        // Save to DataStore
        context.dataStore.edit { preferences ->
            preferences[CORNER_1_X] = corner1.x
            preferences[CORNER_1_Y] = corner1.y
            preferences[CORNER_2_X] = corner2.x
            preferences[CORNER_2_Y] = corner2.y
            preferences[CORNER_3_X] = corner3.x
            preferences[CORNER_3_Y] = corner3.y
            preferences[CORNER_4_X] = corner4.x
            preferences[CORNER_4_Y] = corner4.y
        }
    }
    
    /**
     * Load saved calibration
     */
    suspend fun loadCalibration() {
        val preferences = context.dataStore.data.first()
        val corner1 = PointF(
            preferences[CORNER_1_X] ?: 0f,
            preferences[CORNER_1_Y] ?: 0f
        )
        val corner2 = PointF(
            preferences[CORNER_2_X] ?: 0f,
            preferences[CORNER_2_Y] ?: 0f
        )
        val corner3 = PointF(
            preferences[CORNER_3_X] ?: 0f,
            preferences[CORNER_3_Y] ?: 0f
        )
        val corner4 = PointF(
            preferences[CORNER_4_X] ?: 0f,
            preferences[CORNER_4_Y] ?: 0f
        )
        
        if (corner1.x != 0f || corner1.y != 0f) {
            corners = listOf(corner1, corner2, corner3, corner4)
        }
    }
    
    /**
     * Check if calibration is complete
     */
    fun isCalibrated(): Boolean {
        return corners != null && corners!!.size == 4
    }
    
    /**
     * Get calibration corners
     */
    fun getCorners(): List<PointF>? = corners
    
    /**
     * Calculate table bounding box from corners
     */
    fun getTableBounds(): android.graphics.RectF? {
        val corners = this.corners ?: return null
        
        val minX = corners.minOfOrNull { it.x } ?: 0f
        val minY = corners.minOfOrNull { it.y } ?: 0f
        val maxX = corners.maxOfOrNull { it.x } ?: 0f
        val maxY = corners.maxOfOrNull { it.y } ?: 0f
        
        return android.graphics.RectF(minX, minY, maxX, maxY)
    }
    
    /**
     * Convert pixel coordinates to real-world meters
     * @param pixelPoint Point in pixel coordinates
     * @return Point in meters (relative to table)
     */
    fun pixelToMeters(pixelPoint: PointF): PointF? {
        val corners = this.corners ?: return null
        val bounds = getTableBounds() ?: return null
        
        // Calculate scale factors
        val pixelWidth = bounds.width()
        val pixelHeight = bounds.height()
        
        val scaleX = TABLE_WIDTH / pixelWidth
        val scaleY = TABLE_LENGTH / pixelHeight
        
        // Convert relative to top-left corner
        val relativeX = pixelPoint.x - bounds.left
        val relativeY = pixelPoint.y - bounds.top
        
        return PointF(relativeX * scaleX, relativeY * scaleY)
    }
    
    /**
     * Convert meters to pixel coordinates
     */
    fun metersToPixel(meterPoint: PointF): PointF? {
        val bounds = getTableBounds() ?: return null
        
        val pixelWidth = bounds.width()
        val pixelHeight = bounds.height()
        
        val scaleX = pixelWidth / TABLE_WIDTH
        val scaleY = pixelHeight / TABLE_LENGTH
        
        val pixelX = bounds.left + (meterPoint.x * scaleX)
        val pixelY = bounds.top + (meterPoint.y * scaleY)
        
        return PointF(pixelX, pixelY)
    }
    
    /**
     * Calculate distance in meters between two pixel points
     */
    fun pixelDistanceToMeters(point1: PointF, point2: PointF): Float? {
        val meter1 = pixelToMeters(point1) ?: return null
        val meter2 = pixelToMeters(point2) ?: return null
        
        val dx = meter2.x - meter1.x
        val dy = meter2.y - meter1.y
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Clear calibration
     */
    suspend fun clearCalibration() {
        corners = null
        context.dataStore.edit { it.clear() }
    }
}

