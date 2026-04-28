package com.ttcoachai

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests to ensure camera and video modes don't break each other.
 * These tests run on an Android device or emulator.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class CameraVideoModeIntegrationTest {

    private fun cameraExercise(name: String) = Exercise(
        id = "test-$name",
        name = name,
        description = "Test exercise",
        difficulty = "Beginner",
        duration = "5 min",
        useVideo = false
    )

    private fun videoExercise(name: String) = Exercise(
        id = "test-$name",
        name = name,
        description = "Test exercise",
        difficulty = "Beginner",
        duration = "5 min",
        useVideo = true
    )

    @Test
    fun testCameraModeShowsCameraContainer() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), TrainingActivity::class.java).apply {
            putExtra("exercise_id", cameraExercise("Test Forehand").id)
        }

        ActivityScenario.launch<TrainingActivity>(intent).use {
            onView(withId(R.id.camera_preview_container))
                .check(matches(isDisplayed()))

            onView(withId(R.id.video_container))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test
    fun testVideoModeShowsVideoContainer() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), TrainingActivity::class.java).apply {
            putExtra("exercise_id", videoExercise("Test Forehand").id)
        }

        ActivityScenario.launch<TrainingActivity>(intent).use {
            onView(withId(R.id.video_container))
                .check(matches(isDisplayed()))

            onView(withId(R.id.camera_preview_container))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test
    fun testSwitchingFromVideoToCameraMode() {
        val videoIntent = Intent(ApplicationProvider.getApplicationContext(), TrainingActivity::class.java).apply {
            putExtra("exercise_id", videoExercise("Video Exercise").id)
        }

        ActivityScenario.launch<TrainingActivity>(videoIntent).use {
            onView(withId(R.id.video_container))
                .check(matches(isDisplayed()))
        }

        val cameraIntent = Intent(ApplicationProvider.getApplicationContext(), TrainingActivity::class.java).apply {
            putExtra("exercise_id", cameraExercise("Camera Exercise").id)
        }

        ActivityScenario.launch<TrainingActivity>(cameraIntent).use {
            onView(withId(R.id.camera_preview_container))
                .check(matches(isDisplayed()))

            onView(withId(R.id.video_container))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

}
