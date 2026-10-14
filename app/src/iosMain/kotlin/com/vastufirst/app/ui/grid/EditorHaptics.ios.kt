package com.vastufirst.app.ui.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * The editor's four touches on the iPhone, from Apple's feedback generators — the same four meanings
 * Android's View haptics carry: grab (a firm tap), tick (a selection click per cell), reject (the error
 * pattern), confirm (the success pattern).
 */
@Composable
actual fun rememberEditorHaptics(): EditorFeedback = remember { IosEditorHaptics() }

private class IosEditorHaptics : EditorFeedback {
    private val impact = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val selection = UISelectionFeedbackGenerator()
    private val notification = UINotificationFeedbackGenerator()

    override fun grab() = impact.impactOccurred()
    override fun tick() = selection.selectionChanged()
    override fun reject() = notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
    override fun confirm() = notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
}
