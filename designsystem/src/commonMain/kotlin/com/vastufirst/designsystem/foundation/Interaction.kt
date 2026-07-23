package com.vastufirst.designsystem.foundation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Owned tap modifier: a clickable with NO Material ripple (components draw their own pressed
 * state from tokens) that still exposes the [interactionSource] so a component can react to
 * press, plus a click [Role] for accessibility. Keeps every control off Material's indication
 * so the iOS re-skin is mechanical (Impl PRD §7).
 */
@Composable
fun Modifier.clickableTap(
    enabled: Boolean = true,
    role: Role? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    role = role,
    onClick = onClick,
)

/** Attach a content description (accessibility) to any node — used on graphics like the zone map. */
fun Modifier.semanticsLabel(description: String): Modifier =
    semantics { contentDescription = description }
