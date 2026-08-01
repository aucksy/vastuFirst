package com.vastufirst.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * The workhorse surface card (design system §06). Optional [accent] paints a left stripe — used to
 * key defect / not-ideal / ideal cards to their verdict colour while the label and icon carry the
 * same meaning independently.
 *
 * ⭐⭐ THE STRIPE IS **DRAWN**, NOT LAID OUT, AND THAT IS THE WHOLE POINT.
 *
 * ⚠ This card used to be a `Row` pinned to `IntrinsicSize.Min` for one reason only: so a stripe that
 * was a real child `Box` could `fillMaxHeight()`. That single measurement constraint has now caused
 * two separate shipped defects, because everything inside the card had to be measured at an
 * intrinsic height first:
 *
 *   1. a wrapping row of chips inside a card measured **0 × 0** and vanished (the nine direction
 *      chips on the extras step);
 *   2. ⭐ long wrapping text inside a card was **ellipsised mid-sentence** — the reasons in the
 *      ₹699 report were cut off with "…" at 200 % font and on a 320 dp phone, which is the paid
 *      content being unreadable on exactly the phones this app is for.
 *
 * Drawing the stripe behind the card removes the constraint entirely: the card is now an ordinary
 * `Column` that wraps its content, so text wraps to whatever height it needs and a wrapping row
 * measures normally. Visually it is identical — same width, same colour, same full height, and it
 * still starts on the correct side in a right-to-left layout.
 */
@Composable
fun VastuCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    background: Color = VastuTheme.colors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val stripe = VastuTheme.spacing.s1
    val pad = VastuTheme.spacing.s4
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.md)
            .background(background)
            .then(
                if (accent == null) Modifier else Modifier.drawBehind {
                    val w = stripe.toPx()
                    // Start edge, not left edge — the stripe must swap sides in an RTL layout just
                    // as the child Box it replaced did.
                    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
                    drawRect(color = accent, topLeft = Offset(x, 0f), size = Size(w, size.height))
                },
            )
            .border(VastuTheme.borders.regular, VastuTheme.colors.borderDefault, VastuTheme.shapes.md)
            .padding(start = if (accent == null) pad else stripe + pad, top = pad, end = pad, bottom = pad),
        content = content,
    )
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
