package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.mediapipe.examples.poselandmarker.data.Exercise
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests to ensure camera and video modes don't break each other
 * These tests run on an Android device or emulator
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class CameraVideoModeIntegrationTest {

    @Test
    fun testCameraModeShowsCameraContainer() {
        // Given - exercise without video (camera mode)
        val exercise = Exercise(
            name = "Test Forehand",
            description = "Test exercise",
            videoUrl = "",
            useVideo = false
        )
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), TrainingActivity::class.java).apply {
            putExtra("exercise", exercise)
        }
        
        // When - launch activity
        ActivityScenario.launch<TrainingActivity>(intent).use { scenario ->
            // Then - camera container should be visible
            onView(withId(R.id.camera_preview_container))
                .check(matches(isDisplayed()))
            
            // And video container should be gone
            onView(withId(R.id.video_container))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test
    fun testVideoModeShowsVideoContainer() {
        // Given - exercise with video (video mode)
        val exercise = Exercise(
            name = "Test Forehand",
            description = "Test exercise",
            videoUrl = "forehand_drive",
            useVideo = true
        )
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), TrainingActivity::class.java).apply {
            putExtra("exercise", exercise)
        }
        
        // When - launch activity
        ActivityScenario.launch<TrainingActivity>(intent).use { scenario ->
            // Then - video container should be visible
            onView(withId(R.id.video_container))
                .check(matches(isDisplayed()))
            
            // And camera container should be gone
            onView(withId(R.id.camera_preview_container))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test
    fun testSwitchingFromVideoToCameraMode() {
        // Given - start with video mode
        val videoExercise = Exercise(
            name = "Video Exercise",
            description = "Test",
            videoUrl = "forehand_drive",
            useVideo = true
        )
        
        val videoIntent = Intent(ApplicationProvider.getApplicationContext(), TrainingActivity::class.java).apply {
            putExtra("exercise", videoExercise)
        }
        
        // When - launch video mode
        ActivityScenario.launch<TrainingActivity>(videoIntent).use { videoScenario ->
            // Verify video mode
            onView(withId(R.id.video_container))
                .check(matches(isDisplayed()))
        }
        
        // Then - switch to camera mode
        val cameraExercise = Exercise(
            name = "Camera Exercise",
            description = "Test",
            videoUrl = "",
            useVideo = false
        )
        
        val cameraIntent = Intent(ApplicationProvider.getApplicationContext(), TrainingActivity::class.java).apply {
            putExtra("exercise", cameraExercise)
        }
        
        ActivityScenario.launch<TrainingActivity>(cameraIntent).use { cameraScenario ->
            // Verify camera mode works
            onView(withId(R.id.camera_preview_container))
                .check(matches(isDisplayed()))
            
            onView(withId(R.id.video_container))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test
    fun testControlsAreVisibleInBothModes() {
        // Test camera mode
        val cameraExercise = Exercise(
            name = "Camera Test",
            description = "Test",
            videoUrl = "",
            useVideo = false
        )
        
        val cameraIntent = Intent(ApplicationProvider.getApplicationContext(), TrainingActivity::class.java).apply {
            putExtra("exercise", cameraExercise)
        }
        
        ActivityScenario.launch<TrainingActivity>(cameraIntent).use {
            // Verify controls exist
            onView(withId(R.id.btn_calibrate)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_start)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_stop)).check(matches(isDisplayed()))
        }
        
        // Test video mode
        val videoExercise = Exercise(
            name = "Video Test",
            description = "Test",
            videoUrl = "forehand_drive",
            useVideo = true
        )
        
        val videoIntent = Intent(ApplicationProvider.getApplicationContext(), TrainingActivity::class.java).apply {
            putExtra("exercise", videoExercise)
        }
        
        ActivityScenario.launch<TrainingActivity>(videoIntent).use {
            // Verify controls exist
            onView(withId(R.id.btn_calibrate)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_start)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_stop)).check(matches(isDisplayed()))
        }
    }
}
