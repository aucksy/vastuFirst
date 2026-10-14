package com.vastufirst.app.ui.grid

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Immutable
class EditorHaptics(private val view: View) : EditorFeedback {

    override fun grab() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    override fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    override fun reject() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS,
        )
    }

    override fun confirm() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.KEYBOARD_TAP,
        )
    }
}

@Composable
actual fun rememberEditorHaptics(): EditorFeedback {
    val view = LocalView.current
    return remember(view) { EditorHaptics(view) }
}
