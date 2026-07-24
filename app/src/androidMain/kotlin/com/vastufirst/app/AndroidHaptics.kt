package com.vastufirst.app

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.vastufirst.designsystem.foundation.VastuHaptics

/**
 * The Android implementation of the app's [VastuHaptics] vocabulary. Always routes through
 * `View.performHapticFeedback` (never a raw Vibrator) so it honours the user's own system
 * touch-feedback setting — haptics off means silence, not an app that overrides the choice.
 *
 * Compose 1.7.1's `HapticFeedbackType` only carries LongPress/TextHandleMove, so the crisper
 * constants (CONFIRM, REJECT — API 30) and the light KEYBOARD_TAP / CLOCK_TICK are used directly,
 * with an API guard for the two that need it (minSdk 26 falls back to the nearest older constant).
 */
class AndroidVastuHaptics(private val view: View) : VastuHaptics {
    override fun tap() { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    override fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    override fun confirm() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.KEYBOARD_TAP,
        )
    }
    override fun reject() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS,
        )
    }
}

@Composable
fun rememberAndroidVastuHaptics(): VastuHaptics {
    val view = LocalView.current
    return remember(view) { AndroidVastuHaptics(view) }
}
