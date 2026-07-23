package com.vastufirst.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * Selectable pill chip (language, room type, quick N/E/S/W). Selected = solid ink fill with
 * paper text; unselected = bordered with secondary text. Selection carries a text + fill
 * change, never colour alone (review gate).
 */
@Composable
fun VastuChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VastuTheme.colors
    Box(
        modifier = modifier
            .clip(VastuTheme.shapes.full)
            .background(if (selected) colors.textPrimary else colors.paper)
            .then(
                if (selected) Modifier
                else Modifier.border(VastuTheme.borders.regular, colors.borderDefault, VastuTheme.shapes.full)
            )
            .clickableTap(role = Role.Button, onClick = onClick)
            .padding(horizontal = VastuTheme.spacing.s4, vertical = VastuTheme.spacing.s3),
        contentAlignment = Alignment.Center,
    ) {
        VText(
            text = text,
            style = VastuTheme.type.bodySm,
            color = if (selected) colors.paper else colors.textSecondary,
        )
    }
}

/** Two-option segmented toggle (school profile, numerals). Active segment sits raised on the track. */
@Composable
fun VastuSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VastuTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.full)
            .background(colors.surface)
            .padding(VastuTheme.spacing.s1),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(VastuTheme.shapes.full)
                    .background(if (active) colors.surfaceRaised else colors.surface)
                    .clickableTap(role = Role.Tab, onClick = { onSelect(i) })
                    .padding(vertical = VastuTheme.spacing.s3),
                contentAlignment = Alignment.Center,
            ) {
                VText(
                    text = label,
                    style = VastuTheme.type.bodySm,
                    color = if (active) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}
