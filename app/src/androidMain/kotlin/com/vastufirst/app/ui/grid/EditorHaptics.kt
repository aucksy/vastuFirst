package com.vastufirst.app.ui.grid

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The four touches the floor-plan editor speaks with (EDITOR-REWORK-PLAN.md §4.5).
 *
 * Always `View.performHapticFeedback`, never a raw `Vibrator`: the View path honours the user's
 * own system touch-feedback setting, so someone who has turned haptics off gets silence instead of
 * being buzzed by an app that decided it knew better.
 *
 * Compose 1.7.1's `HapticFeedbackType` only carries `LongPress` and `TextHandleMove` — the richer
 * set (`SegmentTick`, `Reject`, `Confirm`) arrives in Compose UI 1.8. Until that bump these route
 * through the platform constants directly, with an API guard: `REJECT` and `CONFIRM` are API 30 and
 * `minSdk` here is 26, so older phones fall back to the nearest thing they do have.
 */
@Immutable
class EditorHaptics(private val view: View) {

    /** Finger-DOWN on a room or a grip — the "you have hold of it" tap. Never fired on release. */
    fun grab() = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

    /** One click per cell crossed. Rate-limited by the caller to actual snapped-step changes. */
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    /** A refused drop — the rooms would overlap. */
    fun reject() = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS,
    )

    /** A committed placement or move. */
    fun confirm() = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.KEYBOARD_TAP,
    )
}

@Composable
fun rememberEditorHaptics(): EditorHaptics {
    val view = LocalView.current
    return remember(view) { EditorHaptics(view) }
}
