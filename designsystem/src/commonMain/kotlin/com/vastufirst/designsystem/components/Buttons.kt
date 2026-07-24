package com.vastufirst.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * Button variants (design system §06 button table). All are pill-shaped (radius-full), the
 * a11y touch floor tall, and read every colour/size from tokens. Pressed and disabled states
 * are both realised (review gate: "renders its correct pressed / disabled state").
 */
enum class VastuButtonStyle { PRIMARY, SECONDARY }

@Composable
fun VastuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: VastuButtonStyle = VastuButtonStyle.PRIMARY,
    enabled: Boolean = true,
    large: Boolean = true,
) {
    val colors = VastuTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val bg = when {
        !enabled -> colors.borderDefault
        style == VastuButtonStyle.PRIMARY -> if (pressed) colors.primaryDark else colors.primary
        else -> colors.paper
    }
    val fg = when {
        !enabled -> colors.textTertiary
        style == VastuButtonStyle.PRIMARY -> colors.onPrimary
        else -> colors.textPrimary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (large) VastuTheme.sizes.cta else VastuTheme.sizes.control)
            .graphicsLayer { alpha = if (pressed && enabled) 0.92f else 1f }
            .clip(VastuTheme.shapes.full)
            .background(bg)
            .then(
                if (style == VastuButtonStyle.SECONDARY)
                    Modifier.border(VastuTheme.borders.regular, colors.borderStrong, VastuTheme.shapes.full)
                else Modifier
            )
            // pressEffect = false: this button owns a richer pressed state (fill swap + graphicsLayer above).
            .clickableTap(enabled = enabled, role = Role.Button, pressEffect = false, interactionSource = interaction, onClick = onClick)
            .padding(horizontal = VastuTheme.spacing.s4, vertical = VastuTheme.spacing.s2),
        contentAlignment = Alignment.Center,
    ) {
        VText(text = text, style = VastuTheme.type.label, color = fg, align = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** A pill that is not full-width — used inside button rows (Back / Continue). */
@Composable
fun VastuButtonInline(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: VastuButtonStyle = VastuButtonStyle.PRIMARY,
    enabled: Boolean = true,
) {
    val colors = VastuTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = when {
        !enabled -> colors.borderDefault
        style == VastuButtonStyle.PRIMARY -> if (pressed) colors.primaryDark else colors.primary
        else -> colors.paper
    }
    val fg = if (style == VastuButtonStyle.PRIMARY) colors.onPrimary else colors.textPrimary
    Box(
        modifier = modifier
            .heightIn(min = VastuTheme.sizes.cta)
            .clip(RoundedCornerShape(percent = 50))
            .background(bg)
            .then(
                if (style == VastuButtonStyle.SECONDARY)
                    Modifier.border(VastuTheme.borders.regular, colors.borderStrong, RoundedCornerShape(percent = 50))
                else Modifier
            )
            // pressEffect = false: owns a richer pressed state (fill swap + graphicsLayer above).
            .clickableTap(enabled = enabled, role = Role.Button, pressEffect = false, interactionSource = interaction, onClick = onClick)
            .padding(horizontal = VastuTheme.spacing.s6),
        contentAlignment = Alignment.Center,
    ) {
        VText(text = text, style = VastuTheme.type.label, color = fg)
    }
}

/**
 * An icon/glyph tap target sized to the a11y touch floor (48dp) with a spoken [contentDescription]
 * — so a screen-reader user hears "Back"/"Settings", not the raw glyph. Merges descendants so the
 * decorative glyph itself is not announced separately.
 */
@Composable
fun IconTapButton(
    glyph: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = VastuTheme.colors.textSecondary,
) {
    val cd = contentDescription
    Box(
        modifier = modifier
            .size(VastuTheme.sizes.minTouch)
            .clip(VastuTheme.shapes.full)
            .clickableTap(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { this.contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        VText(text = glyph, style = VastuTheme.type.h2, color = tint)
    }
}
