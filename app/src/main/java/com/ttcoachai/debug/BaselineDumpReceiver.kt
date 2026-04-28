package com.ttcoachai.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import com.ttcoachai.calibration.CalibrationActivity
import com.ttcoachai.db.AppDatabase
import com.ttcoachai.db.BaselineConverters
import com.ttcoachai.repository.PersonalBaselineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Dev-only hook for dumping the active baseline via:
 *
 *   adb shell am broadcast -a com.ttcoachai.debug.DUMP_BASELINE \
 *     [--es drill_type forehand_shadow]
 *
 * Output is logged under the "BaselineDump" tag (check `adb logcat -s BaselineDump:V`).
 * No-op on release builds — the `FLAG_DEBUGGABLE` check keeps this from running
 * on user-installed APKs even though the receiver remains registered in the manifest.
 */
class BaselineDumpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appCtx = context.applicationContext
        if (appCtx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            Log.w(TAG, "Refusing dump: APK is not debuggable")
            return
        }
        if (intent.action != ACTION) return

        val drillType = intent.getStringExtra(EXTRA_DRILL_TYPE)
            ?: CalibrationActivity.DRILL_FOREHAND_SHADOW
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = PersonalBaselineRepository(
                    AppDatabase.getDatabase(appCtx).personalBaselineDao()
                )
                val baseline = repo.getActiveBaseline(drillType).first()
                if (baseline == null) {
                    Log.i(TAG, "No active baseline for drillType=$drillType")
                    return@launch
                }
                val json = JSONObject().apply {
                    put("drillType", baseline.drillType)
                    put("createdAtMs", baseline.createdAtMs)
                    put("repCount", baseline.repCount)
                    put("qualityScore", baseline.qualityScore)
                    put("handedness", baseline.drillerHandedness ?: JSONObject.NULL)
                    put("excludedRepIndices", BaselineConverters.intListToJson(baseline.excludedRepIndices))
                    put("metricStats", JSONObject(BaselineConverters.metricStatsMapToJson(baseline.metricStats)))
                    put("phaseDurationsMs", JSONObject(BaselineConverters.metricStatsMapToJson(baseline.phaseDurationsMs)))
                }
                Log.i(TAG, json.toString(2))
            } catch (e: Exception) {
                Log.e(TAG, "Dump failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.ttcoachai.debug.DUMP_BASELINE"
        const val EXTRA_DRILL_TYPE = "drill_type"
        private const val TAG = "BaselineDump"
    }
}
