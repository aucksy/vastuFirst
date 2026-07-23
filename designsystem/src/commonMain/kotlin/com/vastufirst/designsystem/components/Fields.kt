package com.vastufirst.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * Single-line text field with default / focus / error states (design system §06). Owns its
 * border and colours from tokens; no Material TextField theming.
 */
@Composable
fun VastuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = VastuTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val borderColor = when {
        error != null -> colors.error
        focused -> colors.borderFocus
        else -> colors.borderDefault
    }
    val borderWidth = if (focused || error != null) VastuTheme.borders.focus else VastuTheme.borders.regular

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1)) {
        if (label != null) {
            VText(text = label, style = VastuTheme.type.bodySm, color = if (error != null) colors.error else colors.textSecondary)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(VastuTheme.sizes.control)
                .clip(VastuTheme.shapes.sm)
                .background(colors.surfaceRaised)
                .border(borderWidth, borderColor, VastuTheme.shapes.sm)
                .padding(horizontal = VastuTheme.spacing.s4),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                interactionSource = interaction,
                textStyle = VastuTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.borderFocus),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        VText(text = placeholder, style = VastuTheme.type.body, color = colors.textTertiary)
                    }
                    inner()
                },
            )
        }
        if (error != null) {
            VText(text = error, style = VastuTheme.type.bodySm, color = colors.error)
        }
    }
}

/** Degree entry stepper (Mark North): a mono value with ▲ / ▼ steppers, wrapping 0..359. */
@Composable
fun DegreeStepper(
    degrees: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VastuTheme.colors
    Row(
        modifier = modifier
            .height(VastuTheme.sizes.control)
            .clip(VastuTheme.shapes.sm)
            .background(colors.surfaceRaised)
            .border(VastuTheme.borders.regular, colors.borderDefault, VastuTheme.shapes.sm)
            .padding(start = VastuTheme.spacing.s4, end = VastuTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
    ) {
        VText(text = "$degrees°", style = VastuTheme.type.mono, color = colors.textPrimary)
        Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1)) {
            StepBtn("▲") { onChange(((degrees + 1) % 360 + 360) % 360) }
            StepBtn("▼") { onChange(((degrees - 1) % 360 + 360) % 360) }
        }
    }
}

@Composable
private fun StepBtn(glyph: String, onClick: () -> Unit) {
    val colors = VastuTheme.colors
    Box(
        modifier = Modifier
            .size(VastuTheme.sizes.iconLg)
            .clip(VastuTheme.shapes.sm)
            .background(colors.primary.copy(alpha = 0.14f))
            .clickableTap(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        VText(text = glyph, style = VastuTheme.type.bodySm, color = colors.primary)
    }
}
