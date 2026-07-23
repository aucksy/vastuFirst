package com.vastufirst.designsystem.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * Thin text wrapper over [BasicText] so screens never touch `fontSize`/`fontWeight` directly
 * (review gate) — they pass a ramp style from [VastuTheme.type] and an optional token colour.
 */
@Composable
fun VText(
    text: String,
    style: TextStyle,
    color: Color = VastuTheme.colors.textPrimary,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) = BasicText(
    text = text,
    modifier = modifier,
    style = if (align != null) style.copy(color = color, textAlign = align) else style.copy(color = color),
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
)

/** The DM-Mono uppercase eyebrow used above almost every section (design system, all screens). */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = VastuTheme.colors.textTertiary,
) = VText(
    text = text.uppercase(),
    style = VastuTheme.type.caption,
    color = color,
    modifier = modifier,
)
