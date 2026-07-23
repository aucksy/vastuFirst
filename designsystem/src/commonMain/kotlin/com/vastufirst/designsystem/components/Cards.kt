package com.vastufirst.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * The workhorse surface card (design system §06). Optional [accent] paints a 3-unit left
 * stripe — used to key defect / not-ideal / ideal cards to their verdict colour while the
 * label and icon carry the same meaning independently.
 */
@Composable
fun VastuCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    background: Color = VastuTheme.colors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)   // bound the row so the accent stripe can fill height in a scroll
            .clip(VastuTheme.shapes.md)
            .background(background)
            .border(VastuTheme.borders.regular, VastuTheme.colors.borderDefault, VastuTheme.shapes.md),
    ) {
        if (accent != null) {
            Box(
                modifier = Modifier
                    .width(VastuTheme.spacing.s1)
                    .fillMaxHeight()
                    .background(accent),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(VastuTheme.spacing.s4),
            content = content,
        )
    }
}

/** Saved-plans / detail list row: leading icon tile, title + subtitle, trailing slot. */
@Composable
fun VastuListRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.md)
            .background(VastuTheme.colors.surfaceRaised)
            .border(VastuTheme.borders.regular, VastuTheme.colors.borderDefault, VastuTheme.shapes.md)
            .padding(VastuTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Box(Modifier.width(VastuTheme.spacing.s3))
        }
        Column(Modifier.weight(1f)) {
            VText(text = title, style = VastuTheme.type.h3, color = VastuTheme.colors.textPrimary, maxLines = 1)
            VText(text = subtitle, style = VastuTheme.type.bodySm, color = VastuTheme.colors.textSecondary, maxLines = 1)
        }
        trailing?.invoke(this)
    }
}
