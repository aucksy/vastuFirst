package com.vastufirst.app.ui.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * The four touches the floor-plan editor speaks with (EDITOR-REWORK-PLAN.md §4.5), as a contract.
 *
 * ⭐ An interface, not the class, so a headless test can hand the editor a COUNTING fake and prove
 * that a key which cannot act says "no" — the plot keys, the move arrows and the size steppers at a
 * wall. Until this seam existed, "the arrow buzzes at the wall" was a claim only a finger could
 * check, and the arrows and steppers in fact did NOT buzz while the plot keys did: three controls
 * doing the same job, one of them silently. The phone gets [EditorHaptics]; a test gets whatever
 * it likes.
 */
@Immutable
interface EditorFeedback {
    /** Finger-DOWN on a room or a grip — the "you have hold of it" tap. Never fired on release. */
    fun grab()

    /** One click per cell crossed. Rate-limited by the caller to actual snapped-step changes. */
    fun tick()

    /** A refused action — an overlap, or a key pressed against a wall or a limit. */
    fun reject()

    /** A committed placement or move. */
    fun confirm()
}

/**
 * The phone's own touches.
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
/** The phone's own touches for the editor (Android: View haptics; iPhone: its feedback generators). */
@Composable
expect fun rememberEditorHaptics(): EditorFeedback
