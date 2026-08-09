package com.vastufirst.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * ⭐ THE BALANCE METER — how much of this home already reads well, shown before what is wrong.
 *
 * One band across every room that was checked, split by verdict: already right, not ideal, needs
 * fixing. It exists because the report used to open on a front-door card and then list nothing but
 * problems for twenty screens — a reader with a perfectly decent home closed it feeling accused.
 * The counts are the same numbers the score is built from; this just lets somebody see the shape of
 * their home in one glance instead of reading to the end to find out.
 *
 * ⚠ **No animation, and that is deliberate.** An earlier draft grew the segments from zero on first
 * composition. Anything that delays that first frame — a slow phone, a screen restored behind a
 * dialog — leaves the most important device on the screen rendering as an empty track. A data
 * display is not a place to spend motion.
 *
 * ⚠ **Colour never carries this alone** (UI-POLISH §3G). Every segment has its count and its words
 * printed underneath, and the whole meter announces itself as one sentence to a screen reader.
 */
@Composable
fun BalanceMeter(
    right: Int,
    notIdeal: Int,
    needsFixing: Int,
    modifier: Modifier = Modifier,
) {
    val colors = VastuTheme.colors
    val total = right + notIdeal + needsFixing
    if (total <= 0) return

    val spoken = buildString {
        append("Of $total rooms checked: ")
        append("$right already right")
        if (notIdeal > 0) append(", $notIdeal not ideal")
        if (needsFixing > 0) append(", $needsFixing need fixing")
        append(".")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
    ) {
        SectionLabel("How your $total rooms read")

        Row(
            Modifier
                .fillMaxWidth()
                .height(VastuTheme.sizes.balanceTrack)
                .clip(VastuTheme.shapes.full)
                .background(colors.surface)
                .border(VastuTheme.borders.regular, colors.borderDefault, VastuTheme.shapes.full),
        ) {
            // weight() is the whole trick: three siblings sharing the row in proportion, so no
            // arithmetic on a width we do not know yet and nothing to re-measure on rotation.
            Seg(right, colors.verdictIdeal)
            Seg(notIdeal, colors.verdictSuboptimal)
            Seg(needsFixing, colors.verdictDefect)
        }

        // ⚠ ONE HEIGHT FOR ALL THREE, and it has to be asked for. Left to themselves the tiles are
        // each as tall as their own words: at 320 dp "already right" and "need fixing" wrap onto two
        // lines while "not ideal" stays on one, so the middle box ends a whole line short of its
        // neighbours and the row reads as a mistake. Seen in the 320 dp golden.
        //
        // IntrinsicSize.Min asks the row how tall it needs to be at the width it has been given —
        // the weights are honoured during that question, so each tile is measured at its real third
        // of the row and the tallest answer sets all three. fillMaxHeight is what makes the shorter
        // tiles take it up; without it they keep their own height and the border still ends early.
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        ) {
            Legend(Modifier.weight(1f).fillMaxHeight(), right, "already right", colors.verdictIdeal)
            Legend(Modifier.weight(1f).fillMaxHeight(), notIdeal, "not ideal", colors.verdictSuboptimal)
            Legend(Modifier.weight(1f).fillMaxHeight(), needsFixing, "need fixing", colors.verdictDefect)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Seg(count: Int, color: Color) {
    if (count <= 0) return
    Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(color))
}

/**
 * One count and its words. The number is Marcellus so it reads as a figure rather than a label, and
 * the words sit under it — never beside it, which shatters at 200 % font on a 320 dp phone.
 *
 * ⚠ THE NUMBER IS INK, NOT THE VERDICT COLOUR, and that is an accessibility fix rather than a taste
 * one. Drawn in its verdict colour it failed contrast: the amber (#D68C18) and the gold-family
 * accents sit around 2.5:1, well under the 3:1 floor for large text, so the count — the single most
 * important figure in this component — was the least legible thing in it. The colour moves to the
 * dot, which is decoration and carries no meaning on its own; the bar segment and the label under
 * the number carry the meaning (UI-POLISH §2 rule 3: never colour alone).
 */
@Composable
private fun Legend(modifier: Modifier, count: Int, label: String, color: Color) {
    val colors = VastuTheme.colors
    Column(
        modifier
            .clip(VastuTheme.shapes.md)
            .background(colors.surfaceRaised)
            .border(VastuTheme.borders.regular, colors.borderDefault, VastuTheme.shapes.md)
            .padding(VastuTheme.spacing.s3),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
    ) {
        Box(
            Modifier
                .size(VastuTheme.sizes.dot)
                .clip(VastuTheme.shapes.full)
                .background(color),
        )
        VText(count.toString(), style = VastuTheme.type.h2, color = colors.textPrimary)
        VText(label, style = VastuTheme.type.bodySm, color = colors.textTertiary)
    }
}
