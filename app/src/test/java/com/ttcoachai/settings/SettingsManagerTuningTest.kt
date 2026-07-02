package com.ttcoachai.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ttcoachai.managers.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class SettingsManagerTuningTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun detection_defaults_match_real_code() {
        val sm = SettingsManager(ctx)
        assertEquals(0, sm.getDetCameraAngle())
        assertEquals(1.0f, sm.getDetPeakSpeed(), 0.0001f)
        assertEquals(500, sm.getDetMinPeakIntervalMs())
        assertEquals(300, sm.getDetSpeedSmoothingMs())
        assertEquals(0.4f, sm.getDetWalkGate(), 0.0001f)
        assertEquals(true, sm.isDetSkipStaleReps())
        assertEquals(1000, sm.getDetPreStrokeBufferMs())
    }

    @Test fun feedback_defaults_match_real_code() {
        val sm = SettingsManager(ctx)
        assertEquals(1.4f, sm.getFbZoneWidth(), 0.0001f)
        assertEquals(7, sm.getFbSignificanceDeg())
        assertEquals(10000, sm.getFbReminderIntervalMs())
        assertEquals(true, sm.isFbAlternateCues())
        assertEquals(5000, sm.getFbPauseBetweenMs())
        assertEquals(10000, sm.getFbSilenceBeforePraiseMs())
        assertEquals(300, sm.getFbPauseAfterStrokeMs())
        assertEquals(true, sm.isPraiseEnabled())
        assertEquals(false, sm.isPraiseOnStreak())
        assertEquals(3, sm.getPraiseStreakLen())
    }

    @Test fun round_trip_persists() {
        val sm = SettingsManager(ctx)
        sm.setDetPeakSpeed(1.5f)
        sm.setFbSignificanceDeg(9)
        sm.setCoachLanguage("uk")
        val sm2 = SettingsManager(ctx)
        assertEquals(1.5f, sm2.getDetPeakSpeed(), 0.0001f)
        assertEquals(9, sm2.getFbSignificanceDeg())
        assertEquals("uk", sm2.getCoachLanguage())
    }
}
