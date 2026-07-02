package com.ttcoachai.ui

/**
 * Pure, Android-free settle logic for [SwipeRevealLayout]. Kept in its own file with no
 * Android imports so it is unit-testable on the JVM without Robolectric.
 *
 * Sign convention: a foreground offset / velocity that is NEGATIVE means the foreground was
 * dragged LEFT, exposing the Clone panel on the right edge; POSITIVE means dragged RIGHT,
 * exposing the Delete panel on the left edge.
 */

enum class SwipeState { CLOSED, OPEN_CLONE, OPEN_DELETE }

/** Default release fling speed (px/s) above which a swipe opens regardless of distance. */
const val DEFAULT_FLING_VELOCITY: Float = 1000f

/**
 * Clamp the foreground's left offset to the range permitted by which sides are enabled.
 * Clone (left drag) is always allowed; Delete (right drag) only when [deleteEnabled].
 */
fun clampSwipeOffset(offsetX: Float, buttonWidth: Float, deleteEnabled: Boolean): Float {
    val min = -buttonWidth
    val max = if (deleteEnabled) buttonWidth else 0f
    return offsetX.coerceIn(min, max)
}

/**
 * Decide which state a released drag settles to. Crossing ~half the button width, or a fling
 * faster than [flingVelocityThreshold] in that direction, opens the side; otherwise it closes.
 * A blocked side (Delete when [deleteEnabled] is false) can only resolve to [SwipeState.CLOSED].
 */
fun decideSwipeSettle(
    offsetX: Float,
    velocityX: Float,
    buttonWidth: Float,
    deleteEnabled: Boolean,
    flingVelocityThreshold: Float = DEFAULT_FLING_VELOCITY,
): SwipeState {
    val half = buttonWidth / 2f
    if (offsetX < 0f) {
        val open = -offsetX >= half || velocityX <= -flingVelocityThreshold
        return if (open) SwipeState.OPEN_CLONE else SwipeState.CLOSED
    }
    if (offsetX > 0f && deleteEnabled) {
        val open = offsetX >= half || velocityX >= flingVelocityThreshold
        return if (open) SwipeState.OPEN_DELETE else SwipeState.CLOSED
    }
    return SwipeState.CLOSED
}
