package com.vastufirst.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * The two verdict / provenance vocabularies, defined in the design system so the module stays
 * free of `shared` (keeps the iOS common source set clean). The app maps `shared.Verdict` and
 * `shared.Provenance` onto these one-to-one.
 */
enum class VastuVerdict { IDEAL, ACCEPTABLE, SUBOPTIMAL, DEFECT, NOT_ASSESSED }
enum class VastuProvenance { TEXT, DERIV, MOD, DISP }

/** Low-level tinted pill: a coloured word (+ optional leading glyph) on a soft tint of that colour. */
@Composable
fun TagPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    glyph: String? = null,
) {
    // See [pillShapeFor]: one line keeps its round ends exactly; wrapped words get rounded corners.
    var wrappedLine by remember(text) { mutableStateOf<Float?>(null) }
    Row(
        modifier = modifier
            .clip(pillShapeFor(wrappedLine, VastuTheme.spacing.s1))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = VastuTheme.spacing.s2, vertical = VastuTheme.spacing.s1),
        horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) VText(text = glyph, style = VastuTheme.type.caption, color = color)
        VText(
            text = text,
            style = VastuTheme.type.caption,
            color = color,
            onTextLayout = { wrappedLine = wrappedLineHeight(it) },
        )
    }
}

/** The first line's height when the words wrapped onto more than one line; null when they fit on one. */
fun wrappedLineHeight(layout: TextLayoutResult): Float? =
    if (layout.lineCount > 1) layout.getLineBottom(0) - layout.getLineTop(0) else null

/**
 * ⭐ THE SHAPE OF A PILL WHOSE WORDS MAY WRAP (29 Sep 2026).
 *
 * On one line a pill has fully round ends ([VastuShapes.full] — half its height), and that is returned
 * unchanged, so every one-line pill in the app draws exactly as it always has.
 *
 * ⚠ Once the words WRAP, half the height is a huge radius, and the curve cuts into the first letter of
 * the top line and the bottom one. Found by looking: at a 200 % font a room crossing into the centre
 * read "orth-West · crosses the / entre" — in its row and on the plan — while every gate stayed green,
 * because the box was the right size and only the ink was cut. So a wrapped pill gets rounded CORNERS
 * the size of a one-line pill's ends: [wrappedLinePx] is its first line's height, [verticalPadding] the
 * pill's own top padding.
 */
@Composable
fun pillShapeFor(wrappedLinePx: Float?, verticalPadding: Dp): Shape {
    if (wrappedLinePx == null) return VastuTheme.shapes.full
    val pad = with(LocalDensity.current) { verticalPadding.toPx() }
    return RoundedCornerShape(wrappedPillRadiusPx(wrappedLinePx, pad))
}

/** A wrapped pill's corner radius, in px: exactly the round end a ONE-line pill of that type has. */
fun wrappedPillRadiusPx(linePx: Float, padPx: Float): Float = linePx / 2f + padPx

@Composable
fun VastuVerdict.color(): Color = when (this) {
    VastuVerdict.IDEAL -> VastuTheme.colors.verdictIdeal
    VastuVerdict.ACCEPTABLE -> VastuTheme.colors.verdictAcceptable
    VastuVerdict.SUBOPTIMAL -> VastuTheme.colors.verdictSuboptimal
    VastuVerdict.DEFECT -> VastuTheme.colors.verdictDefect
    VastuVerdict.NOT_ASSESSED -> VastuTheme.colors.verdictNotAssessed
}

private fun VastuVerdict.label(): String = when (this) {
    VastuVerdict.IDEAL -> "Ideal"
    VastuVerdict.ACCEPTABLE -> "Fine"
    VastuVerdict.SUBOPTIMAL -> "Not ideal"
    VastuVerdict.DEFECT -> "Defect"
    VastuVerdict.NOT_ASSESSED -> "Not assessed"
}

private fun VastuVerdict.glyph(): String = when (this) {
    VastuVerdict.IDEAL, VastuVerdict.ACCEPTABLE -> "✓"        // ✓
    VastuVerdict.SUBOPTIMAL -> "△"                            // △
    VastuVerdict.DEFECT -> "✕"                                // ✕
    VastuVerdict.NOT_ASSESSED -> "–"                          // –
}

/** Verdict pill — colour + label + shape-glyph, so meaning survives without colour (review gate). */
@Composable
fun VerdictPill(verdict: VastuVerdict, modifier: Modifier = Modifier) =
    TagPill(text = verdict.label(), color = verdict.color(), glyph = verdict.glyph(), modifier = modifier)

@Composable
fun VastuProvenance.color(): Color = when (this) {
    VastuProvenance.TEXT -> VastuTheme.colors.provenanceText
    VastuProvenance.DERIV -> VastuTheme.colors.provenanceDeriv
    VastuProvenance.MOD -> VastuTheme.colors.provenanceMod
    VastuProvenance.DISP -> VastuTheme.colors.provenanceDisp
}

fun VastuProvenance.label(): String = when (this) {
    VastuProvenance.TEXT -> "From classical text"
    VastuProvenance.DERIV -> "Traditional practice"
    VastuProvenance.MOD -> "Modern practice"
    VastuProvenance.DISP -> "Schools disagree"
}

/**
 * Provenance tag — the product's core differentiator (§6.6). A coloured dot + a plain-language
 * label. Informative, never alarming: a MOD tag states age, it is not a warning.
 */
@Composable
fun ProvenanceTag(provenance: VastuProvenance, modifier: Modifier = Modifier) {
    val c = provenance.color()
    Row(
        modifier = modifier
            .clip(VastuTheme.shapes.full)
            .background(c.copy(alpha = 0.14f))
            .padding(horizontal = VastuTheme.spacing.s2, vertical = VastuTheme.spacing.s1),
        horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(VastuTheme.sizes.dot).clip(CircleShape).background(c)
        )
        VText(text = provenance.label(), style = VastuTheme.type.caption, color = c)
    }
}
