package com.vastufirst.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
            .heightIn(min = VastuTheme.sizes.minTouch)   // a11y: 48dp touch target (review gate)
            .clip(VastuTheme.shapes.full)
            .background(if (selected) colors.textPrimary else colors.paper)
            .then(
                if (selected) Modifier
                else Modifier.border(VastuTheme.borders.regular, colors.borderDefault, VastuTheme.shapes.full)
            )
            .clickableTap(role = Role.Button, onClick = onClick)
            .padding(horizontal = VastuTheme.spacing.s4, vertical = VastuTheme.spacing.s2),
        contentAlignment = Alignment.Center,
    ) {
        VText(
            text = text,
            style = VastuTheme.type.bodySm,
            color = if (selected) colors.paper else colors.textSecondary,
        )
    }
}

/**
 * Two-option segmented toggle (school profile, numerals). Active segment sits raised on the track.
 *
 * An index in [disabledIndices] renders dimmed and is **not tappable** — so a "coming soon" option
 * looks unavailable instead of a live-looking control that silently does nothing (UI-POLISH §3.F,
 * "no dead controls"). Give it a "· soon"-style label so the state is spoken, not only shown.
 */
@Composable
fun VastuSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    disabledIndices: Set<Int> = emptySet(),
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
            val disabled = i in disabledIndices
            Box(
                modifier = Modifier
                    .weight(1f)
                    // 48dp touch floor (review gate): the vertical padding alone left each tab 45dp,
                    // 3dp under the Material minimum — the render harness measured it.
                    .heightIn(min = VastuTheme.sizes.minTouch)
                    .clip(VastuTheme.shapes.full)
                    .background(if (active) colors.surfaceRaised else colors.surface)
                    .then(
                        if (disabled) Modifier
                        else Modifier.clickableTap(role = Role.Tab, onClick = { onSelect(i) })
                    )
                    // ⚠ Horizontal padding matters as much as vertical here. Without it a label
                    // measured to the segment's exact width, and at 200 % font a long one was drawn
                    // wider than its half of the row and CLIPPED AT BOTH ENDS by the centring — the
                    // first character simply gone. Padding gives the text a narrower box to wrap
                    // inside, so it breaks at the space instead of overflowing.
                    .padding(vertical = VastuTheme.spacing.s3, horizontal = VastuTheme.spacing.s2),
                contentAlignment = Alignment.Center,
            ) {
                VText(
                    text = label,
                    // Centred per LINE, not just as a block: a label that wraps to two lines used to
                    // sit left-ragged inside a centred box, which reads as a layout mistake.
                    align = TextAlign.Center,
                    style = VastuTheme.type.bodySm,
                    // A disabled segment reads the same muted tone as an inactive one (known to meet
                    // the contrast floor). Its "unavailable" meaning is carried by the "· soon" label
                    // and by being non-tappable — never by colour alone (review gate / a11y).
                    color = if (active) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}
