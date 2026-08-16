package com.vastufirst.designsystem.foundation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** How much the content dims while held down — the default owned pressed state (see [clickableTap]). */
private const val PRESSED_ALPHA = 0.55f

/**
 * Owned tap modifier: a clickable with NO Material ripple (components draw their own pressed
 * state from tokens) that still exposes the [interactionSource] so a component can react to
 * press, plus a click [Role] for accessibility. Keeps every control off Material's indication
 * so the iOS re-skin is mechanical (Impl PRD §7).
 *
 * Because the ripple is suppressed, the design owes EVERY control a visible pressed state
 * (UI-POLISH §3.F). So by default this dims the control's content while held ([pressEffect]) — a
 * single owned press feedback for chips, list rows, icon buttons, cards and segments, none of which
 * previously reacted to touch. Components that already render a richer pressed state of their own
 * (the buttons swap their fill) pass `pressEffect = false` to avoid double-dimming.
 */
@Composable
fun Modifier.clickableTap(
    enabled: Boolean = true,
    role: Role? = null,
    /**
     * What activating this control will DO, for a screen reader — TalkBack speaks it as
     * "double tap to <label>". Say the outcome ("change the room type"), never the gesture.
     *
     * ⚠ This exists because putting the gesture in a `contentDescription` instead is an
     * accessibility DEFECT, not merely a style choice: Google's ATF `RedundantDescriptionCheck`
     * flags it, since TalkBack already announces the role and the gesture, so the user hears
     * "double tap" twice. It fired eleven times — once per room row — the first time a row on the
     * scan confirmation list was made tappable.
     */
    onClickLabel: String? = null,
    pressEffect: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
): Modifier {
    // One light tap haptic for every discrete control in the app — buttons, chips, icon buttons,
    // list rows and cards all route through here (VastuHaptics.None is a no-op off-device).
    val haptics = LocalVastuHaptics.current
    val pressed by interactionSource.collectIsPressedAsState()
    return this
        .then(
            if (pressEffect) Modifier.graphicsLayer { alpha = if (pressed && enabled) PRESSED_ALPHA else 1f }
            else Modifier
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = { haptics.tap(); onClick() },
        )
}

/**
 * ⭐ TAP, PLUS PRESS-AND-HOLD — the same owned tap as [clickableTap], with a long press on top.
 *
 * ⚠ It exists as its own modifier rather than a flag on [clickableTap] because a long press is not
 * a decoration: `combinedClickable` changes how a tap is DETECTED (it waits to find out whether the
 * finger is going to stay), so every control in the app must not silently acquire that wait. Only
 * the controls that really offer a hold use this one.
 *
 * ⚠ [onLongClickLabel] is not optional in practice. TalkBack offers a long press as a separate
 * action and reads this sentence out as its name; without it a screen-reader user is told there is a
 * second action and not what it does. Say the OUTCOME ("delete this home"), never the gesture.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.clickableTapAndHold(
    enabled: Boolean = true,
    role: Role? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    pressEffect: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onLongClick: () -> Unit,
    onClick: () -> Unit,
): Modifier {
    val haptics = LocalVastuHaptics.current
    val pressed by interactionSource.collectIsPressedAsState()
    return this
        .then(
            if (pressEffect) Modifier.graphicsLayer { alpha = if (pressed && enabled) PRESSED_ALPHA else 1f }
            else Modifier
        )
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onLongClickLabel = onLongClickLabel,
            // ⚠ `confirm`, not `tap`. A hold has to be FELT the moment it registers — the finger is
            // still on the screen and the dialog it opens has not been drawn yet, so without a
            // distinct buzz the reader keeps holding and then sees a box they did not connect to
            // their own hand. It is the app's existing "a committed action happened" haptic.
            onLongClick = { haptics.confirm(); onLongClick() },
            onClick = { haptics.tap(); onClick() },
        )
}

/** Attach a content description (accessibility) to any node — used on graphics like the zone map. */
fun Modifier.semanticsLabel(description: String): Modifier =
    semantics { contentDescription = description }
