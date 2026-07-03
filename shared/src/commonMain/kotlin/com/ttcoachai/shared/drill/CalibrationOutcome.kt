package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.PersonalBaseline

/**
 * Result of a safe calibration attempt, designed for FFI and Swift interop.
 * Wraps the existing DrillCalibrator.calibrate() to prevent Kotlin exceptions
 * from crashing Swift bridge code.
 *
 * Three outcomes:
 * - Success: baseline derived successfully; proceed to feedback sessions
 * - PlacementError: camera placement gate failed (|yaw| > ~30°, or unmeasurable yaw);
 *   user should reposition camera and re-record calibration
 * - Failed: other errors (insufficient reps after filtering, derivation logic error);
 *   retry the whole calibration
 */
sealed class CalibrationOutcome {
  data class Success(val baseline: PersonalBaseline) : CalibrationOutcome()
  data class PlacementError(val message: String) : CalibrationOutcome()
  data class Failed(val message: String) : CalibrationOutcome()
}
