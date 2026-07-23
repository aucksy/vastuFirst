package com.vastufirst.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    Row(
        modifier = modifier
            .clip(VastuTheme.shapes.full)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = VastuTheme.spacing.s2, vertical = VastuTheme.spacing.s1),
        horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) VText(text = glyph, style = VastuTheme.type.caption, color = color)
        VText(text = text, style = VastuTheme.type.caption, color = color)
    }
}

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
