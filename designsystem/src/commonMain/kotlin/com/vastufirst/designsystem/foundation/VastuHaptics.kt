package com.vastufirst.designsystem.foundation

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The app's haptic vocabulary, provided as a CompositionLocal so any design-system control can speak
 * it without depending on an Android type (this module is Compose Multiplatform). The real
 * implementation lives in the app layer and routes to `View.performHapticFeedback`, which honours the
 * user's own system touch-feedback setting — someone who has turned haptics off gets silence.
 *
 * The design's haptic map (Sage & Gold design system): "tick on quick-set chips; soft detent on the
 * dial; success on unlock." [tap] is the everyday discrete press (every button/chip/row, via
 * clickableTap); [tick] is the fine detent while dragging the North dial or the slider; [confirm] is
 * a success (a committed action); [reject] is a refused one.
 *
 * The default is [None] (no-op), so screenshot/host renders and any non-Android target are silent and
 * safe. The app overrides it once at the root.
 */
interface VastuHaptics {
    fun tap()
    fun tick()
    fun confirm()
    fun reject()

    object None : VastuHaptics {
        override fun tap() {}
        override fun tick() {}
        override fun confirm() {}
        override fun reject() {}
    }
}

val LocalVastuHaptics = staticCompositionLocalOf<VastuHaptics> { VastuHaptics.None }
